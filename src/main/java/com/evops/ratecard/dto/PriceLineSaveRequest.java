package com.evops.ratecard.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** 草稿版本点位挂牌单价批量保存（全量替换语义：按提交整表覆盖草稿现有行）。 */
@Data
public class PriceLineSaveRequest {

    /** 单价行，整表提交；空表不允许（发布要求至少一个点位有价） */
    @NotEmpty(message = "至少提交一条点位单价行")
    @Valid
    private List<Item> lines;

    /** 经手人，留空取当前登录账号 */
    private String operatorName;

    @Data
    public static class Item {
        /** 点位 id */
        @NotNull(message = "点位 id 不能为空")
        private Long siteId;

        /** 按周挂牌单价（不含税），必须大于 0、最多 2 位小数 */
        @NotNull(message = "挂牌单价不能为空")
        private BigDecimal listNetPrice;
    }
}
