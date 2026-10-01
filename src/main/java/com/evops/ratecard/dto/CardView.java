package com.evops.ratecard.dto;

import lombok.Data;

import java.util.List;

/** 年度刊例详情视图：刊例头 + 全部版本（版本号倒序）。 */
@Data
public class CardView {
    private Long id;
    /** 刊例编号，如 RC2026 */
    private String cardNo;
    /** 刊例年度 */
    private Integer cardYear;
    private String cardName;
    /** 刊例头状态：DRAFT / EFFECTIVE */
    private String status;
    private Long currentVersionId;
    private Integer currentVersionNo;
    private String operatorName;
    private String remark;
    /** 全部版本（版本号倒序） */
    private List<VersionView> versions;
}
