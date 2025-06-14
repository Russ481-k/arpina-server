package cms.nice.controller;

import cms.nice.dto.NiceInitiateResponseDto;
import cms.nice.dto.NicePublicUserDataDto;
import cms.nice.service.NiceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

// DTO for request body
import cms.nice.dto.NiceInitiateRequestDto;
import cms.nice.dto.NiceCallbackResultDto;
import cms.nice.dto.NiceErrorDataDto;
import cms.nice.dto.NiceUserDataDto;

@RestController
@RequestMapping("/nice/checkplus") // API 경로를 /api/v1 하위로 변경 고려
public class NiceController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NiceController.class);

    private final NiceService niceService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${nice.checkplus.frontend-redirect-success-path}")
    private String frontendSuccessPath;

    @Value("${nice.checkplus.frontend-redirect-fail-path}")
    private String frontendFailPath;

    private String frontendRedirectSuccessUrl;
    private String frontendRedirectFailUrl;

    // tempReqSeqStore and tempResultStore are now managed by NiceService

    public NiceController(NiceService niceService) {
        this.niceService = niceService;
    }

    @PostConstruct
    private void initializeUrls() {
        // allowedOrigins가 http://localhost:3000과 같이 /로 끝나지 않는다고 가정
        // frontendSuccessPath, frontendFailPath가 /signup-result 와 같이 /로 시작한다고 가정
        this.frontendRedirectSuccessUrl = allowedOrigins + frontendSuccessPath;
        this.frontendRedirectFailUrl = allowedOrigins + frontendFailPath;
        log.info("NICE Success Redirect URL initialized to: {}", frontendRedirectSuccessUrl);
        log.info("NICE Fail Redirect URL initialized to: {}", frontendRedirectFailUrl);
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateVerification(@RequestBody NiceInitiateRequestDto requestDto) {
        try {
            if (requestDto == null || requestDto.getServiceType() == null || requestDto.getServiceType().isEmpty()) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "serviceType is required."));
            }
            // Validate serviceType (optional, can be done in service layer too)
            String serviceType = requestDto.getServiceType().toUpperCase();
            if (!serviceType.equals("REGISTER") && !serviceType.equals("FIND_ID")
                    && !serviceType.equals("RESET_PASSWORD")) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error",
                        "Invalid serviceType. Allowed values: REGISTER, FIND_ID, RESET_PASSWORD"));
            }

            Map<String, String> initData = niceService.initiateVerification(serviceType);
            // reqSeq is now stored and managed by NiceService
            NiceInitiateResponseDto responseDto = new NiceInitiateResponseDto(initData.get("encodeData"),
                    initData.get("reqSeq"));
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            log.error("Error initiating NICE verification for serviceType: {}",
                    requestDto != null ? requestDto.getServiceType() : "null", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "본인인증 초기화 실패: " + e.getMessage()));
        }
    }

    @RequestMapping(value = "/success", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<Void> successCallback(@RequestParam("EncodeData") String encodeData) {
        String reqSeqFromNice = null;
        String resultKey = null;
        try {
            reqSeqFromNice = niceService.getReqSeqFromEncodedData(encodeData);
            resultKey = niceService.storeSuccessData(encodeData);

            Object rawResult = niceService.peekRawNiceData(resultKey);
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(frontendRedirectSuccessUrl);
            urlBuilder.queryParam("key", resultKey);

            if (rawResult instanceof NiceCallbackResultDto) {
                NiceCallbackResultDto callbackResult = (NiceCallbackResultDto) rawResult;
                urlBuilder.queryParam("status", callbackResult.getStatus());
                urlBuilder.queryParam("serviceType", callbackResult.getServiceType());
                if (callbackResult.getMessage() != null) {
                    urlBuilder.queryParam("message", callbackResult.getMessage());
                }
                if (callbackResult.getErrorCode() != null) {
                    urlBuilder.queryParam("errorCode", callbackResult.getErrorCode());
                }
                if (callbackResult.getUserEmail() != null) {
                    urlBuilder.queryParam("email", callbackResult.getUserEmail());
                }
                if (callbackResult.getIdentifiedName() != null) {
                    urlBuilder.queryParam("name", callbackResult.getIdentifiedName());
                }

                if ("REGISTER".equals(callbackResult.getServiceType()) && callbackResult.getUserData() != null) {
                    NiceUserDataDto userData = callbackResult.getUserData();
                    urlBuilder.queryParam("joined", String.valueOf(userData.isAlreadyJoined()));
                    if (userData.isAlreadyJoined() && userData.getExistingUsername() != null) {
                        urlBuilder.queryParam("username", userData.getExistingUsername());
                        log.info("[NICE] REGISTER - User with DI already joined. Username: {}",
                                userData.getExistingUsername());
                    } else {
                        log.info("[NICE] REGISTER - New user identified.");
                    }
                }
                log.info(
                        "[NICE] Success callback processed. Redirecting with params from NiceCallbackResultDto. ServiceType: {}, Status: {}",
                        callbackResult.getServiceType(), callbackResult.getStatus());

            } else {
                log.warn(
                        "[NICE] Success callback rawResult is not NiceCallbackResultDto. Type: {}. Redirecting with generic success.",
                        rawResult != null ? rawResult.getClass().getName() : "null");
                urlBuilder.queryParam("status", "success");
            }

            String redirectUrl = urlBuilder.toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("Exception in NICE successCallback. Initial reqSeqFromNice: {}", reqSeqFromNice, e);
            String errorRedirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectFailUrl)
                    .queryParam("status", "fail")
                    .queryParam("error", "processing_failed")
                    .queryParam("detail",
                            e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage()
                                    : e.getClass().getSimpleName())
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(errorRedirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    @RequestMapping(value = "/fail", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<Void> failCallback(@RequestParam("EncodeData") String encodeData) {
        String reqSeqFromNice = null;
        String resultKey = null;
        try {
            reqSeqFromNice = niceService.getReqSeqFromEncodedData(encodeData);
            resultKey = niceService.storeErrorData(encodeData);

            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(frontendRedirectFailUrl)
                    .queryParam("key", resultKey);

            Object rawError = niceService.peekRawNiceData(resultKey);
            if (rawError instanceof NiceErrorDataDto) {
                NiceErrorDataDto errorData = (NiceErrorDataDto) rawError;
                urlBuilder.queryParam("status", "fail");
                urlBuilder.queryParam("errorCode", errorData.getErrorCode());
                urlBuilder.queryParam("message", errorData.getMessage());
                if (errorData.getServiceType() != null) {
                    urlBuilder.queryParam("serviceType", errorData.getServiceType());
                }
                log.info(
                        "[NICE] Fail callback processed. Redirecting with params from NiceErrorDataDto. ErrorCode: {}, ServiceType: {}",
                        errorData.getErrorCode(), errorData.getServiceType());
            } else {
                log.warn(
                        "[NICE] Fail callback rawError is not NiceErrorDataDto. Type: {}. Redirecting with generic fail.",
                        rawError != null ? rawError.getClass().getName() : "null");
                urlBuilder.queryParam("status", "fail");
                urlBuilder.queryParam("error", "unknown_error_type");
            }

            String redirectUrl = urlBuilder.toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("Exception in NICE failCallback. Initial reqSeqFromNice: {}", reqSeqFromNice, e);
            String errorRedirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectFailUrl)
                    .queryParam("status", "fail")
                    .queryParam("error", "processing_failed_on_fail")
                    .queryParam("detail",
                            e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage()
                                    : e.getClass().getSimpleName())
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(errorRedirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    @GetMapping("/result/{resultKey}")
    public ResponseEntity<?> getVerificationResult(@PathVariable String resultKey) {
        try {
            Object resultData = niceService.peekRawNiceData(resultKey);
            if (resultData == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "결과를 찾을 수 없거나 만료되었습니다."));
            }

            return ResponseEntity.ok(resultData);
        } catch (Exception e) {
            log.error("Error retrieving NICE verification result for key {}: {}", resultKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "결과 조회 처리 중 에러 발생: " + e.getMessage()));
        }
    }
    // isValidReqSeq method is removed as its logic is now within NiceService's
    // consumeAndValidateReqSeq
}
