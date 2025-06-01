package cms.nice.service;

import NiceID.Check.CPClient;
import cms.common.service.EmailService;
import cms.common.exception.EmailSendingException;
import cms.nice.dto.NiceCallbackResultDto;
import cms.nice.dto.NiceErrorDataDto;
import cms.nice.dto.NiceReqSeqDataDto;
import cms.nice.dto.NiceUserDataDto;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NiceService {

    private static final Logger log = LoggerFactory.getLogger(NiceService.class);

    @Value("${nice.checkplus.site-code}")
    private String siteCode;

    @Value("${nice.checkplus.site-password}")
    private String sitePassword;

    @Value("${nice.checkplus.base-callback-url}")
    private String baseCallbackUrl;

    private final Map<String, CacheEntry> tempReqSeqStore = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> tempResultStore = new ConcurrentHashMap<>();
    private static final long REQ_SEQ_EXPIRY_MINUTES = 10;
    private static final long RESULT_EXPIRY_MINUTES = 10;
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static class CacheEntry {
        Object data;
        long expiryTime;
        CacheEntry(Object data, long expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
        }
    }

    public Map<String, String> initiateVerification(String serviceType) {
        CPClient niceCheck = new CPClient();
        String requestNumber = niceCheck.getRequestNO(siteCode); 
        log.info("[NICE] Generated reqSeq: {} for serviceType: {}", requestNumber, serviceType);
        storeReqSeqWithServiceType(requestNumber, serviceType);

        String authType = ""; 
        String customize = ""; 
        String returnUrl = baseCallbackUrl + "/api/v1/nice/checkplus/success";
        String errorUrl = baseCallbackUrl + "/api/v1/nice/checkplus/fail";

        String plainData = "7:REQ_SEQ" + requestNumber.getBytes().length + ":" + requestNumber +
                           "8:SITECODE" + siteCode.getBytes().length + ":" + siteCode +
                           "9:AUTH_TYPE" + authType.getBytes().length + ":" + authType +
                           "7:RTN_URL" + returnUrl.getBytes().length + ":" + returnUrl +
                           "7:ERR_URL" + errorUrl.getBytes().length + ":" + errorUrl +
                           "9:CUSTOMIZE" + customize.getBytes().length + ":" + customize;

        String encodeData = "";
        int result = niceCheck.fnEncode(siteCode, sitePassword, plainData);

        if (result == 0) {
            encodeData = niceCheck.getCipherData();
        } else {
            log.error("[NICE] Data encryption failed. Code: {}", result);
            throw new RuntimeException("NICE CheckPlus 데이터 암호화 실패. 코드: " + result);
        }

        Map<String, String> response = new HashMap<>();
        response.put("encodeData", encodeData);
        response.put("reqSeq", requestNumber); 
        log.info("[NICE] Initiated verification for reqSeq: {}, serviceType: {}", requestNumber, serviceType);
        return response;
    }

    @Transactional
    public String storeSuccessData(String encodeData) {
        CPClient niceCheck = new CPClient();
        String plainData;
        int result = niceCheck.fnDecode(siteCode, sitePassword, encodeData);
        if (result != 0) {
            log.error("[NICE] Success data decryption failed. Code: {}", result);
            throw new RuntimeException("NICE CheckPlus 성공 데이터 복호화 실패. 코드: " + result);
        }
        plainData = niceCheck.getPlainData();
        HashMap<?, ?> parsedData = niceCheck.fnParse(plainData);

        String reqSeq = (String) parsedData.get("REQ_SEQ");
        log.info("[NICE] storeSuccessData - Received reqSeq from NICE: {}", reqSeq);

        NiceReqSeqDataDto reqSeqData = consumeAndValidateReqSeq(reqSeq);
        if (reqSeqData == null) {
            log.warn("[NICE] storeSuccessData - Invalid or expired reqSeq: {}", reqSeq);
            throw new RuntimeException("유효하지 않거나 만료된 NICE 요청 순서 번호입니다.");
        }
        String serviceType = reqSeqData.getServiceType();
        log.info("[NICE] storeSuccessData - Successfully validated reqSeq: {} for serviceType: {}", reqSeq, serviceType);

        String utf8Name = null;
        try {
            String tempUtf8Name = (String) parsedData.get("UTF8_NAME");
            if (tempUtf8Name != null) {
                utf8Name = URLDecoder.decode(tempUtf8Name, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            log.error("[NICE] UTF-8 Name decoding failed for reqSeq: {}", reqSeq, e);
        }
        String nameFromNice = (utf8Name != null && !utf8Name.isEmpty()) ? utf8Name : (String) parsedData.get("NAME");
        String di = (String) parsedData.get("DI");

        NiceCallbackResultDto callbackResult;

        Optional<User> userOptional = Optional.empty();
        if (di != null && !di.isEmpty()) {
            userOptional = userRepository.findByDi(di);
        }

        switch (serviceType) {
            case "REGISTER":
                log.info("[NICE] Processing REGISTER for reqSeq: {}", reqSeq);
                NiceUserDataDto registerUserData = NiceUserDataDto.builder()
                .reqSeq(reqSeq)
                .resSeq((String) parsedData.get("RES_SEQ"))
                .authType((String) parsedData.get("AUTH_TYPE"))
                        .name(nameFromNice)
                .utf8Name(utf8Name)
                .birthDate((String) parsedData.get("BIRTHDATE"))
                .gender((String) parsedData.get("GENDER"))
                .nationalInfo((String) parsedData.get("NATIONALINFO"))
                .di(di)
                .ci((String) parsedData.get("CI"))
                .mobileCo((String) parsedData.get("MOBILE_CO"))
                .mobileNo((String) parsedData.get("MOBILE_NO"))
                        .alreadyJoined(userOptional.isPresent())
                        .existingUsername(userOptional.map(User::getUsername).orElse(null))
                .build();
                callbackResult = NiceCallbackResultDto.successForRegister(serviceType, registerUserData);
                break;

            case "FIND_ID":
                log.info("[NICE] Processing FIND_ID for reqSeq: {}", reqSeq);
                if (userOptional.isPresent()) {
                    User foundUser = userOptional.get();
                    if (foundUser.getEmail() != null && !foundUser.getEmail().isEmpty()) {
                        try {
                            emailService.sendUserIdEmail(foundUser.getEmail(), foundUser.getUsername(), foundUser.getName());
                            log.info("[NICE] FIND_ID - User ID email sent to: {} for user: {}", foundUser.getEmail(), foundUser.getUsername());
                            callbackResult = NiceCallbackResultDto.idFound(serviceType, foundUser.getEmail(), nameFromNice);
                        } catch (Exception e) {
                            log.error("[NICE] FIND_ID - Failed to send User ID email to: {}", foundUser.getEmail(), e);
                            callbackResult = NiceCallbackResultDto.error(serviceType, "EMAIL_SEND_FAILED", "아이디 안내 메일 발송에 실패했습니다. 관리자에게 문의해주세요.", nameFromNice);
                        }
                    } else {
                        log.warn("[NICE] FIND_ID - User {} has no email address. Cannot send ID.", foundUser.getUsername());
                        callbackResult = NiceCallbackResultDto.error(serviceType, "NO_EMAIL", "사용자에게 이메일 주소가 등록되어 있지 않아 아이디를 발송할 수 없습니다.", nameFromNice);
                    }
                } else {
                    log.info("[NICE] FIND_ID - No user found for DI: {}", di);
                    callbackResult = NiceCallbackResultDto.accountNotFound(serviceType, nameFromNice);
                }
                break;

            case "RESET_PASSWORD":
                log.info("[NICE] Processing RESET_PASSWORD for reqSeq: {}", reqSeq);
                if (userOptional.isPresent()) {
                    User userToReset = userOptional.get();
                    if (userToReset.getEmail() != null && !userToReset.getEmail().isEmpty()) {
                        String tempPassword = RandomStringUtils.randomAlphanumeric(TEMP_PASSWORD_LENGTH);
                        try {
                            log.info("[NICE] RESET_PASSWORD - Email for user {} before sending: '[{}]', length: {}",
                                     userToReset.getUsername(),
                                     userToReset.getEmail(),
                                     userToReset.getEmail().length());

                            userToReset.setPassword(passwordEncoder.encode(tempPassword));
                            userToReset.setIsTemporary(true);
                            userToReset.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
                            userRepository.save(userToReset);
                            
                            emailService.sendTemporaryPasswordEmail(userToReset.getEmail(), tempPassword, userToReset.getName());
                            log.info("[NICE] RESET_PASSWORD - Temporary password email sent to: {} for user: {}", userToReset.getEmail(), userToReset.getUsername());
                            callbackResult = NiceCallbackResultDto.passwordResetSent(serviceType, userToReset.getEmail(), nameFromNice);
                        } catch (EmailSendingException e) {
                            log.error("[NICE] RESET_PASSWORD - Failed to send temporary password for user: {}. ErrorCode: {}, Message: {}", userToReset.getUsername(), e.getErrorCode(), e.getMessage(), e);
                            callbackResult = NiceCallbackResultDto.error(serviceType, e.getErrorCode(), e.getMessage(), nameFromNice);
                        } catch (Exception e) {
                            log.error("[NICE] RESET_PASSWORD - Failed to process password reset for user: {}", userToReset.getUsername(), e);
                            callbackResult = NiceCallbackResultDto.error(serviceType, "PASSWORD_RESET_PROCESS_FAILED", "비밀번호 재설정 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.", nameFromNice);
                        }
                    } else {
                        log.warn("[NICE] RESET_PASSWORD - User {} has no email address. Cannot send temporary password.", userToReset.getUsername());
                        callbackResult = NiceCallbackResultDto.error(serviceType, "NO_EMAIL", "사용자에게 이메일 주소가 등록되어 있지 않아 임시 비밀번호를 발송할 수 없습니다.", nameFromNice);
                    }
                } else {
                    log.info("[NICE] RESET_PASSWORD - No user found for DI: {}", di);
                    callbackResult = NiceCallbackResultDto.accountNotFound(serviceType, nameFromNice);
                }
                break;

            default:
                log.warn("[NICE] Unknown serviceType: {} for reqSeq: {}", serviceType, reqSeq);
                callbackResult = NiceCallbackResultDto.error(serviceType, "INVALID_SERVICE_TYPE", "알 수 없는 서비스 요청 유형입니다.", nameFromNice);
                break;
        }
        
        String resultKey = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(RESULT_EXPIRY_MINUTES);
        tempResultStore.put(resultKey, new CacheEntry(callbackResult, expiryTime));
        log.info("[NICE] Stored callback result for serviceType: {} with resultKey: {}, expiry: {} ({} mins)", serviceType, resultKey, expiryTime, RESULT_EXPIRY_MINUTES);
        return resultKey;
    }

    public String storeErrorData(String encodeData) {
        CPClient niceCheck = new CPClient();
        String plainData;
        int result = niceCheck.fnDecode(siteCode, sitePassword, encodeData);
        if (result != 0) {
            log.error("[NICE] Error data decryption failed. Code: {}", result);
            throw new RuntimeException("NICE CheckPlus 실패 데이터 복호화 실패. 코드: " + result);
        }
        plainData = niceCheck.getPlainData();
        HashMap<?, ?> parsedData = niceCheck.fnParse(plainData);
        String reqSeq = (String) parsedData.get("REQ_SEQ");
        log.info("[NICE] storeErrorData - Received reqSeq from NICE: {}", reqSeq);

        NiceReqSeqDataDto reqSeqData = getReqSeqData(reqSeq);
        String serviceType = "UNKNOWN";
        if (reqSeqData != null) {
            serviceType = reqSeqData.getServiceType();
            log.info("[NICE] storeErrorData - reqSeq: {} has serviceType: {}. Storing error data.", reqSeq, serviceType);
        } else {
            log.warn("[NICE] storeErrorData - reqSeq not found or expired: {}. Error data will be stored without serviceType context.", reqSeq);
        }

        NiceErrorDataDto errorDataDto = NiceErrorDataDto.builder()
                .reqSeq(reqSeq)
                .errorCode((String) parsedData.get("ERR_CODE"))
                .authType((String) parsedData.get("AUTH_TYPE"))
                .message("NICE 본인인증 실패 (ERR_CODE: " + parsedData.get("ERR_CODE") + ")")
                .serviceType(serviceType)
                .build();

        String resultKey = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(RESULT_EXPIRY_MINUTES);
        tempResultStore.put(resultKey, new CacheEntry(errorDataDto, expiryTime));
        log.info("[NICE] Stored ERROR data with resultKey: {}, reqSeq: {}, errorCode: {}, serviceType: {}, expiry: {} ({} mins)",
                resultKey, reqSeq, errorDataDto.getErrorCode(), serviceType, expiryTime, RESULT_EXPIRY_MINUTES);
        return resultKey;
    }

    public NiceUserDataDto getVerifiedNiceUserDataForRegister(String resultKey) {
        log.info("[NICE] Attempting to getVerifiedNiceUserDataForRegister for resultKey: {}", resultKey);
        CacheEntry cachedResult = tempResultStore.get(resultKey);

        if (cachedResult == null) {
            log.warn("[NICE] No cache entry found for resultKey: {}", resultKey);
            throw new RuntimeException("NICE 인증 결과를 찾을 수 없거나 만료되었습니다.");
        }

        if (System.currentTimeMillis() > cachedResult.expiryTime) {
            log.warn("[NICE] Cache entry expired for resultKey: {}", resultKey);
            tempResultStore.remove(resultKey);
            throw new RuntimeException("NICE 인증 결과가 만료되었습니다.");
        }

        Object data = cachedResult.data;
        if (data instanceof NiceCallbackResultDto) {
            NiceCallbackResultDto callbackResult = (NiceCallbackResultDto) data;
            if ("REGISTER".equals(callbackResult.getServiceType()) && callbackResult.getUserData() != null) {
                tempResultStore.remove(resultKey);
                log.info("[NICE] Successfully retrieved and consumed REGISTER data for resultKey: {}", resultKey);
                return callbackResult.getUserData();
            } else {
                 log.error("[NICE] Cached data for resultKey: {} is NiceCallbackResultDto but not for REGISTER or userData is null. ServiceType: {}", resultKey, callbackResult.getServiceType());
                throw new RuntimeException("NICE 인증 결과가 회원가입 성공 데이터가 아닙니다.");
            }
        } else if (data instanceof NiceUserDataDto) {
            tempResultStore.remove(resultKey);
            log.warn("[NICE] Directly consumed NiceUserDataDto for resultKey: {} (should be wrapped in NiceCallbackResultDto for REGISTER)", resultKey);
            return (NiceUserDataDto) data;
        }
        log.error("[NICE] Cached data for resultKey: {} is not expected type for REGISTER. Actual type: {}", resultKey, data != null ? data.getClass().getName() : "null");
        throw new RuntimeException("NICE 인증 결과 데이터 타입 오류입니다.");
    }
    
    public Object peekRawNiceData(String resultKey) {
        log.info("[NICE] Attempting to peekRawNiceData for resultKey: {}", resultKey);
        CacheEntry cachedResult = tempResultStore.get(resultKey);
        if (cachedResult == null) {
            log.warn("[NICE] peekRawNiceData - No cache entry found for resultKey: {}", resultKey);
            return null; 
        }
        if (System.currentTimeMillis() > cachedResult.expiryTime) {
            log.warn("[NICE] peekRawNiceData - Cache entry expired for resultKey: {}.", resultKey);
            return null; 
        }
        log.info("[NICE] peekRawNiceData - Successfully retrieved data for resultKey: {} (without consuming)", resultKey);
        return cachedResult.data;
    }

    public String getReqSeqFromEncodedData(String encodeData) {
        CPClient niceCheck = new CPClient();
        int result = niceCheck.fnDecode(siteCode, sitePassword, encodeData);
        if (result == 0) {
            String plainData = niceCheck.getPlainData();
            if (plainData == null || plainData.isEmpty()) return null;
            HashMap<?, ?> parsedData = niceCheck.fnParse(plainData);
            if (parsedData == null) return null;
            return (String) parsedData.get("REQ_SEQ");
        } else {
            return null; 
        }
    }

    private void storeReqSeqWithServiceType(String reqSeq, String serviceType) {
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(REQ_SEQ_EXPIRY_MINUTES);
        NiceReqSeqDataDto dataToStore = new NiceReqSeqDataDto(reqSeq, serviceType, System.currentTimeMillis());
        tempReqSeqStore.put(reqSeq, new CacheEntry(dataToStore, expiryTime));
        log.info("[NICE] Stored reqSeq: {} with serviceType: {}, expiry: {} ({} mins)", reqSeq, serviceType, expiryTime, REQ_SEQ_EXPIRY_MINUTES);
    }

    public NiceReqSeqDataDto consumeAndValidateReqSeq(String reqSeq) {
        log.info("[NICE] Attempting to consumeAndValidateReqSeq: {}", reqSeq);
        CacheEntry entry = tempReqSeqStore.get(reqSeq);
        if (entry == null) {
            log.warn("[NICE] consumeAndValidateReqSeq - reqSeq not found or already consumed: {}", reqSeq);
            return null;
        }
        if (System.currentTimeMillis() > entry.expiryTime) {
            log.warn("[NICE] consumeAndValidateReqSeq - reqSeq expired: {}", reqSeq);
            tempReqSeqStore.remove(reqSeq);
            return null;
        }
        tempReqSeqStore.remove(reqSeq); 
        log.info("[NICE] consumeAndValidateReqSeq - Successfully validated and consumed reqSeq: {}. tempReqSeqStore size after removal: {}",
                reqSeq, tempReqSeqStore.size());
        if (entry.data instanceof NiceReqSeqDataDto) {
            return (NiceReqSeqDataDto) entry.data;
        } else {
            log.warn("[NICE] consumeAndValidateReqSeq - Data in tempReqSeqStore for reqSeq: {} is not of type NiceReqSeqDataDto. Actual type: {}. Returning null.", 
                reqSeq, entry.data != null ? entry.data.getClass().getName() : "null");
            return null;
        }
    }

    private NiceReqSeqDataDto getReqSeqData(String reqSeq) {
        CacheEntry entry = tempReqSeqStore.get(reqSeq);
        if (entry == null) {
            log.warn("[NICE] getReqSeqData - reqSeq not found: {}", reqSeq);
            return null;
        }
        if (System.currentTimeMillis() > entry.expiryTime) {
            log.warn("[NICE] getReqSeqData - reqSeq expired: {}", reqSeq);
            return null;
        }
        if (entry.data instanceof NiceReqSeqDataDto) {
            return (NiceReqSeqDataDto) entry.data;
        } else {
            log.warn("[NICE] getReqSeqData - Data in tempReqSeqStore for reqSeq: {} is not of type NiceReqSeqDataDto. Actual type: {}. Returning null.", 
                reqSeq, entry.data != null ? entry.data.getClass().getName() : "null");
            return null;
        }
    }
} 