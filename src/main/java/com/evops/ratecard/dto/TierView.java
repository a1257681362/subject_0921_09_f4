package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 版本周数阶梯视图。 */
@Data
public class TierView {
    private Long id;
    private Integer tierNo;
    private Integer minWeeks;
    /** null=无上限 */
    private Integer maxWeeks;
    /** 实收比例（分数） */
    private BigDecimal discountRate;
}
