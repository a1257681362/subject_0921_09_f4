package com.evops.ratecard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evops.common.BusinessException;
import com.evops.ledger.entity.Site;
import com.evops.ledger.mapper.SiteMapper;
import com.evops.ledger.support.Operators;
import com.evops.ratecard.RateCardEnums;
import com.evops.ratecard.dto.CardCreateRequest;
import com.evops.ratecard.dto.CardView;
import com.evops.ratecard.dto.DraftVersionRequest;
import com.evops.ratecard.dto.PriceLineSaveRequest;
import com.evops.ratecard.dto.PriceLineView;
import com.evops.ratecard.dto.TierSaveRequest;
import com.evops.ratecard.dto.TierView;
import com.evops.ratecard.dto.VersionDetailView;
import com.evops.ratecard.dto.VersionView;
import com.evops.ratecard.entity.RateCard;
import com.evops.ratecard.entity.RateCardPrice;
import com.evops.ratecard.entity.RateCardTier;
import com.evops.ratecard.entity.RateCardVersion;
import com.evops.ratecard.mapper.RateCardMapper;
import com.evops.ratecard.mapper.RateCardPriceMapper;
import com.evops.ratecard.mapper.RateCardTierMapper;
import com.evops.ratecard.mapper.RateCardVersionMapper;
import com.evops.ratecard.support.EffectiveVersion;
import com.evops.ratecard.support.RateCardMath;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 年度刊例与版本维护。
 * 口径：公司级一年一张刊例（card_year 唯一）；版本 DRAFT→EFFECTIVE→SUPERSEDED；
 * 草稿可整表改点位单价与周数阶梯，发布即冻结，旧生效版本置 SUPERSEDED 不抹历史；
 * 报价按报价日反查「当时已发布、生效时刻最近」的版本。
 */
@Service
public class RateCardService {

    private final RateCardMapper rateCardMapper;
    private final RateCardVersionMapper versionMapper;
    private final RateCardPriceMapper priceMapper;
    private final RateCardTierMapper tierMapper;
    private final SiteMapper siteMapper;

    public RateCardService(RateCardMapper rateCardMapper,
                           RateCardVersionMapper versionMapper,
                           RateCardPriceMapper priceMapper,
                           RateCardTierMapper tierMapper,
                           SiteMapper siteMapper) {
        this.rateCardMapper = rateCardMapper;
        this.versionMapper = versionMapper;
        this.priceMapper = priceMapper;
        this.tierMapper = tierMapper;
        this.siteMapper = siteMapper;
    }

    /** 建年度刊例：一年只能建一张；建档同时自动生成 v1 草稿（INITIAL）。 */
    @Transactional(rollbackFor = Exception.class)
    public RateCard createCard(CardCreateRequest request) {
        int year = request.getCardYear();
        RateCard existing = rateCardMapper.selectOne(
                new LambdaQueryWrapper<RateCard>().eq(RateCard::getCardYear, year));
        if (existing != null) {
            throw new BusinessException(year + " 年度刊例已存在（" + existing.getCardNo()
                    + "），一年只能建一张刊例");
        }
        BigDecimal taxRate = request.getTaxRate() == null
                ? RateCardEnums.DEFAULT_TAX_RATE
                : RateCardMath.requireTaxRate(request.getTaxRate());
        String operator = Operators.resolve(request.getOperatorName());

        RateCard card = new RateCard();
        card.setCardNo("RC" + year);
        card.setCardYear(year);
        card.setCardName(request.getCardName() == null || request.getCardName().trim().isEmpty()
                ? year + " 年度刊例" : request.getCardName().trim());
        card.setStatus(RateCardEnums.CARD_DRAFT);
        card.setOperatorName(operator);
        card.setRemark(request.getRemark());
        try {
            rateCardMapper.insert(card);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(year + " 年度刊例已存在，一年只能建一张刊例");
        }

        RateCardVersion v1 = new RateCardVersion();
        v1.setCardId(card.getId());
        v1.setVersionNo(1);
        v1.setSource(RateCardEnums.VERSION_SOURCE_INITIAL);
        v1.setStatus(RateCardEnums.VERSION_DRAFT);
        v1.setTaxRate(taxRate);
        v1.setOperatorName(operator);
        v1.setRemark(request.getRemark());
        versionMapper.insert(v1);
        return card;
    }

