package com.evops.ratecard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.common.BusinessException;
import com.evops.ledger.support.Operators;
import com.evops.ratecard.RateCardEnums;
import com.evops.ratecard.dto.QuoteLineView;
import com.evops.ratecard.dto.QuoteSubmitRequest;
import com.evops.ratecard.dto.QuoteView;
import com.evops.ratecard.entity.Quote;
import com.evops.ratecard.entity.QuoteLine;
import com.evops.ratecard.mapper.QuoteLineMapper;
import com.evops.ratecard.mapper.QuoteMapper;
import com.evops.ratecard.support.QuotePriceResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价单：提交即终态（只提交+翻查，无改/删/撤）。
 * 每行走唯一定价入口 PricingEngine，返回什么快照就存什么；以后刊例调价只影响新报价，旧单一个字节都不回写。
 */
@Service
public class QuoteService {

    private final QuoteMapper quoteMapper;
    private final QuoteLineMapper quoteLineMapper;
    private final PricingEngine pricingEngine;

    public QuoteService(QuoteMapper quoteMapper, QuoteLineMapper quoteLineMapper, PricingEngine pricingEngine) {
        this.quoteMapper = quoteMapper;
        this.quoteLineMapper = quoteLineMapper;
        this.pricingEngine = pricingEngine;
    }

    /** 试算：与提交完全同口径，但不落库。 */
    public QuoteView preview(QuoteSubmitRequest request) {
        return assemble(request, false, null);
    }

    /** 提交报价单：逐行计价、固化快照；返回含报价单号的完整视图。 */
    @Transactional(rollbackFor = Exception.class)
    public QuoteView submit(QuoteSubmitRequest request) {
        String operator = Operators.resolve(request.getOperatorName());
        QuoteView view = assemble(request, true, operator);

        Quote quote = new Quote();
        quote.setQuoteNo(view.getQuoteNo());
        quote.setQuoteDate(view.getQuoteDate());
        quote.setCustomerName(view.getCustomerName());
        quote.setCardId(view.getCardId());
        quote.setCardNo(view.getCardNo());
        quote.setCardYear(view.getCardYear());
        quote.setVersionId(view.getVersionId());
        quote.setVersionNo(view.getVersionNo());
        quote.setVersionEffectiveTime(view.getVersionEffectiveTime());
        quote.setTaxRate(view.getTaxRate());
        quote.setTotalWeeks(view.getTotalWeeks());
        quote.setNetAmount(view.getNetAmount());
        quote.setTaxAmount(view.getTaxAmount());
        quote.setGrossAmount(view.getGrossAmount());
        quote.setStatus(RateCardEnums.QUOTE_SUBMITTED);
        quote.setOperatorName(operator);
        quote.setRemark(request.getRemark());
        try {
            quoteMapper.insert(quote);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("报价单号冲突，请重试");
        }

        int lineNo = 1;
        for (QuotePriceResult result : view.getResults()) {
            QuoteLine line = new QuoteLine();
            line.setQuoteId(quote.getId());
            line.setLineNo(lineNo++);
            line.setSiteId(result.getSiteId());
            line.setSiteNo(result.getSiteNo());
            line.setWeeks(result.getWeeks());
            line.setListNetUnit(result.getListNetUnit());
            line.setTierId(result.getTierId());
            line.setTierNo(result.getTierNo());
            line.setMinWeeks(result.getMinWeeks());
            line.setMaxWeeks(result.getMaxWeeks());
            line.setDiscountRate(result.getDiscountRate());
            line.setNetUnit(result.getNetUnit());
            line.setNetAmount(result.getNetAmount());
            line.setTaxAmount(result.getTaxAmount());
            line.setGrossUnit(result.getGrossUnit());
            line.setGrossAmount(result.getGrossAmount());
            quoteLineMapper.insert(line);
        }
        return detail(quote.getId());
    }

    /** 报价单翻查（日期区间/单号/客户/年度，可选），按 id 倒序，明细请查详情。 */
    public List<QuoteView> list(LocalDate from, LocalDate to, String quoteNo, String customerName, Integer cardYear) {
        LambdaQueryWrapper<Quote> wrapper = new LambdaQueryWrapper<>();
        if (from != null) {
            wrapper.ge(Quote::getQuoteDate, from);
        }
        if (to != null) {
            wrapper.le(Quote::getQuoteDate, to);
        }
        if (quoteNo != null && !quoteNo.trim().isEmpty()) {
            wrapper.like(Quote::getQuoteNo, quoteNo.trim());
        }
        if (customerName != null && !customerName.trim().isEmpty()) {
            wrapper.like(Quote::getCustomerName, customerName.trim());
        }
        if (cardYear != null) {
            wrapper.eq(Quote::getCardYear, cardYear);
        }
        wrapper.orderByDesc(Quote::getId);
        return quoteMapper.selectList(wrapper).stream().map(q -> toView(q, null)).collect(Collectors.toList());
    }

    /** 报价单详情：头 + 逐行快照。 */
    public QuoteView detail(Long id) {
        Quote quote = quoteMapper.selectById(id);
        if (quote == null) {
            throw new BusinessException("报价单不存在: id=" + id);
        }
        List<QuoteLine> lines = quoteLineMapper.selectList(new LambdaQueryWrapper<QuoteLine>()
                .eq(QuoteLine::getQuoteId, id)
                .orderByAsc(QuoteLine::getLineNo));
        return toView(quote, lines);
    }

