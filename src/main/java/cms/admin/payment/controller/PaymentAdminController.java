package cms.admin.payment.controller;

import cms.admin.payment.dto.PaymentAdminDto;
import cms.admin.payment.service.PaymentAdminService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import cms.payment.domain.PaymentStatus;
import cms.admin.payment.dto.KispgQueryRequestDto;
import cms.kispg.service.KispgPaymentService;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.Map;

@Tag(name = "CMS - Payment Management", description = "결제 및 환불 내역 관리 API (관리자용)")
@RestController
@RequestMapping("/cms/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class PaymentAdminController {

    private final PaymentAdminService paymentAdminService;
    private final KispgPaymentService kispgPaymentService;

    @Operation(summary = "모든 결제/환불 내역 조회", description = "다양한 필터와 페이징을 적용하여 결제 및 환불 내역을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<PaymentAdminDto>>> getAllPayments(
            @Parameter(description = "강습 ID") @RequestParam(required = false) Long lessonId,
            @Parameter(description = "신청 ID") @RequestParam(required = false) Long enrollId,
            @Parameter(description = "사용자 UUID") @RequestParam(required = false) String userId,
            @Parameter(description = "KISPG 거래 ID (TID)") @RequestParam(required = false) String tid,
            @Parameter(description = "조회 시작일 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "결제 상태 (PAID, FAILED, CANCELED, PARTIAL_REFUNDED, REFUND_REQUESTED)") @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 10, sort = "paidAt,desc") Pageable pageable) {
        Page<PaymentAdminDto> payments = paymentAdminService.getAllPayments(lessonId, enrollId, userId, tid, startDate,
                endDate, status, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(payments, "결제 내역 조회 성공"));
    }

    @Operation(summary = "특정 결제 상세 조회", description = "결제 ID로 특정 결제의 상세 정보를 조회합니다.")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponseSchema<PaymentAdminDto>> getPaymentById(
            @Parameter(description = "결제 ID") @PathVariable Long paymentId) {
        PaymentAdminDto paymentDto = paymentAdminService.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResponseSchema.success(paymentDto, "결제 상세 조회 성공"));
    }

    // Using a Map for request body to be flexible for manual refund parameters
    @Operation(summary = "수동 환불 처리", description = "특정 결제에 대해 수동으로 환불을 기록합니다. (주의: KISPG 연동 없이 DB만 업데이트)")
    @PostMapping("/{paymentId}/manual-refund")
    public ResponseEntity<ApiResponseSchema<PaymentAdminDto>> manualRefund(
            @Parameter(description = "결제 ID") @PathVariable Long paymentId,
            @Valid @RequestBody Map<String, Object> payload) {
        // Basic validation, more specific DTO could be used for better validation
        Integer amount = (Integer) payload.get("amount");
        String reason = (String) payload.get("reason");
        String adminNote = (String) payload.getOrDefault("adminNote", "");

        if (amount == null || reason == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseSchema.error("환불 금액과 사유는 필수입니다.", "INVALID_MANUAL_REFUND_REQUEST"));
        }

        PaymentAdminDto updatedPayment = paymentAdminService.manualRefund(paymentId, amount, reason, adminNote);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedPayment, "수동 환불 처리 성공"));
    }

    @Operation(summary = "PG사 결제 내역 직접 조회", description = "PG사에 특정 거래의 상세 내역을 직접 조회하여 DB와 대사합니다.")
    @PostMapping("/query-pg")
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> queryPaymentFromPg(
            @Valid @RequestBody KispgQueryRequestDto requestDto) {
        Map<String, Object> pgTransactionDetails = kispgPaymentService.queryTransactionAtPg(requestDto);
        return ResponseEntity.ok(ApiResponseSchema.success(pgTransactionDetails, "PG사 결제 내역 조회 성공"));
    }
}