    /**
     * 基于当前生效版本开修订草稿（REVISE）：深拷贝生效版本的单价行、阶梯与税率。
     * 前提：已发布过生效版本，且当前没有其他草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public RateCardVersion createDraftVersion(Long cardId, DraftVersionRequest request) {
        RateCard card = requireCard(cardId);
        if (card.getCurrentVersionId() == null) {
            throw new BusinessException("刊例 " + card.getCardNo() + " 的首版草稿尚未发布，不能再开修订草稿；请直接编辑并发布 v1");
        }
        Long blocking = versionMapper.selectCount(new LambdaQueryWrapper<RateCardVersion>()
                .eq(RateCardVersion::getCardId, cardId)
                .in(RateCardVersion::getStatus,
                        RateCardEnums.VERSION_DRAFT, RateCardEnums.VERSION_PENDING));
        if (blocking > 0) {
            throw new BusinessException("刊例 " + card.getCardNo() + " 已有草稿或待生效版本，请等其生效后再开修订版");
        }
        RateCardVersion effective = versionMapper.selectById(card.getCurrentVersionId());
        if (effective == null) {
            throw new BusinessException("刊例 " + card.getCardNo() + " 当前生效版本数据缺失，请联系管理员");
        }
        BigDecimal taxRate = request.getTaxRate() == null
                ? effective.getTaxRate()
                : RateCardMath.requireTaxRate(request.getTaxRate());
        String operator = Operators.resolve(request.getOperatorName());

        Integer nextNo = versionMapper.selectList(new LambdaQueryWrapper<RateCardVersion>()
                .eq(RateCardVersion::getCardId, cardId)).stream()
                .map(RateCardVersion::getVersionNo).max(Integer::compareTo).orElse(0) + 1;

        RateCardVersion draft = new RateCardVersion();
        draft.setCardId(cardId);
        draft.setVersionNo(nextNo);
        draft.setSource(RateCardEnums.VERSION_SOURCE_REVISE);
        draft.setStatus(RateCardEnums.VERSION_DRAFT);
        draft.setTaxRate(taxRate);
        draft.setOperatorName(operator);
        draft.setRemark(request.getRemark());
        versionMapper.insert(draft);

        for (RateCardPrice src : priceMapper.selectList(new LambdaQueryWrapper<RateCardPrice>()
                .eq(RateCardPrice::getVersionId, effective.getId()))) {
            RateCardPrice copy = new RateCardPrice();
            copy.setVersionId(draft.getId());
            copy.setSiteId(src.getSiteId());
            copy.setListNetPrice(src.getListNetPrice());
            priceMapper.insert(copy);
        }
        for (RateCardTier src : tierMapper.selectList(new LambdaQueryWrapper<RateCardTier>()
                .eq(RateCardTier::getVersionId, effective.getId())
                .orderByAsc(RateCardTier::getTierNo))) {
            RateCardTier copy = new RateCardTier();
            copy.setVersionId(draft.getId());
            copy.setTierNo(src.getTierNo());
            copy.setMinWeeks(src.getMinWeeks());
            copy.setMaxWeeks(src.getMaxWeeks());
            copy.setDiscountRate(src.getDiscountRate());
            tierMapper.insert(copy);
        }
        return draft;
    }

    /** 草稿改税率（版本内税率固化，只允许草稿态调整）。 */
    @Transactional(rollbackFor = Exception.class)
    public RateCardVersion updateDraftTaxRate(Long versionId, DraftVersionRequest request) {
        RateCardVersion version = requireDraftVersion(versionId);
        if (request.getTaxRate() == null) {
            throw new BusinessException("税率不能为空");
        }
        version.setTaxRate(RateCardMath.requireTaxRate(request.getTaxRate()));
        if (request.getRemark() != null) {
            version.setRemark(request.getRemark());
        }
        versionMapper.updateById(version);
        return version;
    }

