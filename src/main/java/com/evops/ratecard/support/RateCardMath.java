package com.evops.ratecard.support;

import com.evops.common.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 刊例计价口径：单价按周、不含税录入，金额一律 BigDecimal、HALF_UP、2 位小数；
 * 税率/折扣率为 4 位小数分数。超小数位直接拒收，不静默截断（与 GeoMath 同一哲学）。
 * 含税金额从不含税金额换算（权威），与「含税单价×周数」可能差 1 分。
 */
public final class RateCardMath {

    private RateCardMath() {}

    /** 金额小数位（DECIMAL(12,2)/DECIMAL(14,2)） */
    public static final int MONEY_SCALE = 2;
    /** 税率/折扣率小数位（DECIMAL(5,4)） */
    public static final int RATE_SCALE = 4;

    private static final BigDecimal RATE_ONE = new BigDecimal("1");

    /** 校验挂牌单价/金额：必填、大于 0、最多 2 位小数。 */
    public static BigDecimal requireMoney(BigDecimal value, String label) {
        if (value == null) {
            throw new BusinessException(label + "不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(label + "必须大于 0：" + value.toPlainString());
        }
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BusinessException(label + "最多支持 " + MONEY_SCALE + " 位小数，请核对后再录：" + value.toPlainString());
        }
        return value;
    }

    /** 校验税率（分数）：必填、区间 [0,1]（允许免税 0）、最多 4 位小数。 */
    public static BigDecimal requireTaxRate(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("税率不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(RATE_ONE) > 0) {
            throw new BusinessException("税率必须在 0~1 之间（分数，如 0.06 表示 6%）：" + value.toPlainString());
        }
        requireRateScale(value, "税率");
        return value;
    }

    /** 校验折扣率（实收比例，分数）：必填、区间 (0,1]（免费不是折扣）、最多 4 位小数。 */
    public static BigDecimal requireDiscountRate(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("折扣率不能为空");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(RATE_ONE) > 0) {
            throw new BusinessException("折扣率必须在 0~1 之间且大于 0（分数，如 0.95 表示 95 折）：" + value.toPlainString());
        }
        requireRateScale(value, "折扣率");
        return value;
    }

    private static void requireRateScale(BigDecimal value, String label) {
        if (value.stripTrailingZeros().scale() > RATE_SCALE) {
            throw new BusinessException(label + "最多支持 " + RATE_SCALE + " 位小数，请核对后再录：" + value.toPlainString());
        }
    }

    /** 金额归一：HALF_UP 保留 2 位。 */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 折后不含税周单价 = 挂牌净单价 × 折扣率。 */
    public static BigDecimal netUnit(BigDecimal listNetPrice, BigDecimal discountRate) {
        return money(listNetPrice.multiply(discountRate));
    }

    /** 不含税金额 = 折后不含税周单价 × 周数。 */
    public static BigDecimal netAmount(BigDecimal netUnit, int weeks) {
        return money(netUnit.multiply(BigDecimal.valueOf(weeks)));
    }

    /** 含税单价/金额 = 不含税值 × (1+税率)。 */
    public static BigDecimal grossOf(BigDecimal netValue, BigDecimal taxRate) {
        return money(netValue.multiply(BigDecimal.ONE.add(taxRate)));
    }
}
