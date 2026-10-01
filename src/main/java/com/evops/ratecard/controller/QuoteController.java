package com.evops.ratecard.controller;

import com.evops.common.ApiResponse;
import com.evops.ratecard.dto.QuoteSubmitRequest;
import com.evops.ratecard.dto.QuoteView;
import com.evops.ratecard.service.QuoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * 报价单：试算（不落库）与提交（固化快照，提交即终态），翻查与详情。
 * 计价只走 PricingEngine 一处，含税/不含税并列返回，默认看含税。
 */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** 试算：body 与提交一致，按报价日当天刊例计价，不落库。 */
    @PostMapping("/preview")
    public ApiResponse<QuoteView> preview(@Valid @RequestBody QuoteSubmitRequest request) {
        return ApiResponse.ok(quoteService.preview(request));
    }

    /** 提交报价单：逐行快照落库，刊例后调不回写。 */
    @PostMapping
    public ApiResponse<QuoteView> submit(@Valid @RequestBody QuoteSubmitRequest request) {
        return ApiResponse.ok(quoteService.submit(request));
    }

    /** 报价单翻查：?from=&to=&quoteNo=&customerName=&cardYear= 均可空。 */
    @GetMapping
    public ApiResponse<List<QuoteView>> list(@RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) String quoteNo,
                                             @RequestParam(required = false) String customerName,
                                             @RequestParam(required = false) Integer cardYear) {
        return ApiResponse.ok(quoteService.list(from, to, quoteNo, customerName, cardYear));
    }

    /** 报价单详情：头 + 逐行计价快照。 */
    @GetMapping("/{id}")
    public ApiResponse<QuoteView> detail(@PathVariable Long id) {
        return ApiResponse.ok(quoteService.detail(id));
    }
}
