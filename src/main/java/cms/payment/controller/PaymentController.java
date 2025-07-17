package cms.payment.controller;

import cms.common.dto.ApiResponseSchema;
import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.service.KispgPaymentService;
import cms.user.domain.User;
import cms.swimming.dto.EnrollRequestDto;
import cms.mypage.dto.EnrollDto;
import io.swagger.v3.oas.annotations.Operation;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cms.kispg.dto.PaymentApprovalRequestDto;
import cms.common.exception.BusinessRuleException;
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import cms.kispg.dto.KispgPaymentResultDto;
import cms.common.util.IpUtil;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제 관련 API")
public class PaymentController {

    private final KispgPaymentService kispgPaymentService;
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @GetMapping("/kispg-init-params/{enrollId}")
    @Operation(summary = "KISPG 결제 초기화 파라미터 조회 (DEPRECATED)", description = "등록된 수강신청에 대한 KISPG 결제 파라미터를 조회합니다. 새로운 플로우에서는 prepare-kispg-payment를 사용하세요.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 파라미터 조회 성공"),
            @ApiResponse(responseCode = "404", description = "수강신청 정보를 찾을 수 없음")
    })
    @Deprecated
    public ResponseEntity<ApiResponseSchema<KispgInitParamsDto>> getKispgInitParams(
            @PathVariable Long enrollId,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        String userIp = IpUtil.getClientIp();
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

        String userIp = IpUtil.getClientIp();
        KispgInitParamsDto initParams = kispgPaymentService.preparePaymentWithoutEnroll(enrollRequest, currentUser,
                userIp);

        return ResponseEntity.ok(ApiResponseSchema.success(initParams, "KISPG 결제가 준비되었습니다."));
    }

    @PostMapping("/confirm")
    @Operation(summary = "결제 확인", description = "프론트엔드에서 결제 완료를 서버에 알립니다.")
    public ResponseEntity<Void> confirmPayment(@RequestBody PaymentConfirmRequest request) {
        // 결제 확인 로직 (주로 로깅이나 추가 검증 용도)
        // 실제 결제 처리는 KISPG returnUrl과 웹훅에서 처리됨
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-and-get-enrollment")
    @Operation(summary = "결제 검증 및 수강신청 조회", description = "MOID로 결제 상태를 검증하고 생성된 수강신청 정보를 조회합니다.")
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

    @GetMapping("/kispg-return")
    @Operation(summary = "KISPG 결제 완료 후 리턴 URL", description = "KISPG 인증 완료 후 자동으로 승인 요청을 처리합니다.")
    public ResponseEntity<ApiResponseSchema<String>> handleKispgReturn(
            @RequestParam String resultCd,
            @RequestParam String resultMsg,
            @RequestParam(required = false) String tid,
            @RequestParam(required = false) String ordNo,
            @RequestParam(required = false) String amt,
            @RequestParam(required = false) String payMethod,
            @RequestParam(required = false) String encData,
            @RequestParam(required = false) String mbsReserved1,
            @RequestParam(required = false) String buyerName,
            @RequestParam(required = false) String buyerTel,
            @RequestParam(required = false) String buyerEmail,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        logger.info("=== KISPG 인증 결과 수신 ===");
        logger.info("resultCd: {}", resultCd);
        logger.info("resultMsg: {}", resultMsg);
        logger.info("tid: {}", tid);
        logger.info("ordNo (moid): {}", ordNo);
        logger.info("amt: {}", amt);
        logger.info("payMethod: {}", payMethod);
        logger.info("encData: {}", encData);
        logger.info("mbsReserved1: {}", mbsReserved1);
        logger.info("buyerName: {}", buyerName);
        logger.info("buyerTel: {}", buyerTel);
        logger.info("buyerEmail: {}", buyerEmail);
        logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "null");

        try {
            // 인증 성공 시에만 승인 API 호출
            if ("0000".equals(resultCd)) {
                if (tid == null || ordNo == null || amt == null) {
                    logger.error("❌ 필수 파라미터 누락: tid={}, ordNo={}, amt={}", tid, ordNo, amt);
                    return ResponseEntity.ok(ApiResponseSchema.success("FAILED", "필수 파라미터가 누락되었습니다."));
                }

                logger.info("✅ KISPG 인증 성공 - 승인 요청 시작");
                logger.info("실제 TID로 승인 API 호출: {}", tid);

                // Construct PaymentApprovalRequestDto from KISPG return parameters
                KispgPaymentResultDto kispgResultDto = new KispgPaymentResultDto();
                kispgResultDto.setResultCd(resultCd);
                kispgResultDto.setResultMsg(resultMsg);
                kispgResultDto.setPayMethod(payMethod);
                kispgResultDto.setTid(tid);
                kispgResultDto.setOrdNo(ordNo); // KISPG's ordNo is our moid in this context
                kispgResultDto.setAmt(amt);
                kispgResultDto.setEdiDate(request.getParameter("ediDate")); // ediDate might not be a direct
                                                                            // @RequestParam
                kispgResultDto.setEncData(encData);
                kispgResultDto.setMbsReserved(mbsReserved1); // Assuming mbsReserved1 from KISPG maps to mbsReserved

                PaymentApprovalRequestDto approvalRequest = new PaymentApprovalRequestDto();
                approvalRequest.setTid(tid); // KISPG TID
                approvalRequest.setMoid(ordNo); // System MOID (from KISPG's ordNo)
                approvalRequest.setAmt(amt); // Amount
                approvalRequest.setKispgPaymentResult(kispgResultDto);

                // 실제 KISPG에서 받은 TID로 승인 요청 처리
                String userIp = IpUtil.getClientIp();
                EnrollDto enrollDto = kispgPaymentService.approvePaymentAndCreateEnrollment(approvalRequest,
                        currentUser, userIp);

                logger.info("✅ 승인 요청 완료 - EnrollID: {}", enrollDto.getEnrollId());
                return ResponseEntity.ok(ApiResponseSchema.success("SUCCESS", "결제가 성공적으로 완료되었습니다."));

            } else {
                // 인증 실패
                logger.warn("❌ KISPG 결제 인증 실패: resultCd={}, resultMsg={}", resultCd, resultMsg);
                return ResponseEntity.ok(ApiResponseSchema.success("FAILED", "결제 인증에 실패했습니다: " + resultMsg));
            }
        } catch (Exception e) {
            logger.error("❌ KISPG return 처리 중 오류: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponseSchema.success("ERROR", "결제 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/approve-and-create-enrollment")
    @Operation(summary = "KISPG 결제 승인 및 수강 등록", description = "프론트엔드에서 KISPG 결제 인증 완료 후, 실제 승인 및 수강 등록을 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "승인 및 등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 KISPG 승인 실패"),
            @ApiResponse(responseCode = "404", description = "관련 정보 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "비즈니스 규칙 위반 (예: 정원 초과)")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponseSchema<EnrollDto>> approveAndCreateEnrollment(
            @RequestBody PaymentApprovalRequestDto approvalRequest,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {

        logger.info("=== 프론트엔드 요청: KISPG 결제 승인 및 수강 등록 시작 ===");
        logger.info("TID: {}, MOID: {}, Amt: {}",
                approvalRequest.getTid(), approvalRequest.getMoid(), approvalRequest.getAmt());
        if (approvalRequest.getKispgPaymentResult() != null) {
            logger.info("KISPG 상세 결과: {}", approvalRequest.getKispgPaymentResult().toString());
        }
        logger.info("사용자: {}", currentUser.getUsername());

        try {
            String userIp = IpUtil.getClientIp();
            EnrollDto enrollDto = kispgPaymentService.approvePaymentAndCreateEnrollment(
                    approvalRequest,
                    currentUser, userIp);

            logger.info("✅ KISPG 결제 승인 및 수강 등록 성공 - EnrollID: {}", enrollDto.getEnrollId());
            return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "결제 승인 및 수강 등록이 완료되었습니다."));

        } catch (BusinessRuleException e) {
            logger.error("❌ KISPG 결제 승인 비즈니스 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                    .body(ApiResponseSchema.<EnrollDto>error(e.getErrorCode().getDefaultMessage(),
                            e.getErrorCode().getCode()));
        } catch (ResourceNotFoundException e) {
            logger.error("❌ KISPG 결제 승인 관련 리소스 없음: {}", e.getMessage(), e);
            return ResponseEntity.status(e.getErrorCode().getHttpStatus())
                    .body(ApiResponseSchema.<EnrollDto>error(e.getErrorCode().getDefaultMessage(),
                            e.getErrorCode().getCode()));
        } catch (Exception e) {
            logger.error("❌ KISPG 결제 승인 중 일반 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseSchema.<EnrollDto>error(ErrorCode.PAYMENT_FAILED.getDefaultMessage(),
                            ErrorCode.PAYMENT_FAILED.getCode()));
        }
    }

    // DTO for verifyAndGetEnrollment request body
    public static class VerifyPaymentRequest {
        private String moid;

        // Getters and setters
        public String getMoid() {
            return moid;
        }

        public void setMoid(String moid) {
            this.moid = moid;
        }
    }

    // DTO for confirmPayment request body
    public static class PaymentConfirmRequest {
        private Long enrollId;
        private boolean success;

        // Getters and setters
        public Long getEnrollId() {
            return enrollId;
        }

        public void setEnrollId(Long enrollId) {
            this.enrollId = enrollId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }
    }
}