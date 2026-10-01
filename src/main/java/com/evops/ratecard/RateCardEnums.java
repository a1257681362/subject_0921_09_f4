package com.evops.ratecard;

import java.math.BigDecimal;

/**
 * 年度刊例口径常量：刊例头 / 版本 / 报价单的状态与来源都用字符串码落库（与底账、素材模块一致，不用 DB 枚举）。
 */
public final class RateCardEnums {

    private RateCardEnums() {}

    /** 刊例头状态：DRAFT 尚无生效版本（草稿年）/ EFFECTIVE 已有生效版本 */
    public static final String CARD_DRAFT = "DRAFT";
    public static final String CARD_EFFECTIVE = "EFFECTIVE";

    /**
     * 版本状态：DRAFT 草稿（可改）/ PENDING 已发布待生效（指定了未来生效日）/
     * EFFECTIVE 生效中 / SUPERSEDED 已被新版取代（终态，历史不抹）
     */
    public static final String VERSION_DRAFT = "DRAFT";
    public static final String VERSION_PENDING = "PENDING";
    public static final String VERSION_EFFECTIVE = "EFFECTIVE";
    public static final String VERSION_SUPERSEDED = "SUPERSEDED";

    /** 版本来源：INITIAL 首版 / REVISE 修订版 */
    public static final String VERSION_SOURCE_INITIAL = "INITIAL";
    public static final String VERSION_SOURCE_REVISE = "REVISE";

    /** 报价单状态：SUBMITTED 已提交（唯一终态，只提交+翻查，不可改/删/撤） */
    public static final String QUOTE_SUBMITTED = "SUBMITTED";

    /** 广告服务增值税率默认 6%（分数），全模块唯一默认值出口 */
    public static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.0600");
}
