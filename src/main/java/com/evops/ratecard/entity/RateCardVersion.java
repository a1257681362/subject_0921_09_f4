package com.evops.ratecard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 年度刊例版本：DRAFT 草稿可改 / EFFECTIVE 生效中 / SUPERSEDED 已被新版取代；
 * 税率、点位挂牌单价、周数阶梯都挂在版本上，发布即冻结。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_rate_card_version")
public class RateCardVersion extends BaseEntity {
    /** 所属年度刊例 */
    private Long cardId;
    /** 版本号，刊例内单调递增，首版 v1 */
    private Integer versionNo;
    /** 版本来源：INITIAL 首版 / REVISE 修订版 */
    private String source;
    /** 版本状态：DRAFT / EFFECTIVE / SUPERSEDED */
    private String status;
    /** 广告服务增值税率（分数，默认 0.0600=6%），版本内固化 */
    private BigDecimal taxRate;
    /** 发布生效时刻；报价按报价日反查当时版本 */
    private LocalDateTime effectiveTime;
    /** 发布经手人（草稿期为空） */
    private String publishOperatorName;
    /** 建版经手人 */
    private String operatorName;
    private String remark;
}
