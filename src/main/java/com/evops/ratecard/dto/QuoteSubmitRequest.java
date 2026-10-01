package com.evops.ratecard.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 报价单提交/试算请求（试算与提交同一 DTO，同一套口径，试算不落库）。
 * 报价日期留空取今天；允许补录过去日期（按当天生效版本计价），不允许晚于今天。
 */
@Data
public class QuoteSubmitRequest {
    /** 报价日期（业务计价日），留空取今天 */
    private LocalDate quoteDate;

    /** 客户名称（可空） */
    private String customerName;

    /** 报价明细行，支持多个点位；同一点位不允许拆多行（请合并周数） */
    @NotEmpty(message = "报价至少包含一行点位/周数")
    @Valid
    private List<Line> lines;

    /** 经手人，留空取当前登录账号 */
    private String operatorName;

    private String remark;

    @Data
    public static class Line {
        /** 点位 id */
        @NotNull(message = "点位 id 不能为空")
        private Long siteId;

        /** 购买周数（正整数），按周数落阶梯折扣 */
        @NotNull(message = "购买周数不能为空")
        @Min(value = 1, message = "购买周数至少为 1 周")
        private Integer weeks;
    }
}
