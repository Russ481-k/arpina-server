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

@RestController
@RequestMapping("/nice/checkplus") // Ensure base path is consistent or configured
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
        this.frontendRedirectSuccessUrl = allowedOrigins + frontendSuccessPath;
        this.frontendRedirectFailUrl = allowedOrigins + frontendFailPath;
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateVerification() {
        try {
            Map<String, String> initData = niceService.initiateVerification();
            // reqSeq is now stored and managed by NiceService
            NiceInitiateResponseDto responseDto = new NiceInitiateResponseDto(initData.get("encodeData"), initData.get("reqSeq"));
            return ResponseEntity.ok(responseDto);
        } catch (Exception e) {
            log.error("Error initiating NICE verification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "본인인증 초기화 실패: " + e.getMessage()));
        }
    }

    @RequestMapping(value = "/success", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> successCallback(@RequestParam("EncodeData") String encodeData) {
        String reqSeqFromNice = null; // For logging purposes in case of error before validation
        try {
            reqSeqFromNice = niceService.getReqSeqFromEncodedData(encodeData); // Still useful for logging if needed
            // Actual reqSeq validation is now handled within niceService.storeSuccessData
            String resultKey = niceService.storeSuccessData(encodeData);

            // Check if the user is already joined
            Object rawData = niceService.peekRawNiceData(resultKey);
            boolean isAlreadyJoined = false;
            String existingUsername = null;

            if (rawData instanceof cms.nice.dto.NiceUserDataDto) {
                cms.nice.dto.NiceUserDataDto niceUserData = (cms.nice.dto.NiceUserDataDto) rawData;
                isAlreadyJoined = niceUserData.isAlreadyJoined();
                if (isAlreadyJoined) {
                    existingUsername = niceUserData.getExistingUsername();
                }
            }

            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(frontendRedirectSuccessUrl)
                                .queryParam("status", "success")
                                .queryParam("key", resultKey)
                                .queryParam("joined", String.valueOf(isAlreadyJoined));
            
            // Only add username and error code if we have a duplicate user
            if (isAlreadyJoined && existingUsername != null) {
                urlBuilder.queryParam("username", existingUsername)
                          .queryParam("nice_error_code", "DUPLICATE_DI");
                    log.info("[NICE] User with DI already joined. Redirecting with joined=true and error_code=DUPLICATE_DI. Username: {}", existingUsername);
            } else {
                log.info("[NICE] New user. Redirecting for signup.");
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
                                        .queryParam("detail", e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getSimpleName())
                                        .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(errorRedirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }
    }

    @RequestMapping(value = "/fail", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> failCallback(@RequestParam("EncodeData") String encodeData) {
        String reqSeqFromNice = null; // For logging
        try {
            reqSeqFromNice = niceService.getReqSeqFromEncodedData(encodeData);
            // Actual reqSeq validation is now handled within niceService.storeErrorData
            String resultKey = niceService.storeErrorData(encodeData);
            // Error details are now encapsulated within the object stored by resultKey if needed
            // The error code might be part of the DTO fetched by /result/{resultKey}

            // Construct redirect URL, possibly including the resultKey to fetch error details on FE
            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromUriString(frontendRedirectFailUrl)
                                                .queryParam("status", "fail")
                                                .queryParam("key", resultKey);
            
            // Optionally, decode error code here if needed for immediate redirect query param, 
            // but generally better to fetch details via resultKey to keep this clean.
            // Object errorDetails = niceService.getRawNiceDataAndConsume(resultKey); // This would consume it too early if FE needs it.
            // For now, just pass the key.

            String redirectUrl = urlBuilder.toUriString();                           
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("Exception in NICE failCallback. Initial reqSeqFromNice: {}", reqSeqFromNice, e);
            String errorRedirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectFailUrl)
                                        .queryParam("status", "fail")
                                        .queryParam("error", "processing_failed_on_fail")
                                         .queryParam("detail", e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getSimpleName())
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
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.singletonMap("error", "결과를 찾을 수 없거나 만료되었습니다."));
            }

            // If resultData is NiceUserDataDto, convert to NicePublicUserDataDto for exposure
            if (resultData instanceof cms.nice.dto.NiceUserDataDto) {
                cms.nice.dto.NiceUserDataDto fullUserData = (cms.nice.dto.NiceUserDataDto) resultData;
                NicePublicUserDataDto publicData = NicePublicUserDataDto.builder()
                        .reqSeq(fullUserData.getReqSeq())
                        .resSeq(fullUserData.getResSeq())
                        .authType(fullUserData.getAuthType())
                        .name(fullUserData.getUtf8Name() != null ? fullUserData.getUtf8Name() : fullUserData.getName())
                        .utf8Name(fullUserData.getUtf8Name())
                        .birthDate(fullUserData.getBirthDate())
                        .gender(fullUserData.getGender())
                        .nationalInfo(fullUserData.getNationalInfo())
                        .mobileCo(fullUserData.getMobileCo())
                        .mobileNo(fullUserData.getMobileNo())
                        .build();
                return ResponseEntity.ok(publicData);
            } else {
                // If it's NiceErrorDataDto or other types, return as is.
                return ResponseEntity.ok(resultData);
            }
        } catch (Exception e) {
            log.error("Error retrieving NICE verification result for key {}: {}", resultKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "결과 조회 처리 중 에러 발생: " + e.getMessage()));
        }
    }
    // isValidReqSeq method is removed as its logic is now within NiceService's consumeAndValidateReqSeq
}
