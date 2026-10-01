package com.evops.ratecard.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 刊例版本详情视图：版本头 + 点位单价行 + 周数阶梯行。 */
@Data
public class VersionDetailView {
    private Long id;
    private Long cardId;
    private String cardNo;
    private Integer cardYear;
    private Integer versionNo;
    private String source;
    private String status;
    private BigDecimal taxRate;
    private LocalDateTime effectiveTime;
    private String publishOperatorName;
    private String operatorName;
    private String remark;
    /** 点位挂牌单价行（按点位编号排序） */
    private List<PriceLineView> priceLines;
    /** 周数阶梯行（按档位序号升序） */
    private List<TierView> tiers;
}
