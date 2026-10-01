package com.evops.ratecard.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 建年度刊例请求：建档同时自动生成 v1 草稿。 */
@Data
public class CardCreateRequest {
    /** 刊例年度（2000~2099），报价按提交日年份找本年度刊例 */
    @NotNull(message = "刊例年度不能为空")
    @Min(value = 2000, message = "刊例年度最早只支持 2000 年")
    @Max(value = 2099, message = "刊例年度最晚只支持 2099 年")
    private Integer cardYear;

    /** 刊例名称，留空取「YYYY 年度刊例」 */
    private String cardName;

    /** 税率（分数，默认 0.06），最多 4 位小数，区间 0~1 */
    private BigDecimal taxRate;

    /** 经手人，留空取当前登录账号 */
    private String operatorName;

    private String remark;
}
