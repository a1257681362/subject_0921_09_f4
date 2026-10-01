package com.evops.ratecard.controller;

import com.evops.common.ApiResponse;
import com.evops.ratecard.dto.CardCreateRequest;
import com.evops.ratecard.dto.CardView;
import com.evops.ratecard.dto.DraftVersionRequest;
import com.evops.ratecard.dto.FinanceTierView;
import com.evops.ratecard.dto.PriceLineSaveRequest;
import com.evops.ratecard.dto.PublishRequest;
import com.evops.ratecard.dto.TierSaveRequest;
import com.evops.ratecard.dto.VersionDetailView;
import com.evops.ratecard.entity.RateCard;
import com.evops.ratecard.entity.RateCardVersion;
import com.evops.ratecard.service.FinanceTierService;
import com.evops.ratecard.service.RateCardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** 年度刊例：建档/版本/单价行/阶梯/发布，以及财务阶梯对照表查询与 CSV 导出。 */
@RestController
@RequestMapping("/api/rate-cards")
public class RateCardController {

    private final RateCardService rateCardService;
    private final FinanceTierService financeTierService;

    public RateCardController(RateCardService rateCardService, FinanceTierService financeTierService) {
        this.rateCardService = rateCardService;
        this.financeTierService = financeTierService;
    }

    /** 建年度刊例：一年一张，建档同时生成 v1 草稿。 */
    @PostMapping
    public ApiResponse<RateCard> create(@Valid @RequestBody CardCreateRequest request) {
        return ApiResponse.ok(rateCardService.createCard(request));
    }

    /** 刊例翻查（?year= 可选），年度倒序。 */
    @GetMapping
    public ApiResponse<List<RateCard>> list(@RequestParam(required = false) Integer year) {
        return ApiResponse.ok(rateCardService.listCards(year));
    }

    /** 刊例详情（含全部版本）。 */
    @GetMapping("/{cardId}")
    public ApiResponse<CardView> detail(@PathVariable Long cardId) {
        return ApiResponse.ok(rateCardService.getCard(cardId));
    }

    /** 基于当前生效版本开修订草稿（复制单价行、阶梯、税率）。 */
    @PostMapping("/{cardId}/versions")
    public ApiResponse<RateCardVersion> createDraft(@PathVariable Long cardId,
                                                    @RequestBody(required = false) DraftVersionRequest request) {
        return ApiResponse.ok(rateCardService.createDraftVersion(cardId,
                request == null ? new DraftVersionRequest() : request));
    }

    /** 版本详情：单价行 + 阶梯行。 */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<VersionDetailView> versionDetail(@PathVariable Long versionId) {
        return ApiResponse.ok(rateCardService.getVersionDetail(versionId));
    }

    /** 草稿改税率。 */
    @PutMapping("/versions/{versionId}/tax-rate")
    public ApiResponse<RateCardVersion> updateTaxRate(@PathVariable Long versionId,
                                                      @Valid @RequestBody DraftVersionRequest request) {
        return ApiResponse.ok(rateCardService.updateDraftTaxRate(versionId, request));
    }

    /** 草稿态整表保存点位挂牌单价（全量替换）。 */
    @PutMapping("/versions/{versionId}/lines")
    public ApiResponse<Void> saveLines(@PathVariable Long versionId,
                                       @Valid @RequestBody PriceLineSaveRequest request) {
        rateCardService.saveLines(versionId, request);
        return ApiResponse.ok(null);
    }

    /** 草稿态整表保存周数阶梯（全量替换，重叠/断档/不单调直接拒收）。 */
    @PutMapping("/versions/{versionId}/tiers")
    public ApiResponse<Void> saveTiers(@PathVariable Long versionId,
                                       @Valid @RequestBody TierSaveRequest request) {
        rateCardService.saveTiers(versionId, request);
        return ApiResponse.ok(null);
    }

    /** 发布版本：冻结行与阶梯；可指定未来生效日（待生效，到日自动激活），留空立即生效。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<RateCardVersion> publish(@PathVariable Long versionId,
                                                @RequestBody(required = false) PublishRequest request) {
        if (request == null) {
            return ApiResponse.ok(rateCardService.publish(versionId, null, null));
        }
        return ApiResponse.ok(rateCardService.publish(versionId, request.getOperatorName(),
                request.getEffectiveDate()));
    }

    /** 财务阶梯对照表：?date=&siteId=（均可空，日期默认今天；siteId 非空时附带各档示例单价）。 */
    @GetMapping("/finance/tiers")
    public ApiResponse<FinanceTierView> financeTiers(@RequestParam(required = false) LocalDate date,
                                                     @RequestParam(required = false) Long siteId) {
        return ApiResponse.ok(financeTierService.tierTable(date, siteId));
    }

    /** 财务阶梯对照表 CSV 导出（UTF-8 BOM，Excel 打开不乱码）。 */
    @GetMapping("/finance/tiers/export")
    public ResponseEntity<byte[]> exportFinanceTiers(@RequestParam(required = false) LocalDate date,
                                                     @RequestParam(required = false) Long siteId) {
        byte[] csv = financeTierService.exportCsv(date, siteId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rate-card-tiers.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