    /** 草稿态整表保存点位挂牌单价（全量替换）。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveLines(Long versionId, PriceLineSaveRequest request) {
        requireDraftVersion(versionId);
        List<PriceLineSaveRequest.Item> items = request.getLines();

        Set<Long> seen = new HashSet<>();
        for (PriceLineSaveRequest.Item item : items) {
            if (!seen.add(item.getSiteId())) {
                throw new BusinessException("同一草稿内点位 id=" + item.getSiteId() + " 的单价行重复，请合并为一行");
            }
            if (siteMapper.selectById(item.getSiteId()) == null) {
                throw new BusinessException("点位不存在: id=" + item.getSiteId());
            }
            RateCardMath.requireMoney(item.getListNetPrice(), "点位 id=" + item.getSiteId() + " 的挂牌单价");
        }

        priceMapper.delete(new LambdaQueryWrapper<RateCardPrice>()
                .eq(RateCardPrice::getVersionId, versionId));
        for (PriceLineSaveRequest.Item item : items) {
            RateCardPrice line = new RateCardPrice();
            line.setVersionId(versionId);
            line.setSiteId(item.getSiteId());
            line.setListNetPrice(item.getListNetPrice());
            priceMapper.insert(line);
        }
    }

    /** 草稿态整表保存周数阶梯（全量替换，服务端排序+校验，tierNo 由顺序决定）。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveTiers(Long versionId, TierSaveRequest request) {
        requireDraftVersion(versionId);
        List<TierSpec> specs = request.getTiers().stream()
                .map(t -> new TierSpec(t.getMinWeeks(), t.getMaxWeeks(),
                        RateCardMath.requireDiscountRate(t.getDiscountRate())))
                .collect(Collectors.toList());
        List<TierSpec> sorted = validateTiers(specs);

        tierMapper.delete(new LambdaQueryWrapper<RateCardTier>()
                .eq(RateCardTier::getVersionId, versionId));
        int no = 1;
        for (TierSpec spec : sorted) {
            RateCardTier tier = new RateCardTier();
            tier.setVersionId(versionId);
            tier.setTierNo(no++);
            tier.setMinWeeks(spec.minWeeks);
            tier.setMaxWeeks(spec.maxWeeks);
            tier.setDiscountRate(spec.discountRate);
            tierMapper.insert(tier);
        }
    }

    /**
     * 发布版本：冻结单价行与阶梯，本草稿置 EFFECTIVE（立即生效）或 PENDING（指定未来生效日）；
     * 立即生效时把当前生效版本置 SUPERSEDED 并切换刊例头指针。
     * 待生效版本到日由 resolveEffectiveVersion 懒激活（同一天的新旧版本按发布先后解析为新版）。条件更新兜底并发。
     */
    @Transactional(rollbackFor = Exception.class)
    public RateCardVersion publish(Long versionId, String explicitOperator, LocalDate effectiveDate) {
        RateCardVersion version = requireDraftVersion(versionId);
        RateCard card = requireCard(version.getCardId());

        Long priceCount = priceMapper.selectCount(new LambdaQueryWrapper<RateCardPrice>()
                .eq(RateCardPrice::getVersionId, versionId));
        if (priceCount == 0) {
            throw new BusinessException("请至少配置一个点位的挂牌单价后再发布");
        }
        List<RateCardTier> tierEntities = tierMapper.selectList(new LambdaQueryWrapper<RateCardTier>()
                .eq(RateCardTier::getVersionId, versionId)
                .orderByAsc(RateCardTier::getTierNo));
        if (tierEntities.isEmpty()) {
            throw new BusinessException("请至少配置一档周数阶梯后再发布");
        }
        validateTiers(tierEntities.stream()
                .map(t -> new TierSpec(t.getMinWeeks(), t.getMaxWeeks(), t.getDiscountRate()))
                .collect(Collectors.toList()));

        LocalDate today = LocalDate.now();
        // 生效日可补录过去（年中上线/补录报价时按正确生效日还原历史口径）；未来日则待生效
        boolean pending = effectiveDate != null && effectiveDate.isAfter(today);
        String operator = Operators.resolve(explicitOperator);
        LocalDateTime effectiveTime = (effectiveDate == null ? today : effectiveDate).atTime(LocalTime.MIN);

        // 立即生效时先把当前生效版本置 SUPERSEDED（首次发布影响 0 行，正常）；待生效不影响在效版本
        if (!pending) {
            RateCardVersion supersede = new RateCardVersion();
            supersede.setStatus(RateCardEnums.VERSION_SUPERSEDED);
            versionMapper.update(supersede, new LambdaUpdateWrapper<RateCardVersion>()
                    .eq(RateCardVersion::getCardId, card.getId())
                    .eq(RateCardVersion::getStatus, RateCardEnums.VERSION_EFFECTIVE));
        }

        // 草稿条件置生效/待生效，affected=0 说明被并发抢先
        RateCardVersion patch = new RateCardVersion();
        patch.setStatus(pending ? RateCardEnums.VERSION_PENDING : RateCardEnums.VERSION_EFFECTIVE);
        patch.setEffectiveTime(effectiveTime);
        patch.setPublishOperatorName(operator);
        int updated = versionMapper.update(patch, new LambdaUpdateWrapper<RateCardVersion>()
                .eq(RateCardVersion::getId, versionId)
                .eq(RateCardVersion::getStatus, RateCardEnums.VERSION_DRAFT));
        if (updated == 0) {
            throw new BusinessException("版本 v" + version.getVersionNo() + " 刚被发布或改动，请刷新后重试");
        }

        if (!pending) {
            RateCard cardPatch = new RateCard();
            cardPatch.setId(card.getId());
            cardPatch.setStatus(RateCardEnums.CARD_EFFECTIVE);
            cardPatch.setCurrentVersionId(versionId);
            cardPatch.setCurrentVersionNo(version.getVersionNo());
            rateCardMapper.updateById(cardPatch);
        }
        return versionMapper.selectById(versionId);
    }

