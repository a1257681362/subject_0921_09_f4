package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 开修订草稿请求：基于当前生效版本另起一版（复制单价行、阶梯、税率）。
 * 只有已存在生效版本的刊例才能开修订草稿，且同时只能有一个草稿。
 */
@Data
public class DraftVersionRequest {
    /** 税率（分数），留空沿用上一生效版本；最多 4 位小数，区间 0~1 */
    private BigDecimal taxRate;

    /** 经手人，留空取当前登录账号 */
    private String operatorName;

    private String remark;
}
