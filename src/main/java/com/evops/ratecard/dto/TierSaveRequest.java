package com.evops.ratecard.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 草稿版本周数阶梯批量保存（全量替换语义）。tierNo 不接受前端传入——
 * 档位顺序由数组按下限周数排序后决定，避免错位。
 */
@Data
public class TierSaveRequest {

    /** 阶梯行，整表提交 */
    @NotEmpty(message = "至少配置一档周数阶梯")
    @Valid
    private List<Item> tiers;

    /** 经手人，留空取当前登录账号 */
    private String operatorName;

    @Data
    public static class Item {
        /** 本档最少周数（含），从 1 开始 */
        @NotNull(message = "档位周数下限不能为空")
        @Min(value = 1, message = "档位周数下限至少为 1 周")
        private Integer minWeeks;

        /** 本档最多周数（含），仅最后一档允许留空（无上限） */
        private Integer maxWeeks;

        /** 实收比例（分数，如 0.95=95 折），0<x<=1、最多 4 位小数，且须随周数单调不增 */
        @NotNull(message = "折扣率不能为空")
        private BigDecimal discountRate;
    }
}