    /**
     * 懒激活到期的待生效版本：把生效时刻已到的 PENDING 版本置生效，
     * 同卡旧 EFFECTIVE 置 SUPERSEDED，刊例头指针指向最新到期版本。
     * 按生效时刻升序逐个激活，保证同日多个版本时最终停在最新发布的一版。
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateDueVersions(LocalDate date) {
        LocalDate today = date == null ? LocalDate.now() : date;
        List<RateCardVersion> due = versionMapper.selectList(new LambdaQueryWrapper<RateCardVersion>()
                .eq(RateCardVersion::getStatus, RateCardEnums.VERSION_PENDING)
                .le(RateCardVersion::getEffectiveTime, today.atTime(LocalTime.MAX))
                .orderByAsc(RateCardVersion::getEffectiveTime).orderByAsc(RateCardVersion::getId));
        for (RateCardVersion pending : due) {
            RateCardVersion activate = new RateCardVersion();
            activate.setStatus(RateCardEnums.VERSION_EFFECTIVE);
            int rows = versionMapper.update(activate, new LambdaUpdateWrapper<RateCardVersion>()
                    .eq(RateCardVersion::getId, pending.getId())
                    .eq(RateCardVersion::getStatus, RateCardEnums.VERSION_PENDING));
            if (rows == 0) {
                continue;
            }
            RateCardVersion supersede = new RateCardVersion();
            supersede.setStatus(RateCardEnums.VERSION_SUPERSEDED);
            versionMapper.update(supersede, new LambdaUpdateWrapper<RateCardVersion>()
                    .eq(RateCardVersion::getCardId, pending.getCardId())
                    .eq(RateCardVersion::getStatus, RateCardEnums.VERSION_EFFECTIVE)
                    .ne(RateCardVersion::getId, pending.getId()));

            RateCard cardPatch = new RateCard();
            cardPatch.setId(pending.getCardId());
            cardPatch.setStatus(RateCardEnums.CARD_EFFECTIVE);
            cardPatch.setCurrentVersionId(pending.getId());
            cardPatch.setCurrentVersionNo(pending.getVersionNo());
            rateCardMapper.updateById(cardPatch);
        }
    }

    /** 刊例翻查（年份可选），按年度倒序。 */
    public List<RateCard> listCards(Integer year) {
        LambdaQueryWrapper<RateCard> wrapper = new LambdaQueryWrapper<>();
        if (year != null) {
            wrapper.eq(RateCard::getCardYear, year);
        }
        return rateCardMapper.selectList(wrapper.orderByDesc(RateCard::getCardYear));
    }

