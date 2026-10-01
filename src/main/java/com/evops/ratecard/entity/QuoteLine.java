package com.evops.ratecard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报价单行：每行一次独立计价的完整快照（挂牌单价、命中档位、折扣率、净/含税单价与金额）；
 * 刊例后调一个字节都不回写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_quote_line")
public class QuoteLine extends BaseEntity {
    /** 所属报价单 */
    private Long quoteId;
    /** 行号，单内从 1 起 */
    private Integer lineNo;
    /** 点位 id */
    private Long siteId;
    /** 点位编号快照 */
    private String siteNo;
    /** 购买周数（正整数） */
    private Integer weeks;
    /** 挂牌周单价（不含税）快照 */
    private BigDecimal listNetUnit;
    /** 命中档位 id 快照 */
    private Long tierId;
    /** 命中档位序号快照 */
    private Integer tierNo;
    /** 命中档位周数下限快照 */
    private Integer minWeeks;
    /** 命中档位周数上限快照，null=无上限 */
    private Integer maxWeeks;
    /** 命中档位折扣率快照 */
    private BigDecimal discountRate;
    /** 折后不含税周单价 */
    private BigDecimal netUnit;
    /** 不含税金额 */
    private BigDecimal netAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 含税周单价 */
    private BigDecimal grossUnit;
    /** 含税金额 */
    private BigDecimal grossAmount;
}
