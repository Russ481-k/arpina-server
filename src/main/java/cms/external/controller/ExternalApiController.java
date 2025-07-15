package cms.external.controller;

import cms.external.dto.PaymentDataResponse;
import cms.external.service.ExternalApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "External API", description = "외부 업체 연동 API")
@RestController
@RequestMapping("/external")
@RequiredArgsConstructor
public class ExternalApiController {

    private final ExternalApiService externalApiService;

    @GetMapping("/payment-data")
    @Operation(summary = "결제 데이터 조회", description = "지정된 기간 내의 결제, 신청, 사용자 데이터를 조회합니다.")
    public ResponseEntity<PaymentDataResponse> getPaymentData(
            @Parameter(description = "조회 시작일시 (YYYY-MM-DD'T'HH:mm:ss)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "조회 종료일시 (YYYY-MM-DD'T'HH:mm:ss)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        PaymentDataResponse response = externalApiService.getPaymentDataByPeriod(startDate, endDate);
        return ResponseEntity.ok(response);
    }
}