    /** 逐行走计价引擎并汇总；persist=true 时取报价单号。 */
    private QuoteView assemble(QuoteSubmitRequest request, boolean persist, String operator) {
        LocalDate quoteDate = request.getQuoteDate() == null ? LocalDate.now() : request.getQuoteDate();
        if (quoteDate.isAfter(LocalDate.now())) {
            throw new BusinessException("报价日期不能晚于今天（" + LocalDate.now() + "），不能按未来刊例预报价：" + quoteDate);
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException("报价至少包含一行点位/周数");
        }
        Set<Long> seenSites = new HashSet<>();
        List<QuotePriceResult> results = new ArrayList<>();
        for (QuoteSubmitRequest.Line line : request.getLines()) {
            if (!seenSites.add(line.getSiteId())) {
                throw new BusinessException("同一报价单内点位 id=" + line.getSiteId()
                        + " 出现多行，请合并为一行周数");
            }
            results.add(pricingEngine.price(line.getSiteId(), line.getWeeks(), quoteDate));
        }

        int totalWeeks = results.stream().mapToInt(QuotePriceResult::getWeeks).sum();
        BigDecimal net = results.stream().map(QuotePriceResult::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = results.stream().map(QuotePriceResult::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = results.stream().map(QuotePriceResult::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 同一报价日所有行必命中同一年同一版本，刊例头快照取首行
        QuotePriceResult first = results.get(0);
        QuoteView view = new QuoteView();
        if (persist) {
            view.setQuoteNo(String.format("QT%010d", quoteMapper.nextQuoteSeq()));
        }
        view.setQuoteDate(quoteDate);
        view.setCustomerName(request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()
                ? null : request.getCustomerName().trim());
        view.setCardId(first.getCardId());
        view.setCardNo(first.getCardNo());
        view.setCardYear(first.getCardYear());
        view.setVersionId(first.getVersionId());
        view.setVersionNo(first.getVersionNo());
        view.setVersionEffectiveTime(first.getVersionEffectiveTime());
        view.setTaxRate(first.getTaxRate());
        view.setTotalWeeks(totalWeeks);
        view.setNetAmount(net);
        view.setTaxAmount(tax);
        view.setGrossAmount(gross);
        view.setStatus(persist ? RateCardEnums.QUOTE_SUBMITTED : null);
        view.setOperatorName(operator);
        view.setRemark(request.getRemark());
        view.setResults(results);
        view.setLines(results.stream().map(this::toLineView).collect(Collectors.toList()));
        return view;
    }

    private QuoteLineView toLineView(QuotePriceResult r) {
        QuoteLineView v = new QuoteLineView();
        v.setSiteId(r.getSiteId());
        v.setSiteNo(r.getSiteNo());
        v.setWeeks(r.getWeeks());
        v.setListNetUnit(r.getListNetUnit());
        v.setTierId(r.getTierId());
        v.setTierNo(r.getTierNo());
        v.setMinWeeks(r.getMinWeeks());
        v.setMaxWeeks(r.getMaxWeeks());
        v.setTierRangeText(tierRangeText(r.getMinWeeks(), r.getMaxWeeks()));
        v.setDiscountRate(r.getDiscountRate());
        v.setNetUnit(r.getNetUnit());
        v.setNetAmount(r.getNetAmount());
        v.setTaxAmount(r.getTaxAmount());
        v.setGrossUnit(r.getGrossUnit());
        v.setGrossAmount(r.getGrossAmount());
        return v;
    }

    private QuoteView toView(Quote quote, List<QuoteLine> lines) {
        QuoteView view = new QuoteView();
        view.setId(quote.getId());
        view.setQuoteNo(quote.getQuoteNo());
        view.setQuoteDate(quote.getQuoteDate());
        view.setCustomerName(quote.getCustomerName());
        view.setCardId(quote.getCardId());
        view.setCardNo(quote.getCardNo());
        view.setCardYear(quote.getCardYear());
        view.setVersionId(quote.getVersionId());
        view.setVersionNo(quote.getVersionNo());
        view.setVersionEffectiveTime(quote.getVersionEffectiveTime());
        view.setTaxRate(quote.getTaxRate());
        view.setTotalWeeks(quote.getTotalWeeks());
        view.setNetAmount(quote.getNetAmount());
        view.setTaxAmount(quote.getTaxAmount());
        view.setGrossAmount(quote.getGrossAmount());
        view.setStatus(quote.getStatus());
        view.setOperatorName(quote.getOperatorName());
        view.setRemark(quote.getRemark());
        if (lines != null) {
            view.setLines(lines.stream().map(l -> {
                QuoteLineView v = new QuoteLineView();
                v.setId(l.getId());
                v.setLineNo(l.getLineNo());
                v.setSiteId(l.getSiteId());
                v.setSiteNo(l.getSiteNo());
                v.setWeeks(l.getWeeks());
                v.setListNetUnit(l.getListNetUnit());
                v.setTierId(l.getTierId());
                v.setTierNo(l.getTierNo());
                v.setMinWeeks(l.getMinWeeks());
                v.setMaxWeeks(l.getMaxWeeks());
                v.setTierRangeText(tierRangeText(l.getMinWeeks(), l.getMaxWeeks()));
                v.setDiscountRate(l.getDiscountRate());
                v.setNetUnit(l.getNetUnit());
                v.setNetAmount(l.getNetAmount());
                v.setTaxAmount(l.getTaxAmount());
                v.setGrossUnit(l.getGrossUnit());
                v.setGrossAmount(l.getGrossAmount());
                return v;
            }).collect(Collectors.toList()));
        }
        return view;
    }

    /** 档位展示串：1-3 周、8 周起。 */
    private String tierRangeText(Integer minWeeks, Integer maxWeeks) {
        if (maxWeeks == null) {
            return minWeeks + " 周起";
        }
        if (minWeeks.equals(maxWeeks)) {
            return minWeeks + " 周";
        }
        return minWeeks + "-" + maxWeeks + " 周";
    }
}