    /** 刊例详情（含全部版本，版本号倒序）。 */
    public CardView getCard(Long cardId) {
        RateCard card = requireCard(cardId);
        CardView view = new CardView();
        view.setId(card.getId());
        view.setCardNo(card.getCardNo());
        view.setCardYear(card.getCardYear());
        view.setCardName(card.getCardName());
        view.setStatus(card.getStatus());
        view.setCurrentVersionId(card.getCurrentVersionId());
        view.setCurrentVersionNo(card.getCurrentVersionNo());
        view.setOperatorName(card.getOperatorName());
        view.setRemark(card.getRemark());
        view.setVersions(versionMapper.selectList(new LambdaQueryWrapper<RateCardVersion>()
                        .eq(RateCardVersion::getCardId, cardId)
                        .orderByDesc(RateCardVersion::getVersionNo)).stream()
                .map(this::toVersionView).collect(Collectors.toList()));
        return view;
    }

    /** 版本详情：版本头 + 点位单价行（补点位编号/尺寸/商圈）+ 阶梯行。 */
    public VersionDetailView getVersionDetail(Long versionId) {
        RateCardVersion version = requireVersion(versionId);
        RateCard card = requireCard(version.getCardId());

        VersionDetailView view = new VersionDetailView();
        view.setId(version.getId());
        view.setCardId(card.getId());
        view.setCardNo(card.getCardNo());
        view.setCardYear(card.getCardYear());
        view.setVersionNo(version.getVersionNo());
        view.setSource(version.getSource());
        view.setStatus(version.getStatus());
        view.setTaxRate(version.getTaxRate());
        view.setEffectiveTime(version.getEffectiveTime());
        view.setPublishOperatorName(version.getPublishOperatorName());
        view.setOperatorName(version.getOperatorName());
        view.setRemark(version.getRemark());

        List<RateCardPrice> prices = priceMapper.selectList(new LambdaQueryWrapper<RateCardPrice>()
                .eq(RateCardPrice::getVersionId, versionId));
        Map<Long, Site> sites;
        if (prices.isEmpty()) {
            sites = java.util.Collections.emptyMap();
        } else {
            sites = siteMapper.selectBatchIds(prices.stream()
                    .map(RateCardPrice::getSiteId).collect(Collectors.toList())).stream()
                    .collect(Collectors.toMap(Site::getId, Function.identity()));
        }
        List<PriceLineView> priceViews = prices.stream().map(p -> {
            PriceLineView row = new PriceLineView();
            row.setId(p.getId());
            row.setVersionId(versionId);
            row.setSiteId(p.getSiteId());
            row.setListNetPrice(p.getListNetPrice());
            Site site = sites.get(p.getSiteId());
            if (site != null) {
                row.setSiteNo(site.getSiteNo());
                row.setSizeSpec(site.getSizeSpec());
                row.setBusinessDistrict(site.getBusinessDistrict());
            }
            return row;
        }).sorted(Comparator.comparing(v -> v.getSiteNo() == null ? "" : v.getSiteNo()))
                .collect(Collectors.toList());
        view.setPriceLines(priceViews);

        view.setTiers(tierMapper.selectList(new LambdaQueryWrapper<RateCardTier>()
                        .eq(RateCardTier::getVersionId, versionId)
                        .orderByAsc(RateCardTier::getTierNo)).stream()
                .map(t -> {
                    TierView row = new TierView();
                    row.setId(t.getId());
                    row.setTierNo(t.getTierNo());
                    row.setMinWeeks(t.getMinWeeks());
                    row.setMaxWeeks(t.getMaxWeeks());
                    row.setDiscountRate(t.getDiscountRate());
                    return row;
                }).collect(Collectors.toList()));
        return view;
    }

