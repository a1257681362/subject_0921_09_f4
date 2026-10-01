package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 版本点位挂牌单价视图（join 点位档案补编号/尺寸/商圈，便于整表编辑）。 */
@Data
public class PriceLineView {
    private Long id;
    private Long versionId;
    private Long siteId;
    private String siteNo;
    private String sizeSpec;
    private String businessDistrict;
    /** 按周挂牌单价（不含税） */
    private BigDecimal listNetPrice;
}
