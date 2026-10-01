package com.evops.ratecard;

import com.evops.common.BusinessException;
import com.evops.ledger.dto.SiteCreateRequest;
import com.evops.ledger.entity.Site;
import com.evops.ledger.service.SchemaVersionService;
import com.evops.ledger.service.SiteService;
import com.evops.ratecard.dto.CardCreateRequest;
import com.evops.ratecard.dto.CardView;
import com.evops.ratecard.dto.DraftVersionRequest;
import com.evops.ratecard.dto.FinanceTierView;
import com.evops.ratecard.dto.PriceLineSaveRequest;
import com.evops.ratecard.dto.PublishRequest;
import com.evops.ratecard.dto.QuoteSubmitRequest;
import com.evops.ratecard.dto.QuoteView;
import com.evops.ratecard.dto.TierSaveRequest;
import com.evops.ratecard.dto.VersionDetailView;
import com.evops.ratecard.dto.VersionView;
import com.evops.ratecard.entity.RateCard;
import com.evops.ratecard.service.FinanceTierService;
import com.evops.ratecard.service.PricingEngine;
import com.evops.ratecard.service.QuoteService;
import com.evops.ratecard.service.RateCardService;
import com.evops.ratecard.support.QuotePriceResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F4 年度刊例与统一计价口径端到端测试（真实 H2 + 真实事务）。
 * 共享内存库 + 年度唯一：计价类用例一律使用独占的过去年度（从 2001 起递增）和固定报价日 6 月 1 日，
 * 与用例执行顺序无关，互不干扰。
 */
@SpringBootTest
class RateCardFlowTest {

    @Autowired private SiteService siteService;
    @Autowired private RateCardService rateCardService;
    @Autowired private PricingEngine pricingEngine;
    @Autowired private QuoteService quoteService;
    @Autowired private FinanceTierService financeTierService;
    @Autowired private SchemaVersionService schemaVersionService;

    /** 独占的过去年度（各用例不同；static：JUnit 每个方法新建实例，计数必须跨实例累加） */
    private static final AtomicInteger yearSeq = new AtomicInteger(2001);

    private int newYear() {
        return yearSeq.getAndIncrement();
    }

    private LocalDate quoteDay(int year) {
        return LocalDate.of(year, 6, 1);
    }

    private static int siteSeq = 0;
    private synchronized String uniqueSiteNo() {
        siteSeq++;
        return "RC-SITE-" + System.nanoTime() + "-" + siteSeq;
    }

    private Site registerSite() {
        SiteCreateRequest req = new SiteCreateRequest();
        req.setSiteNo(uniqueSiteNo());
        req.setSizeSpec("18m×6m");
        req.setOrientation("朝北");
        req.setBusinessDistrict("王府井");
        req.setOperatorName("tester");
        return siteService.register(req);
    }

    private RateCard createCard(int year, String taxRate) {
        CardCreateRequest req = new CardCreateRequest();
        req.setCardYear(year);
        req.setOperatorName("tester");
        if (taxRate != null) {
            req.setTaxRate(new BigDecimal(taxRate));
        }
        return rateCardService.createCard(req);
    }

    private Long draftVersionId(Long cardId) {
        return rateCardService.getCard(cardId).getVersions().stream()
                .filter(v -> v.getVersionNo() == 1).findFirst().orElseThrow(IllegalStateException::new)
                .getId();
    }

    private Long draftVersionId(RateCard card) {
        return draftVersionId(card.getId());
    }

