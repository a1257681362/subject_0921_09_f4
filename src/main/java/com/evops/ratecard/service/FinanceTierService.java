package com.evops.ratecard.service;

import com.evops.common.BusinessException;
import com.evops.ledger.entity.Site;
import com.evops.ledger.mapper.SiteMapper;
import com.evops.ratecard.dto.FinanceTierView;
import com.evops.ratecard.entity.RateCard;
import com.evops.ratecard.entity.RateCardPrice;
import com.evops.ratecard.entity.RateCardTier;
import com.evops.ratecard.entity.RateCardVersion;
import com.evops.ratecard.support.EffectiveVersion;
import com.evops.ratecard.support.RateCardMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 财务阶梯对照表：随查随拉。复用 PricingEngine 同源的「按日报价解析生效版本」口径，
 * 可指定一个点位附带各档位下的挂牌/折后净单价/含税单价；支持整表 CSV 导出（UTF-8 BOM，Excel 不乱码）。
 */
@Service
public class FinanceTierService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RateCardService rateCardService;
    private final SiteMapper siteMapper;

    public FinanceTierService(RateCardService rateCardService, SiteMapper siteMapper) {
        this.rateCardService = rateCardService;
        this.siteMapper = siteMapper;
    }

    /** 某日生效版本的阶梯对照表；siteId 非空时附带该点位各档位示例周单价。 */
    public FinanceTierView tierTable(LocalDate date, Long siteId) {
        LocalDate quoteDate = date == null ? LocalDate.now() : date;
        EffectiveVersion effective = rateCardService.resolveEffectiveVersion(quoteDate);
        RateCard card = effective.getCard();
        RateCardVersion version = effective.getVersion();

        RateCardPrice sitePrice = null;
        Site site = null;
        if (siteId != null) {
            Site found = siteMapper.selectById(siteId);
            if (found == null) {
                throw new BusinessException("点位不存在: id=" + siteId);
            }
            site = found;
            final Site foundSite = found;
            sitePrice = rateCardService.listPrices(version.getId()).stream()
                    .filter(p -> siteId.equals(p.getSiteId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("点位 " + foundSite.getSiteNo() + " 在 "
                            + card.getCardYear() + " 年度刊例 v" + version.getVersionNo()
                            + " 中未配置挂牌单价，无法生成对照单价"));
        }

        List<RateCardTier> tiers = rateCardService.listTiers(version.getId());
        final RateCardPrice price = sitePrice;
        List<FinanceTierView.Row> rows = tiers.stream().map(t -> {
            FinanceTierView.Row row = new FinanceTierView.Row();
            row.setTierNo(t.getTierNo());
            row.setMinWeeks(t.getMinWeeks());
            row.setMaxWeeks(t.getMaxWeeks());
            row.setDiscountRate(t.getDiscountRate());
            if (price != null) {
                BigDecimal netUnit = RateCardMath.netUnit(price.getListNetPrice(), t.getDiscountRate());
                row.setListNetUnit(price.getListNetPrice());
                row.setNetUnit(netUnit);
                row.setGrossUnit(RateCardMath.grossOf(netUnit, version.getTaxRate()));
            }
            return row;
        }).collect(Collectors.toList());

        FinanceTierView view = new FinanceTierView();
        view.setCardNo(card.getCardNo());
        view.setCardYear(card.getCardYear());
        view.setVersionId(version.getId());
        view.setVersionNo(version.getVersionNo());
        view.setEffectiveTime(version.getEffectiveTime());
        view.setTaxRate(version.getTaxRate());
        if (site != null) {
            view.setSiteId(site.getId());
            view.setSiteNo(site.getSiteNo());
        }
        view.setTiers(rows);
        return view;
    }

    /** 整表导出 CSV（BOM 头；未指定点位时示例单价列留空而不是省略列）。 */
    public String exportCsv(LocalDate date, Long siteId) {
        FinanceTierView view = tierTable(date, siteId);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("刊例编号,年度,版本号,版本生效时间,税率,档位,周数下限,周数上限(空=无上限),折扣率,")
                .append("点位编号,挂牌周单价(不含税),折后周单价(不含税),折后周单价(含税)\n");
        for (FinanceTierView.Row row : view.getTiers()) {
            csv.append(escape(view.getCardNo())).append(',')
                    .append(view.getCardYear()).append(',')
                    .append("v").append(view.getVersionNo()).append(',')
                    .append(view.getEffectiveTime() == null ? "" : view.getEffectiveTime().format(TIME_FMT)).append(',')
                    .append(view.getTaxRate().toPlainString()).append(',')
                    .append("第").append(row.getTierNo()).append("档").append(',')
                    .append(row.getMinWeeks()).append(',')
                    .append(row.getMaxWeeks() == null ? "" : row.getMaxWeeks()).append(',')
                    .append(row.getDiscountRate().toPlainString()).append(',')
                    .append(escape(view.getSiteNo())).append(',')
                    .append(row.getListNetUnit() == null ? "" : row.getListNetUnit().toPlainString()).append(',')
                    .append(row.getNetUnit() == null ? "" : row.getNetUnit().toPlainString()).append(',')
                    .append(row.getGrossUnit() == null ? "" : row.getGrossUnit().toPlainString())
                    .append('\n');
        }
        return csv.toString();
    }

    /** CSV 单元格转义：含逗号/引号/换行时整体加引号，内部引号翻倍。 */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
