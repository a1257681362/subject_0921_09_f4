package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 刊例版本列表项视图。 */
@Data
public class VersionView {
    private Long id;
    private Long cardId;
    /** 版本号 */
    private Integer versionNo;
    /** 版本来源：INITIAL 首版 / REVISE 修订版 */
    private String source;
    /** 版本状态：DRAFT / EFFECTIVE / SUPERSEDED */
    private String status;
    /** 税率（分数） */
    private BigDecimal taxRate;
    /** 发布生效时刻 */
    private LocalDateTime effectiveTime;
    /** 发布经手人 */
    private String publishOperatorName;
    /** 建版经手人 */
    private String operatorName;
    private String remark;
    private LocalDateTime createTime;
}