    /**
     * 按报价日解析当天生效的刊例版本——计价引擎与财务对照表的唯一解析口径。
     * 取该年度刊例下「已发布（EFFECTIVE/SUPERSEDED）且生效时刻不晚于报价日当天结束」中生效时刻最近的一版。
     */
    public EffectiveVersion resolveEffectiveVersion(LocalDate date) {
        LocalDate quoteDate = date == null ? LocalDate.now() : date;
        // 先把到日的待生效版本激活（按当前日期；补录过去报价时下面直接按生效时刻解析，不依赖激活）
        activateDueVersions(LocalDate.now());
        int year = quoteDate.getYear();
        RateCard card = rateCardMapper.selectOne(
                new LambdaQueryWrapper<RateCard>().eq(RateCard::getCardYear, year));
        if (card == null) {
            throw new BusinessException(year + " 年度刊例尚未建立，无法报价");
        }
        // PENDING 且生效时刻已到报价日的版本也算数（补录历史报价：该版本在报价日当天就是生效版本）
        RateCardVersion version = versionMapper.selectOne(new LambdaQueryWrapper<RateCardVersion>()
                .eq(RateCardVersion::getCardId, card.getId())
                .in(RateCardVersion::getStatus,
                        RateCardEnums.VERSION_EFFECTIVE,
                        RateCardEnums.VERSION_SUPERSEDED,
                        RateCardEnums.VERSION_PENDING)
                .le(RateCardVersion::getEffectiveTime, quoteDate.atTime(LocalTime.MAX))
                .orderByDesc(RateCardVersion::getEffectiveTime)
                .orderByDesc(RateCardVersion::getId)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException(year + " 年度刊例尚无生效版本（草稿不能用于报价），报价日期 "
                    + quoteDate + " 无法计价");
        }
        return new EffectiveVersion(card, version);
    }

    /** 版本下全部单价行（计价引擎用）。 */
    public List<RateCardPrice> listPrices(Long versionId) {
        return priceMapper.selectList(new LambdaQueryWrapper<RateCardPrice>()
                .eq(RateCardPrice::getVersionId, versionId));
    }

    /** 版本下全部阶梯（按档位升序，计价引擎用）。 */
    public List<RateCardTier> listTiers(Long versionId) {
        return tierMapper.selectList(new LambdaQueryWrapper<RateCardTier>()
                .eq(RateCardTier::getVersionId, versionId)
                .orderByAsc(RateCardTier::getMinWeeks));
    }

    private RateCard requireCard(Long cardId) {
        RateCard card = rateCardMapper.selectById(cardId);
        if (card == null) {
            throw new BusinessException("年度刊例不存在: id=" + cardId);
        }
        return card;
    }

