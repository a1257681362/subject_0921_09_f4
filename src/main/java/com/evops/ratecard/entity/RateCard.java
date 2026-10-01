package com.evops.ratecard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 年度刊例头：公司级一年一张，card_year 唯一；current_version_* 始终指向当前生效版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_rate_card")
public class RateCard extends BaseEntity {
    /** 刊例编号，如 RC2026 */
    private String cardNo;
    /** 刊例年度，报价按提交日年份取它 */
    private Integer cardYear;
    /** 刊例名称（可空） */
    private String cardName;
    /** 刊例头状态：DRAFT 尚无生效版本 / EFFECTIVE 已有生效版本 */
    private String status;
    /** 当前生效版本 id（从未发布为 null） */
    private Long currentVersionId;
    /** 当前生效版本号 */
    private Integer currentVersionNo;
    /** 建档经手人 */
    private String operatorName;
    private String remark;
}
