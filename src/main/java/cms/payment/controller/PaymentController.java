package cms.payment.controller;

import cms.payment.dto.PaymentPageDetailsDto;
import cms.payment.service.PaymentService;
import cms.user.domain.User; // Spring Security의 @AuthenticationPrincipal 사용 가정
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.service.KispgPaymentService;

// DTO for confirmPayment request body
class ConfirmPaymentRequest {
    private boolean wantsLocker;
    private String pgToken; // KISPG에서 전달받는 tid 등

    public boolean isWantsLocker() { return wantsLocker; }
    public void setWantsLocker(boolean wantsLocker) { this.wantsLocker = wantsLocker; }
    public String getPgToken() { return pgToken; }
    public void setPgToken(String pgToken) { this.pgToken = pgToken; }
}

@Tag(name = "Payment API", description = "결제 관련 API (KISPG 연동 전용)")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final KispgPaymentService kispgPaymentService; // KispgPaymentService 주입

    @Operation(summary = "KISPG 결제 페이지 상세 정보 조회",
               description = "enrollId로 KISPG 결제 페이지에 필요한 CMS 내부 상세 정보(강습명, 금액, 라커 옵션, 사용자 성별, 결제 만료 시각 등)를 조회합니다.")
    @GetMapping("/details/{enrollId}")
    public ResponseEntity<PaymentPageDetailsDto> getPaymentPageDetails(
            @Parameter(description = "수강 신청 ID", required = true) @PathVariable Long enrollId,
            @AuthenticationPrincipal User currentUser) { // Spring Security를 통해 현재 사용자 정보 주입
        // currentUser가 null일 경우 또는 권한 없을 시 PaymentServiceImpl에서 ACCESS_DENIED 예외 처리
        PaymentPageDetailsDto detailsDto = paymentService.getPaymentPageDetails(enrollId, currentUser);
        return ResponseEntity.ok(detailsDto);
    }

    @Operation(summary = "KISPG 결제 초기화 파라미터 조회",
               description = "enrollId로 KISPG 결제창 호출에 필요한 파라미터를 조회합니다. (해시 포함)")
    @GetMapping("/kispg-init-params/{enrollId}")
    public ResponseEntity<KispgInitParamsDto> getKispgInitParams(
            @Parameter(description = "수강 신청 ID", required = true) @PathVariable Long enrollId,
            @AuthenticationPrincipal User currentUser) {
        KispgInitParamsDto initParamsDto = kispgPaymentService.generateInitParams(enrollId, currentUser);
        return ResponseEntity.ok(initParamsDto);
    }
    
    @Operation(summary = "KISPG 결제 후 최종 확인 및 사물함 선택 반영",
               description = "KISPG 결제 후 사용자가 돌아왔을 때 호출됩니다. 사용자의 최종 사물함 선택 여부를 반영하고, UX를 관리합니다. 실제 결제 확정은 Webhook을 통합니다.")
    @PostMapping("/confirm/{enrollId}")
    public ResponseEntity<Void> confirmPayment(
            @Parameter(description = "수강 신청 ID", required = true) @PathVariable Long enrollId,
            @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal User currentUser) {
        paymentService.confirmPayment(enrollId, currentUser, request.isWantsLocker(), request.getPgToken());
        return ResponseEntity.ok().build();
    }
} 