    private RateCardVersion requireVersion(Long versionId) {
        RateCardVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("刊例版本不存在: id=" + versionId);
        }
        return version;
    }

    private RateCardVersion requireDraftVersion(Long versionId) {
        RateCardVersion version = requireVersion(versionId);
        if (!RateCardEnums.VERSION_DRAFT.equals(version.getStatus())) {
            throw new BusinessException("刊例版本 v" + version.getVersionNo() + " 已是「"
                    + versionStatusText(version.getStatus()) + "」状态，单价、阶梯与税率已冻结，不能修改；"
                    + "如需调价请基于生效版本另开修订草稿");
        }
        return version;
    }

    private VersionView toVersionView(RateCardVersion version) {
        VersionView view = new VersionView();
        view.setId(version.getId());
        view.setCardId(version.getCardId());
        view.setVersionNo(version.getVersionNo());
        view.setSource(version.getSource());
        view.setStatus(version.getStatus());
        view.setTaxRate(version.getTaxRate());
        view.setEffectiveTime(version.getEffectiveTime());
        view.setPublishOperatorName(version.getPublishOperatorName());
        view.setOperatorName(version.getOperatorName());
        view.setRemark(version.getRemark());
        view.setCreateTime(version.getCreateTime());
        return view;
    }

    /**
     * 阶梯配置校验，返回按下限升序排好的档位。规则：
     * 从 1 周开始覆盖；双闭区间无缝拼接（不重叠、不断档）；仅最后一档允许无上限；折扣率随周数单调不增。
     */
    private List<TierSpec> validateTiers(List<TierSpec> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException("至少配置一档周数阶梯");
        }
        List<TierSpec> tiers = new ArrayList<>(raw);
        tiers.sort(Comparator.comparingInt(t -> t.minWeeks));

        TierSpec first = tiers.get(0);
        if (first.minWeeks != 1) {
            throw new BusinessException("阶梯必须从 1 周开始覆盖，当前第一档从 " + first.minWeeks + " 周开始");
        }
        for (int i = 0; i < tiers.size(); i++) {
            TierSpec cur = tiers.get(i);
            if (cur.minWeeks < 1) {
                throw new BusinessException("档位周数下限至少为 1 周");
            }
            if (cur.maxWeeks != null && cur.maxWeeks < cur.minWeeks) {
                throw new BusinessException("第 " + (i + 1) + " 档周数上限(" + cur.maxWeeks
                        + ")不能小于下限(" + cur.minWeeks + ")");
            }
            boolean last = i == tiers.size() - 1;
            if (cur.maxWeeks == null && !last) {
                throw new BusinessException("只有最后一档可以无上限，第 " + (i + 1) + " 档的周数上限不能为空");
            }
            if (last && cur.maxWeeks == null) {
                // 末档无上限，合法
            }
            if (i > 0) {
                TierSpec prev = tiers.get(i - 1);
                if (cur.minWeeks <= prev.maxWeeks) {
                    throw new BusinessException("周数区间重叠：第 " + i + " 档(" + prev.minWeeks + "-"
                            + (prev.maxWeeks == null ? "∞" : prev.maxWeeks) + "周)与第 " + (i + 1)
                            + " 档(" + cur.minWeeks + "-" + (cur.maxWeeks == null ? "∞" : cur.maxWeeks) + "周)");
                }
                if (prev.maxWeeks != null && cur.minWeeks != prev.maxWeeks + 1) {
                    throw new BusinessException("周数区间断档：第 " + i + " 档结束于 " + prev.maxWeeks
                            + " 周，第 " + (i + 1) + " 档从 " + cur.minWeeks + " 周开始，请补齐中间周数");
                }
                if (cur.discountRate.compareTo(prev.discountRate) > 0) {
                    throw new BusinessException("买得越久每周不能越贵：第 " + (i + 1) + " 档折扣率("
                            + cur.discountRate.toPlainString() + ")不得高于第 " + i + " 档("
                            + prev.discountRate.toPlainString() + ")");
                }
            }
        }
        return tiers;
    }

    private String versionStatusText(String status) {
        if (RateCardEnums.VERSION_DRAFT.equals(status)) {
            return "草稿";
        }
        if (RateCardEnums.VERSION_PENDING.equals(status)) {
            return "待生效";
        }
        if (RateCardEnums.VERSION_EFFECTIVE.equals(status)) {
            return "生效中";
        }
        if (RateCardEnums.VERSION_SUPERSEDED.equals(status)) {
            return "已被新版取代";
        }
        return status;
    }

    /** 校验用的档位中间结构。 */
    private static class TierSpec {
        final int minWeeks;
        final Integer maxWeeks;
        final BigDecimal discountRate;

        TierSpec(Integer minWeeks, Integer maxWeeks, BigDecimal discountRate) {
            if (minWeeks == null) {
                throw new BusinessException("档位周数下限不能为空");
            }
            this.minWeeks = minWeeks;
            this.maxWeeks = maxWeeks;
            this.discountRate = discountRate;
        }
    }
}
