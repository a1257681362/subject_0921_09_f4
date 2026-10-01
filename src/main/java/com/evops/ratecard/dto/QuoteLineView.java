package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 报价单行视图：逐行快照，含税/不含税并列展示。 */
@Data
public class QuoteLineView {
    private Long id;
    private Integer lineNo;
    private Long siteId;
    private String siteNo;
    /** 购买周数 */
    private Integer weeks;
    /** 挂牌周单价（不含税）快照 */
    private BigDecimal listNetUnit;
    private Long tierId;
    private Integer tierNo;
    private Integer minWeeks;
    /** null=无上限 */
    private Integer maxWeeks;
    /** 命中档位展示串，如「4-7 周」「8 周起」 */
    private String tierRangeText;
    private BigDecimal discountRate;
    /** 折后不含税周单价 */
    private BigDecimal netUnit;
    private BigDecimal netAmount;
    private BigDecimal taxAmount;
    /** 含税周单价 */
    private BigDecimal grossUnit;
    private BigDecimal grossAmount;
}
