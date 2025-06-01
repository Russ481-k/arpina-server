package cms.payment.controller;

import cms.common.dto.ApiResponseSchema;
import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.service.KispgPaymentService;
import cms.user.domain.User;
import cms.swimming.dto.EnrollRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 관련 API")
public class PaymentController {

    private final KispgPaymentService kispgPaymentService;

    @GetMapping("/kispg-init-params/{enrollId}")
    @Operation(summary = "KISPG 결제 초기화 파라미터 조회 (DEPRECATED)", 
               description = "등록된 수강신청에 대한 KISPG 결제 파라미터를 조회합니다. 새로운 플로우에서는 prepare-kispg-payment를 사용하세요.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "결제 파라미터 조회 성공"),
        @ApiResponse(responseCode = "404", description = "수강신청 정보를 찾을 수 없음")
    })
    @Deprecated
    public ResponseEntity<ApiResponseSchema<KispgInitParamsDto>> getKispgInitParams(
            @PathVariable Long enrollId,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        
        String userIp = getClientIp(request);
        KispgInitParamsDto initParams = kispgPaymentService.generateInitParams(enrollId, currentUser, userIp);
        
        return ResponseEntity.ok(ApiResponseSchema.success(initParams, "KISPG 결제 파라미터가 성공적으로 생성되었습니다."));
    }

    @PostMapping("/prepare-kispg-payment")
    @Operation(summary = "KISPG 결제 준비", description = "수강신청 정보를 바탕으로 KISPG 결제 파라미터를 생성합니다. 실제 등록은 결제 완료 후 이루어집니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "결제 준비 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "409", description = "정원 초과 등 비즈니스 규칙 위반")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponseSchema<KispgInitParamsDto>> prepareKispgPayment(
            @Valid @RequestBody EnrollRequestDto enrollRequest,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        
        String userIp = getClientIp(request);
        KispgInitParamsDto initParams = kispgPaymentService.preparePaymentWithoutEnroll(enrollRequest, currentUser, userIp);
        
        return ResponseEntity.ok(ApiResponseSchema.success(initParams, "KISPG 결제가 준비되었습니다."));
    }

    @PostMapping("/confirm")
    @Operation(summary = "결제 확인", description = "프론트엔드에서 결제 완료를 서버에 알립니다.")
    public ResponseEntity<Void> confirmPayment(@RequestBody PaymentConfirmRequest request) {
        // 결제 확인 로직 (주로 로깅이나 추가 검증 용도)
        // 실제 결제 처리는 KISPG 웹훅에서 처리됨
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-and-get-enrollment")
    @Operation(summary = "결제 검증 및 수강신청 조회", 
               description = "MOID로 결제 상태를 검증하고 생성된 수강신청 정보를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "결제 검증 및 수강신청 조회 성공"),
        @ApiResponse(responseCode = "404", description = "결제 정보 또는 수강신청 정보를 찾을 수 없음"),
        @ApiResponse(responseCode = "400", description = "결제가 완료되지 않았거나 유효하지 않은 상태")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponseSchema<cms.mypage.dto.EnrollDto>> verifyAndGetEnrollment(
            @RequestBody VerifyPaymentRequest request,
            @AuthenticationPrincipal User currentUser) {
        
        cms.mypage.dto.EnrollDto enrollDto = kispgPaymentService.verifyAndGetEnrollment(request.getMoid(), currentUser);
        
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "결제 검증 및 수강신청 조회가 완료되었습니다."));
    }

    @PostMapping("/approve-and-create-enrollment")
    @Operation(summary = "KISPG 결제 승인 및 수강신청 생성", 
               description = "KISPG 승인 API를 호출하고 temp moid로부터 수강신청을 생성합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "승인 및 수강신청 생성 성공"),
        @ApiResponse(responseCode = "400", description = "승인 실패 또는 잘못된 요청"),
        @ApiResponse(responseCode = "409", description = "이미 처리된 결제 또는 비즈니스 규칙 위반")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponseSchema<cms.mypage.dto.EnrollDto>> approveAndCreateEnrollment(
            @RequestBody ApprovePaymentRequest request,
            @AuthenticationPrincipal User currentUser) {
        
        cms.mypage.dto.EnrollDto enrollDto = kispgPaymentService.approvePaymentAndCreateEnrollment(
            request.getTid(), request.getMoid(), request.getAmt(), currentUser);
        
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "결제 승인 및 수강신청이 완료되었습니다."));
    }

    // DTO for verifyAndGetEnrollment request body
    public static class VerifyPaymentRequest {
        private String moid;
        
        // Getters and setters
        public String getMoid() { return moid; }
        public void setMoid(String moid) { this.moid = moid; }
    }

    // DTO for confirmPayment request body
    public static class PaymentConfirmRequest {
        private Long enrollId;
        private boolean success;
        
        // Getters and setters
        public Long getEnrollId() { return enrollId; }
        public void setEnrollId(Long enrollId) { this.enrollId = enrollId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // DTO for approveAndCreateEnrollment request body
    public static class ApprovePaymentRequest {
        private String tid;
        private String moid;
        private String amt;
        
        // Getters and setters
        public String getTid() { return tid; }
        public void setTid(String tid) { this.tid = tid; }
        public String getMoid() { return moid; }
        public void setMoid(String moid) { this.moid = moid; }
        public String getAmt() { return amt; }
        public void setAmt(String amt) { this.amt = amt; }
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }
} 