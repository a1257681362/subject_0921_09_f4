package com.evops.ratecard.service;

import com.evops.common.BusinessException;
import com.evops.ledger.entity.Site;
import com.evops.ledger.mapper.SiteMapper;
import com.evops.ratecard.entity.RateCardPrice;
import com.evops.ratecard.entity.RateCardTier;
import com.evops.ratecard.support.EffectiveVersion;
import com.evops.ratecard.support.QuotePriceResult;
import com.evops.ratecard.support.RateCardMath;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 全系统唯一定价入口：报价试算、报价提交都只走这里，任何控制器/其他服务不得自行算钱。
 * 口径：按报价日解析年度刊例的当天生效版本 → 取点位不含税挂牌周单价 → 按周数双闭区间命中阶梯
 * → 折后净单价 = 挂牌净单价 × 折扣率；净金额 = 折后净单价 × 周数；
 * 含税值 = 不含税值 × (1+税率)；税额 = 含税金额 - 不含税金额。全部 BigDecimal、HALF_UP、2 位小数。
 */
@Component
public class PricingEngine {

    private final RateCardService rateCardService;
    private final SiteMapper siteMapper;

    public PricingEngine(RateCardService rateCardService, SiteMapper siteMapper) {
        this.rateCardService = rateCardService;
        this.siteMapper = siteMapper;
    }

    /**
     * 单行计价。
     *
     * @param siteId    点位 id
     * @param weeks     购买周数（正整数）
     * @param quoteDate 报价日（null=今天）；决定用哪一年刊例、哪一个当时生效版本
     * @return 可直接落报价单行快照的计价结果
     */
    public QuotePriceResult price(Long siteId, int weeks, LocalDate quoteDate) {
        if (siteId == null) {
            throw new BusinessException("点位 id 不能为空");
        }
        if (weeks < 1) {
            throw new BusinessException("购买周数至少为 1 周：" + weeks);
        }
        LocalDate date = quoteDate == null ? LocalDate.now() : quoteDate;

        EffectiveVersion effective = rateCardService.resolveEffectiveVersion(date);
        Site site = siteMapper.selectById(siteId);
        if (site == null) {
            throw new BusinessException("点位不存在: id=" + siteId);
        }

        RateCardPrice price = rateCardService.listPrices(effective.getVersion().getId()).stream()
                .filter(p -> siteId.equals(p.getSiteId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("点位 " + site.getSiteNo() + " 在 "
                        + date.getYear() + " 年度刊例 v" + effective.getVersion().getVersionNo()
                        + " 中未配置挂牌单价，不能报价"));

        List<RateCardTier> tiers = rateCardService.listTiers(effective.getVersion().getId());
        RateCardTier hit = tiers.stream()
                .filter(t -> weeks >= t.getMinWeeks() && (t.getMaxWeeks() == null || weeks <= t.getMaxWeeks()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("购买周数 " + weeks + " 未被 " + date.getYear()
                        + " 年度刊例的任何阶梯覆盖（最大档位到 "
                        + tiers.stream().map(t -> t.getMaxWeeks() == null ? null : t.getMaxWeeks())
                            .filter(java.util.Objects::nonNull).max(Integer::compareTo)
                            .map(String::valueOf).orElse("?")
                        + " 周），请补充阶梯后再报价"));

        BigDecimal taxRate = effective.getVersion().getTaxRate();
        BigDecimal netUnit = RateCardMath.netUnit(price.getListNetPrice(), hit.getDiscountRate());
        BigDecimal netAmount = RateCardMath.netAmount(netUnit, weeks);
        BigDecimal grossUnit = RateCardMath.grossOf(netUnit, taxRate);
        BigDecimal grossAmount = RateCardMath.grossOf(netAmount, taxRate);

        QuotePriceResult result = new QuotePriceResult();
        result.setQuoteDate(date);
        result.setSiteId(siteId);
        result.setSiteNo(site.getSiteNo());
        result.setWeeks(weeks);
        result.setCardId(effective.getCard().getId());
        result.setCardNo(effective.getCard().getCardNo());
        result.setCardYear(effective.getCard().getCardYear());
        result.setVersionId(effective.getVersion().getId());
        result.setVersionNo(effective.getVersion().getVersionNo());
        result.setVersionEffectiveTime(effective.getVersion().getEffectiveTime());
        result.setTaxRate(taxRate);
        result.setTierId(hit.getId());
        result.setTierNo(hit.getTierNo());
        result.setMinWeeks(hit.getMinWeeks());
        result.setMaxWeeks(hit.getMaxWeeks());
        result.setDiscountRate(hit.getDiscountRate());
        result.setListNetUnit(price.getListNetPrice());
        result.setNetUnit(netUnit);
        result.setNetAmount(netAmount);
        result.setGrossUnit(grossUnit);
        result.setGrossAmount(grossAmount);
        result.setTaxAmount(grossAmount.subtract(netAmount));
        return result;
    }
}
