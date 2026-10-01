package com.evops.ratecard.support;

import com.evops.ratecard.entity.RateCard;
import com.evops.ratecard.entity.RateCardVersion;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 按报价日解析出的「年度刊例 + 当天生效版本」组合，计价引擎与财务对照表共用同一解析口径。 */
@Data
@AllArgsConstructor
public class EffectiveVersion {
    /** 年度刊例头 */
    private RateCard card;
    /** 报价日当天生效的版本（可能已被更新版本取代，但历史报价就是按它算的） */
    private RateCardVersion version;
}
