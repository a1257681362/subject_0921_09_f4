package com.evops.ratecard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价单头：提交即终态（只提交+翻查，无改/删/撤）；
 * 所用刊例版本与税率、合计金额全部为计价当时快照，刊例后调不回写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_quote")
public class Quote extends BaseEntity {
    /** 报价单号 QT+序列 */
    private String quoteNo;
    /** 提交当天（业务计价日，决定年度刊例与版本） */
    private LocalDate quoteDate;
    /** 客户名称（可空） */
    private String customerName;
    /** 刊例 id 快照 */
    private Long cardId;
    /** 刊例编号快照 */
    private String cardNo;
    /** 刊例年度快照 */
    private Integer cardYear;
    /** 计价所用刊例版本 id */
    private Long versionId;
    /** 计价所用刊例版本号 */
    private Integer versionNo;
    /** 计价所用版本生效时刻快照 */
    private LocalDateTime versionEffectiveTime;
    /** 税率快照 */
    private BigDecimal taxRate;
    /** 总周数（各行求和） */
    private Integer totalWeeks;
    /** 不含税合计 */
    private BigDecimal netAmount;
    /** 税额合计 */
    private BigDecimal taxAmount;
    /** 含税合计（按不含税金额×(1+税率) 换算） */
    private BigDecimal grossAmount;
    /** 状态：SUBMITTED 已提交 */
    private String status;
    /** 提交经手人 */
    private String operatorName;
    private String remark;
}
