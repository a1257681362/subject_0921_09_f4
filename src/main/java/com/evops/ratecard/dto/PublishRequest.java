package com.evops.ratecard.dto;

import lombok.Data;

import java.time.LocalDate;

/** 版本发布请求：发布后行/阶梯/税率全部冻结，旧生效版本置 SUPERSEDED。 */
@Data
public class PublishRequest {
    /** 经手人，留空取当前登录账号 */
    private String operatorName;

    /** 生效日期，留空立即生效；可预发来年度刊例（指定元旦生效），报价只解析报价日当天已生效的版本 */
    private LocalDate effectiveDate;
}
