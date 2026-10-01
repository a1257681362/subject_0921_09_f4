package com.evops.ratecard.dto;

import com.evops.ratecard.support.QuotePriceResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价单视图（提交结果、翻查详情、试算结果共用同一形状：试算时 id/quoteNo/status 为空）。
 * 头与行上的刊例编号/版本/单价/税率全部是提交当时的快照。
 */
@Data
public class QuoteView {
    private Long id;
    private String quoteNo;
    private LocalDate quoteDate;
    private String customerName;

    private Long cardId;
    private String cardNo;
    private Integer cardYear;
    private Long versionId;
    private Integer versionNo;
    private LocalDateTime versionEffectiveTime;
    private BigDecimal taxRate;

    private Integer totalWeeks;
    /** 不含税合计 */
    private BigDecimal netAmount;
    private BigDecimal taxAmount;
    /** 含税合计（默认展示口径） */
    private BigDecimal grossAmount;

    /** SUBMITTED；试算结果为空 */
    private String status;
    private String operatorName;
    private String remark;

    private List<QuoteLineView> lines;

    /** 试算/提交装配过程中的逐行计价结果（不向前端输出，仅提交落库时取用） */
    @JsonIgnore
    private List<QuotePriceResult> results;
}
