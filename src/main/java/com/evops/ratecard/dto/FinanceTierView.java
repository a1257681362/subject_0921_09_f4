package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 财务阶梯对照表视图：某日报价所用生效版本的全部档位；
 * 指定点位时附带该点位在各档位下的挂牌/折后净单价/含税单价（未指定则三列留空）。
 */
@Data
public class FinanceTierView {
    private String cardNo;
    private Integer cardYear;
    private Long versionId;
    private Integer versionNo;
    private LocalDateTime effectiveTime;
    private BigDecimal taxRate;
    /** 参照点位 id/编号（可空） */
    private Long siteId;
    private String siteNo;
    private List<Row> tiers;

    @Data
    public static class Row {
        private Integer tierNo;
        private Integer minWeeks;
        /** null=无上限 */
        private Integer maxWeeks;
        private BigDecimal discountRate;
        /** 挂牌周单价（不含税），未指定点位时为空 */
        private BigDecimal listNetUnit;
        /** 折后周单价（不含税），未指定点位时为空 */
        private BigDecimal netUnit;
        /** 折后周单价（含税），未指定点位时为空 */
        private BigDecimal grossUnit;
    }
}