    private PriceLineSaveRequest linesReq(Object... siteIdAndPrice) {
        List<PriceLineSaveRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < siteIdAndPrice.length; i += 2) {
            PriceLineSaveRequest.Item item = new PriceLineSaveRequest.Item();
            item.setSiteId((Long) siteIdAndPrice[i]);
            item.setListNetPrice(new BigDecimal((String) siteIdAndPrice[i + 1]));
            items.add(item);
        }
        PriceLineSaveRequest req = new PriceLineSaveRequest();
        req.setLines(items);
        req.setOperatorName("tester");
        return req;
    }

    /** 标准三档：1-3 周 100%、4-7 周 95%、8 周起 90% */
    private TierSaveRequest standardTiersReq() {
        return tiersReq(tier(1, 3, "1.0000"), tier(4, 7, "0.9500"), tier(8, null, "0.9000"));
    }

    private TierSaveRequest tiersReq(TierSaveRequest.Item... items) {
        TierSaveRequest req = new TierSaveRequest();
        req.setTiers(new ArrayList<>(Arrays.asList(items)));
        req.setOperatorName("tester");
        return req;
    }

    private TierSaveRequest.Item tier(int min, Integer max, String rate) {
        TierSaveRequest.Item item = new TierSaveRequest.Item();
        item.setMinWeeks(min);
        item.setMaxWeeks(max);
        item.setDiscountRate(new BigDecimal(rate));
        return item;
    }

    private void publish(Long versionId, LocalDate effectiveDate) {
        rateCardService.publish(versionId, "tester", effectiveDate);
    }

    /** 建刊例 → 挂单价 → 标准三档 → 发布（生效日默认该年元旦）。返回 cardId。 */
    private long publishStandardCard(int year, Site site, String listNetPrice, String taxRate) {
        RateCard card = createCard(year, taxRate);
        Long v1 = draftVersionId(card.getId());
        rateCardService.saveLines(v1, linesReq(site.getId(), listNetPrice));
        rateCardService.saveTiers(v1, standardTiersReq());
        publish(v1, LocalDate.of(year, 1, 1));
        return card.getId();
    }

    private QuoteSubmitRequest quoteReq(Long siteId, int weeks, LocalDate date) {
        QuoteSubmitRequest.Line line = new QuoteSubmitRequest.Line();
        line.setSiteId(siteId);
        line.setWeeks(weeks);
        QuoteSubmitRequest req = new QuoteSubmitRequest();
        req.setQuoteDate(date);
        req.setCustomerName("测试客户");
        req.setLines(new ArrayList<>(Arrays.asList(line)));
        req.setOperatorName("tester");
        return req;
    }

    // ── 刊例与版本 ──

    @Test
    void 年度刊例按年唯一_重复建档报中文错误() {
        int year = newYear();
        createCard(year, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> createCard(year, null));
        assertTrue(ex.getMessage().contains("一年只能建一张刊例"));
    }

    @Test
    void 建档自动生成v1草稿_税率缺省为6个点() {
        int year = newYear();
        RateCard card = createCard(year, null);
        assertEquals("RC" + year, card.getCardNo());
        assertEquals(RateCardEnums.CARD_DRAFT, card.getStatus());
        assertNull(card.getCurrentVersionId());

        CardView view = rateCardService.getCard(card.getId());
        assertEquals(1, view.getVersions().size());
        VersionView v1 = view.getVersions().get(0);
        assertEquals(1, v1.getVersionNo());
        assertEquals(RateCardEnums.VERSION_DRAFT, v1.getStatus());
        assertEquals(RateCardEnums.VERSION_SOURCE_INITIAL, v1.getSource());
        assertEquals(0, new BigDecimal("0.0600").compareTo(v1.getTaxRate()));
    }

    @Test
    void 草稿保存单价_点位不存在与批次内重复点位拒收() {
        int year = newYear();
        RateCard card = createCard(year, null);
        Long v1 = draftVersionId(card.getId());
        Site site = registerSite();

        BusinessException dup = assertThrows(BusinessException.class,
                () -> rateCardService.saveLines(v1, linesReq(site.getId(), "100.00", site.getId(), "200.00")));
        assertTrue(dup.getMessage().contains("单价行重复"));

        BusinessException missing = assertThrows(BusinessException.class,
                () -> rateCardService.saveLines(v1, linesReq(99999999L, "100.00")));
        assertTrue(missing.getMessage().contains("点位不存在"));

        // 任一校验失败都不留行
        assertEquals(0L, rateCardService.getVersionDetail(v1).getPriceLines().size());
    }

    @Test
    void 单价超过两位小数拒收不静默截断() {
        int year = newYear();
        RateCard card = createCard(year, null);
        Long v1 = draftVersionId(card.getId());
        Site site = registerSite();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rateCardService.saveLines(v1, linesReq(site.getId(), "100.123")));
        assertTrue(ex.getMessage().contains("最多支持 2 位小数"));
        assertEquals(0L, rateCardService.getVersionDetail(v1).getPriceLines().size());
    }

    @Test
    void 阶梯区间重叠保存拒收() {
        int year = newYear();
        Long v1 = draftVersionId(createCard(year, null));

        BusinessException touch = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                tiersReq(tier(1, 3, "1"), tier(3, 5, "0.95"), tier(6, null, "0.9"))));
        assertTrue(touch.getMessage().contains("重叠"));

        BusinessException cross = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                tiersReq(tier(1, 4, "1"), tier(3, 7, "0.95"), tier(8, null, "0.9"))));
        assertTrue(cross.getMessage().contains("重叠"));
        assertEquals(0L, rateCardService.getVersionDetail(v1).getTiers().size());
    }

    @Test
    void 阶梯断档与不从1周起步拒收() {
        int year = newYear();
        Long v1 = draftVersionId(createCard(year, null));

        BusinessException gap = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                tiersReq(tier(1, 2, "1"), tier(4, null, "0.9"))));
        assertTrue(gap.getMessage().contains("断档"));

        BusinessException notFromOne = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                tiersReq(tier(2, 5, "1"), tier(6, null, "0.9"))));
        assertTrue(notFromOne.getMessage().contains("从 1 周开始"));
    }

    @Test
    void 阶梯折扣率必须随周数单调不增_买久更贵被拒() {
        int year = newYear();
        Long v1 = draftVersionId(createCard(year, null));

        BusinessException ex = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                tiersReq(tier(1, 3, "1"), tier(4, 7, "0.95"), tier(8, null, "1"))));
        assertTrue(ex.getMessage().contains("买得越久每周不能越贵"));

        // 0.95/0.90 递减可以存
        rateCardService.saveTiers(v1, standardTiersReq());
        assertEquals(3, rateCardService.getVersionDetail(v1).getTiers().size());
    }

    @Test
    void 折扣率与税率越界拒收() {
        int year = newYear();
        Long v1 = draftVersionId(createCard(year, null));

        for (String bad : new String[]{"1.0100", "0.0000", "-0.1000"}) {
            BusinessException ex = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                    tiersReq(tier(1, 3, "1"), tier(4, null, bad))));
            assertTrue(ex.getMessage().contains("折扣率"), "坏值 " + bad + " 应被拒");
        }
        BusinessException scaleEx = assertThrows(BusinessException.class, () -> rateCardService.saveTiers(v1,
                tiersReq(tier(1, 3, "1"), tier(4, null, "0.12345"))));
        assertTrue(scaleEx.getMessage().contains("最多支持 4 位小数"));

        BusinessException taxEx = assertThrows(BusinessException.class, () -> createCard(newYear(), "1.2"));
        assertTrue(taxEx.getMessage().contains("税率"));
    }

    @Test
    void 发布冻结行与阶梯_生效版本改单价改阶梯均被拒() {
        int year = newYear();
        Site site = registerSite();
        long cardId = publishStandardCard(year, site, "100.00", null);
        Long v1 = draftVersionId(cardId);

        BusinessException lineEx = assertThrows(BusinessException.class,
                () -> rateCardService.saveLines(v1, linesReq(site.getId(), "101.00")));
        assertTrue(lineEx.getMessage().contains("已冻结"));
        BusinessException tierEx = assertThrows(BusinessException.class,
                () -> rateCardService.saveTiers(v1, standardTiersReq()));
        assertTrue(tierEx.getMessage().contains("已冻结"));
        assertEquals(RateCardEnums.CARD_EFFECTIVE, rateCardService.listCards(year).get(0).getStatus());
    }

    @Test
    void 再发布修订版_旧版置SUPERSEDED_刊例头指向新版() {
        int year = newYear();
        Site site = registerSite();
        long cardId = publishStandardCard(year, site, "100.00", null);

        DraftVersionRequest draftReq = new DraftVersionRequest();
        draftReq.setOperatorName("tester");
        Long v2 = rateCardService.createDraftVersion(cardId, draftReq).getId();
        // 修订草稿深拷贝了单价行与阶梯
        assertEquals(1, rateCardService.getVersionDetail(v2).getPriceLines().size());
        assertEquals(3, rateCardService.getVersionDetail(v2).getTiers().size());

        rateCardService.saveLines(v2, linesReq(site.getId(), "120.00"));
        publish(v2, LocalDate.of(year, 7, 1));

        CardView view = rateCardService.getCard(cardId);
        assertEquals(2, view.getCurrentVersionNo());
        assertEquals(RateCardEnums.VERSION_EFFECTIVE, view.getVersions().stream()
                .filter(v -> v.getVersionNo() == 2).findFirst().orElseThrow(IllegalStateException::new).getStatus());
        assertEquals(RateCardEnums.VERSION_SUPERSEDED, view.getVersions().stream()
                .filter(v -> v.getVersionNo() == 1).findFirst().orElseThrow(IllegalStateException::new).getStatus());

        // v2 已生效后可以再开 v3 草稿，来源 REVISE 且深拷贝的是 v2 的新价
        DraftVersionRequest another = new DraftVersionRequest();
        another.setOperatorName("tester");
        Long v3 = rateCardService.createDraftVersion(cardId, another).getId();
        VersionDetailView v3Detail = rateCardService.getVersionDetail(v3);
        assertEquals(RateCardEnums.VERSION_SOURCE_REVISE, v3Detail.getSource());
        assertEquals(0, new BigDecimal("120.00").compareTo(
                v3Detail.getPriceLines().get(0).getListNetPrice()));
    }

    // ── 计价引擎与报价 ──

    @Test
    void 定价引擎阶梯边界闭区间命中_HALF_UP与含税不含税并列() {
        int year = newYear();
        Site site = registerSite();
        publishStandardCard(year, site, "100.00", "0.06");
        LocalDate day = quoteDay(year);

        // 3 周命中第一档上界（闭区间），不打折
        QuotePriceResult three = pricingEngine.price(site.getId(), 3, day);
        assertEquals(0, new BigDecimal("100.00").compareTo(three.getNetUnit()));
        assertEquals(0, new BigDecimal("300.00").compareTo(three.getNetAmount()));
        assertEquals(0, new BigDecimal("106.00").compareTo(three.getGrossUnit()));
        assertEquals(1, three.getTierNo());

        // 4 周命中第二档 95 折
        QuotePriceResult four = pricingEngine.price(site.getId(), 4, day);
        assertEquals(0, new BigDecimal("95.00").compareTo(four.getNetUnit()));
        assertEquals(0, new BigDecimal("380.00").compareTo(four.getNetAmount()));
        assertEquals(0, new BigDecimal("100.70").compareTo(four.getGrossUnit()));
        assertEquals(0, new BigDecimal("402.80").compareTo(four.getGrossAmount()));
        assertEquals(0, new BigDecimal("22.80").compareTo(four.getTaxAmount()));
        assertEquals(2, four.getTierNo());

        // 8 周与更大周数都命中无上限末档
        assertEquals(3, pricingEngine.price(site.getId(), 8, day).getTierNo());
        assertEquals(3, pricingEngine.price(site.getId(), 99, day).getTierNo());

        // HALF_UP：99.99×0.95=94.9905→94.99；94.99×1.06=100.6894→100.69
        int year2 = newYear();
        Site site2 = registerSite();
        publishStandardCard(year2, site2, "99.99", "0.06");
        QuotePriceResult rounded = pricingEngine.price(site2.getId(), 4, quoteDay(year2));
        assertEquals(0, new BigDecimal("94.99").compareTo(rounded.getNetUnit()));
        assertEquals(0, new BigDecimal("100.69").compareTo(rounded.getGrossUnit()));
    }

    @Test
    void 报价按提交日命中版本_多行合计与逐行快照落库() {
        int year = newYear();
        Site a = registerSite();
        Site b = registerSite();
        RateCard card = createCard(year, "0.06");
        Long v1 = draftVersionId(card.getId());
        rateCardService.saveLines(v1, linesReq(a.getId(), "100.00", b.getId(), "200.00"));
        rateCardService.saveTiers(v1, standardTiersReq());
        publish(v1, LocalDate.of(year, 1, 1));

        QuoteSubmitRequest req = new QuoteSubmitRequest();
        req.setQuoteDate(quoteDay(year));
        req.setCustomerName("多单行客户");
        QuoteSubmitRequest.Line l1 = new QuoteSubmitRequest.Line();
        l1.setSiteId(a.getId());
        l1.setWeeks(3);
        QuoteSubmitRequest.Line l2 = new QuoteSubmitRequest.Line();
        l2.setSiteId(b.getId());
        l2.setWeeks(4);
        req.setLines(new ArrayList<>(Arrays.asList(l1, l2)));
        req.setOperatorName("tester");

        QuoteView view = quoteService.submit(req);
        assertTrue(view.getQuoteNo().startsWith("QT"));
        assertEquals(2, view.getLines().size());
        assertEquals(7, view.getTotalWeeks());
        // 头合计 = 行合计：300.00 + 760.00 = 1060.00
        assertEquals(0, new BigDecimal("1060.00").compareTo(view.getNetAmount()));
        assertEquals(0, view.getLines().get(0).getNetAmount()
                .add(view.getLines().get(1).getNetAmount()).compareTo(view.getNetAmount()));
        assertEquals("RC" + year, view.getCardNo());

        // 详情拿到的是库里的快照
        QuoteView reloaded = quoteService.detail(view.getId());
        assertEquals(2, reloaded.getLines().size());
        assertEquals(a.getSiteNo(), reloaded.getLines().get(0).getSiteNo());
    }

    @Test
    void 刊例调价发布新版后_旧报价快照不变_新报价用新价() {
        int year = newYear();
        Site site = registerSite();
        long cardId = publishStandardCard(year, site, "100.00", "0.06");
        LocalDate afterRevise = LocalDate.of(year, 8, 1);

        // 修订版 7 月 1 日生效，6 月报价按旧价
        QuoteView oldQuote = quoteService.submit(quoteReq(site.getId(), 5, quoteDay(year)));
        assertEquals(0, new BigDecimal("95.00").compareTo(oldQuote.getLines().get(0).getNetUnit()));
        Integer oldVersion = oldQuote.getVersionNo();

        DraftVersionRequest draftReq = new DraftVersionRequest();
        draftReq.setOperatorName("tester");
        Long v2 = rateCardService.createDraftVersion(cardId, draftReq).getId();
        rateCardService.saveLines(v2, linesReq(site.getId(), "120.00"));
        publish(v2, LocalDate.of(year, 7, 1));

        // 8 月报价按新价
        QuoteView newQuote = quoteService.submit(quoteReq(site.getId(), 5, afterRevise));
        assertEquals(0, new BigDecimal("114.00").compareTo(newQuote.getLines().get(0).getNetUnit()));
        assertEquals(oldVersion + 1, newQuote.getVersionNo());

        // 旧单一个字节都没回写
        QuoteView oldReloaded = quoteService.detail(oldQuote.getId());
        assertEquals(oldVersion, oldReloaded.getVersionNo());
        assertEquals(0, new BigDecimal("100.00").compareTo(oldReloaded.getLines().get(0).getListNetUnit()));
        assertEquals(0, new BigDecimal("95.00").compareTo(oldReloaded.getLines().get(0).getNetUnit()));
        assertEquals(0, new BigDecimal("0.0600").compareTo(oldReloaded.getTaxRate()));
    }

    @Test
    void 未建刊例或仅有草稿或点位未挂价时报清晰中文错误() {
        int noCardYear = newYear();
        Site site = registerSite();
        BusinessException noCard = assertThrows(BusinessException.class,
                () -> pricingEngine.price(site.getId(), 1, quoteDay(noCardYear)));
        assertTrue(noCard.getMessage().contains(noCardYear + " 年度刊例尚未建立"));

        int draftYear = newYear();
        Long v1 = draftVersionId(createCard(draftYear, null));
        rateCardService.saveLines(v1, linesReq(site.getId(), "100.00"));
        rateCardService.saveTiers(v1, standardTiersReq());
        BusinessException draftOnly = assertThrows(BusinessException.class,
                () -> pricingEngine.price(site.getId(), 1, quoteDay(draftYear)));
        assertTrue(draftOnly.getMessage().contains("尚无生效版本"));

        // 点位未挂单价：刊例里只有另一个点位
        int pricedYear = newYear();
        Site pricedSite = registerSite();
        Site noPriceSite = registerSite();
        publishStandardCard(pricedYear, pricedSite, "100.00", null);
        BusinessException noPrice = assertThrows(BusinessException.class,
                () -> quoteService.submit(quoteReq(noPriceSite.getId(), 1, quoteDay(pricedYear))));
        assertTrue(noPrice.getMessage().contains("未配置挂牌单价"));
    }

    @Test
    void 未来报价日期与同单重复点位被拒() {
        int year = newYear();
        Site site = registerSite();
        publishStandardCard(year, site, "100.00", null);

        // 未来日期在计价前直接拒绝
        BusinessException future = assertThrows(BusinessException.class,
                () -> quoteService.submit(quoteReq(site.getId(), 1, LocalDate.now().plusDays(1))));
        assertTrue(future.getMessage().contains("不能晚于今天"));

        // 同单同点位拆两行：在固定报价日上，第一行能计价，第二行被拒
        QuoteSubmitRequest req = new QuoteSubmitRequest();
        req.setQuoteDate(quoteDay(year));
        QuoteSubmitRequest.Line l1 = new QuoteSubmitRequest.Line();
        l1.setSiteId(site.getId());
        l1.setWeeks(1);
        QuoteSubmitRequest.Line l2 = new QuoteSubmitRequest.Line();
        l2.setSiteId(site.getId());
        l2.setWeeks(2);
        req.setLines(new ArrayList<>(Arrays.asList(l1, l2)));
        req.setOperatorName("tester");
        BusinessException dup = assertThrows(BusinessException.class, () -> quoteService.submit(req));
        assertTrue(dup.getMessage().contains("出现多行"));
    }

    @Test
    void 财务阶梯对照表随查随拉_选点位带示例单价_CSV含BOM() {
        int year = newYear();
        Site site = registerSite();
        publishStandardCard(year, site, "100.00", "0.06");

        FinanceTierView table = financeTierService.tierTable(quoteDay(year), site.getId());
        assertEquals("RC" + year, table.getCardNo());
        assertEquals(site.getSiteNo(), table.getSiteNo());
        assertEquals(3, table.getTiers().size());
        FinanceTierView.Row second = table.getTiers().get(1);
        assertEquals(0, new BigDecimal("0.9500").compareTo(second.getDiscountRate()));
        assertEquals(0, new BigDecimal("95.00").compareTo(second.getNetUnit()));
        assertEquals(0, new BigDecimal("100.70").compareTo(second.getGrossUnit()));

        // 不选点位也能拉，示例单价列留空
        FinanceTierView plain = financeTierService.tierTable(quoteDay(year), null);
        assertNull(plain.getTiers().get(0).getNetUnit());

        String csv = financeTierService.exportCsv(quoteDay(year), site.getId());
        assertEquals('﻿', csv.charAt(0));
        assertTrue(csv.contains("折后周单价(含税)"));
        assertTrue(csv.contains("第2档"));
        assertTrue(csv.contains("100.70"));
    }

    @Test
    void 修订税率只影响新报价_旧单快照不变() {
        int year = newYear();
        Site site = registerSite();
        long cardId = publishStandardCard(year, site, "100.00", "0.06");

        QuoteView oldQuote = quoteService.submit(quoteReq(site.getId(), 1, quoteDay(year)));
        assertEquals(0, new BigDecimal("106.00").compareTo(oldQuote.getLines().get(0).getGrossUnit()));

        DraftVersionRequest draftReq = new DraftVersionRequest();
        draftReq.setTaxRate(new BigDecimal("0.1300"));
        draftReq.setOperatorName("tester");
        Long v2 = rateCardService.createDraftVersion(cardId, draftReq).getId();
        publish(v2, LocalDate.of(year, 7, 1));

        QuoteView newQuote = quoteService.submit(quoteReq(site.getId(), 1, LocalDate.of(year, 8, 1)));
        assertEquals(0, new BigDecimal("113.00").compareTo(newQuote.getLines().get(0).getGrossUnit()));
        assertEquals(0, new BigDecimal("0.0600").compareTo(
                quoteService.detail(oldQuote.getId()).getTaxRate()));
    }

    @Test
    void 待生效版本在报价日之前不参与报价() {
        // 独占的未来年度：刊例 12 月才生效，6 月（未来报价日，直接走引擎）不可用
        int year = 2091;
        Site site = registerSite();
        RateCard card = createCard(year, "0.06");
        Long v1 = draftVersionId(card.getId());
        rateCardService.saveLines(v1, linesReq(site.getId(), "100.00"));
        rateCardService.saveTiers(v1, standardTiersReq());
        publish(v1, LocalDate.of(year, 12, 1));
        assertEquals(RateCardEnums.VERSION_PENDING,
                rateCardService.getCard(card.getId()).getVersions().get(0).getStatus());

        BusinessException notYet = assertThrows(BusinessException.class,
                () -> pricingEngine.price(site.getId(), 1, LocalDate.of(year, 6, 1)));
        assertTrue(notYet.getMessage().contains("尚无生效版本"));

        // 生效日后报价命中
        assertEquals(0, new BigDecimal("100.00").compareTo(
                pricingEngine.price(site.getId(), 1, LocalDate.of(year, 12, 1)).getNetUnit()));
    }

    @Test
    void 底账版本登记包含f4起步版本() {
        List<String> versions = schemaVersionService.list().stream()
                .map(v -> v.getVersionNo()).collect(Collectors.toList());
        assertTrue(versions.contains("f4-rate-card-1"));
    }
}
