package com.evops.ratecard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 版本购买周数阶梯折扣：双闭区间无缝拼接（1 周起步），
 * 仅最后一档 maxWeeks 为空表示无上限；折扣率（实收比例）随周数单调不增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_rate_card_tier")
public class RateCardTier extends BaseEntity {
    /** 所属刊例版本 */
    private Long versionId;
    /** 档位序号，从 1 起，按周数升序 */
    private Integer tierNo;
    /** 本档最少周数（含） */
    private Integer minWeeks;
    /** 本档最多周数（含）；null = 无上限（仅最后一档） */
    private Integer maxWeeks;
    /** 实收比例（分数：0.9500=95 折），0<x<=1 */
    private BigDecimal discountRate;
}
