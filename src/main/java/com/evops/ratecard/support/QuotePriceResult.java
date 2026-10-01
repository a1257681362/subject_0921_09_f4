package com.evops.ratecard.support;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单行计价结果：全系统唯一定价入口 {@code PricingEngine.price(...)} 的产物，
 * 字段一一对应报价单行快照——算和存是同一个对象，不存在第二套口径。
 */
@Data
public class QuotePriceResult {
    /** 报价日 */
    private java.time.LocalDate quoteDate;

    // —— 点位 ——
    private Long siteId;
    private String siteNo;
    /** 购买周数 */
    private int weeks;

    // —— 刊例版本快照 ——
    private Long cardId;
    private String cardNo;
    private Integer cardYear;
    private Long versionId;
    private Integer versionNo;
    private LocalDateTime versionEffectiveTime;
    private BigDecimal taxRate;

    // —— 命中档位快照 ——
    private Long tierId;
    private Integer tierNo;
    private Integer minWeeks;
    /** null=无上限 */
    private Integer maxWeeks;
    private BigDecimal discountRate;

    // —— 计价结果（含税/不含税并列） ——
    /** 挂牌周单价（不含税） */
    private BigDecimal listNetUnit;
    /** 折后不含税周单价 */
    private BigDecimal netUnit;
    /** 不含税金额 */
    private BigDecimal netAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 含税周单价（默认展示口径） */
    private BigDecimal grossUnit;
    /** 含税金额 */
    private BigDecimal grossAmount;
}
