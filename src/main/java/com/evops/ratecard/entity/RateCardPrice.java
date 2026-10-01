package com.evops.ratecard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 版本点位挂牌单价行：一行一个点位在该版本的按周挂牌单价（不含税）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_rate_card_price")
public class RateCardPrice extends BaseEntity {
    /** 所属刊例版本 */
    private Long versionId;
    /** 点位 id */
    private Long siteId;
    /** 按周挂牌单价（不含税，2 位小数） */
    private BigDecimal listNetPrice;
}
