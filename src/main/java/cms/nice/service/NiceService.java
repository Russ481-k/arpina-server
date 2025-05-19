package cms.nice.service;

import NiceID.Check.CPClient;
import cms.nice.dto.NiceErrorDataDto;
import cms.nice.dto.NicePublicUserDataDto;
import cms.nice.dto.NiceUserDataDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Import UserRepository and User
import cms.user.repository.UserRepository;
import cms.user.domain.User;
import org.springframework.beans.factory.annotation.Autowired; // For constructor injection, or use @RequiredArgsConstructor

@Service
public class NiceService {

    private static final Logger log = LoggerFactory.getLogger(NiceService.class);

    @Value("${nice.checkplus.site-code}")
    private String siteCode;

    @Value("${nice.checkplus.site-password}")
    private String sitePassword;

    @Value("${nice.checkplus.base-callback-url}")
    private String baseCallbackUrl; // This might need adjustment if API base path changes

    // CacheEntry for storing data with expiry
    private static class CacheEntry {
        Object data;
        long expiryTime;
        CacheEntry(Object data, long expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
        }
    }

    // Stores for REQ_SEQ and NICE results, moved from NiceController
    private final Map<String, CacheEntry> tempReqSeqStore = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> tempResultStore = new ConcurrentHashMap<>();
    private static final long REQ_SEQ_EXPIRY_MINUTES = 10;
    private static final long RESULT_EXPIRY_MINUTES = 10;

    private final UserRepository userRepository; // Inject UserRepository

    @Autowired // Constructor injection
    public NiceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<String, String> initiateVerification() {
        CPClient niceCheck = new CPClient();
        String requestNumber = niceCheck.getRequestNO(siteCode); 
        log.info("[NICE] Generated reqSeq: {}", requestNumber);
        storeReqSeq(requestNumber); // Store REQ_SEQ with expiry

        String authType = ""; 
        String customize = ""; 
        // Ensure callback URLs are correctly formed, especially if base path for controller changes
        String returnUrl = baseCallbackUrl + "/api/v1/nice/checkplus/success"; // Example: http://localhost:8080/api/v1/nice/checkplus/success
        String errorUrl = baseCallbackUrl + "/api/v1/nice/checkplus/fail";   // Example: http://localhost:8080/api/v1/nice/checkplus/fail

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
        log.info("[NICE] Initiated verification for reqSeq: {}", requestNumber);
        return response;
    }

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
        if (!consumeAndValidateReqSeq(reqSeq)) {
            log.warn("[NICE] storeSuccessData - Invalid or expired reqSeq: {}", reqSeq);
            throw new RuntimeException("유효하지 않거나 만료된 NICE 요청 순서 번호입니다.");
        }

        String utf8Name = null;
        try {
            String tempUtf8Name = (String) parsedData.get("UTF8_NAME");
            if (tempUtf8Name != null) {
                utf8Name = URLDecoder.decode(tempUtf8Name, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            log.error("[NICE] UTF-8 Name decoding failed for reqSeq: {}", reqSeq, e);
        }
        String name = (String) parsedData.get("NAME");
        if (utf8Name == null) utf8Name = name;
        String di = (String) parsedData.get("DI");

        // Check if user already exists with this DI
        boolean alreadyJoined = false;
        String existingUsername = null;
        if (di != null && !di.isEmpty()) {
            User existingUser = userRepository.findByDi(di).orElse(null);
            if (existingUser != null) {
                alreadyJoined = true;
                existingUsername = existingUser.getUsername();
                log.info("[NICE] User with DI {} already exists. Username: {}", di, existingUsername);
            }
        }

        NiceUserDataDto userData = NiceUserDataDto.builder()
                .reqSeq(reqSeq)
                .resSeq((String) parsedData.get("RES_SEQ"))
                .authType((String) parsedData.get("AUTH_TYPE"))
                .name(name)
                .utf8Name(utf8Name)
                .birthDate((String) parsedData.get("BIRTHDATE"))
                .gender((String) parsedData.get("GENDER"))
                .nationalInfo((String) parsedData.get("NATIONALINFO"))
                .di(di)
                .ci((String) parsedData.get("CI"))
                .mobileCo((String) parsedData.get("MOBILE_CO"))
                .mobileNo((String) parsedData.get("MOBILE_NO"))
                .alreadyJoined(alreadyJoined) // Set the flag
                .existingUsername(existingUsername) // Set the existing username
                .build();
        
        String resultKey = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(RESULT_EXPIRY_MINUTES);
        tempResultStore.put(resultKey, new CacheEntry(userData, expiryTime));
        log.info("[NICE] Stored SUCCESS data with resultKey: {}, reqSeq: {}, expiry: {} ({} mins)", resultKey, reqSeq, expiryTime, RESULT_EXPIRY_MINUTES);
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

        if (!consumeAndValidateReqSeq(reqSeq)) {
            log.warn("[NICE] storeErrorData - Invalid or expired reqSeq: {}. Storing error data anyway.", reqSeq);
        }

        NiceErrorDataDto errorDataDto = NiceErrorDataDto.builder()
                .reqSeq(reqSeq)
                .errorCode((String) parsedData.get("ERR_CODE"))
                .authType((String) parsedData.get("AUTH_TYPE"))
                .message("NICE 본인인증 실패 (ERR_CODE: " + parsedData.get("ERR_CODE") + ")")
                .build();

        String resultKey = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(RESULT_EXPIRY_MINUTES);
        tempResultStore.put(resultKey, new CacheEntry(errorDataDto, expiryTime));
        log.info("[NICE] Stored ERROR data with resultKey: {}, reqSeq: {}, errorCode: {}, expiry: {} ({} mins)", resultKey, reqSeq, errorDataDto.getErrorCode(), expiryTime, RESULT_EXPIRY_MINUTES);
        return resultKey;
    }

    // Renamed and modified to return Full NiceUserDataDto for internal services like Auth
    public NiceUserDataDto getVerifiedFullNiceDataAndConsume(String resultKey) {
        log.info("[NICE] Attempting to getVerifiedFullNiceDataAndConsume for resultKey: {}", resultKey);
        log.info("[NICE] Current tempResultStore size: {}. Does it contain key '{}': {}",
                tempResultStore.size(), resultKey, tempResultStore.containsKey(resultKey));

        if (tempResultStore.isEmpty()) {
            log.warn("[NICE] tempResultStore is EMPTY when trying to get key: {}", resultKey);
        } else {
            log.info("[NICE] Keys currently in tempResultStore: {}", tempResultStore.keySet());
        }

        CacheEntry cachedResult = tempResultStore.get(resultKey);

        if (cachedResult == null) {
            log.warn("[NICE] No cache entry found for resultKey: {} using get(). Double checking with containsKey: {}", resultKey, tempResultStore.containsKey(resultKey));
            throw new RuntimeException("NICE 인증 결과를 찾을 수 없거나 만료되었습니다.");
        }

        if (System.currentTimeMillis() > cachedResult.expiryTime) {
            log.warn("[NICE] Cache entry expired for resultKey: {}. Current time: {}, Expiry time: {}", resultKey, System.currentTimeMillis(), cachedResult.expiryTime);
            tempResultStore.remove(resultKey); // Clean up expired
            log.info("[NICE] Removed expired entry for resultKey: {}. tempResultStore size after removal: {}", resultKey, tempResultStore.size());
            throw new RuntimeException("NICE 인증 결과가 만료되었습니다.");
        }

        Object data = cachedResult.data; // Get data before removing
        tempResultStore.remove(resultKey); // Consume the key
        log.info("[NICE] Successfully retrieved and consumed data for resultKey: {}. tempResultStore size after removal: {}", resultKey, tempResultStore.size());

        if (data instanceof NiceUserDataDto) {
            return (NiceUserDataDto) data;
        } else {
            log.error("[NICE] Cached data for resultKey: {} is not of type NiceUserDataDto. Actual type: {}", resultKey, data != null ? data.getClass().getName() : "null");
            throw new RuntimeException("NICE 인증 결과가 성공 데이터가 아닙니다. (잘못된 데이터 타입)");
        }
    }
    
    // This method is for peeking at the data, typically by NiceController, without consuming it.
    public Object peekRawNiceData(String resultKey) { // Renamed from getRawNiceDataAndConsume
        log.info("[NICE] Attempting to peekRawNiceData for resultKey: {}", resultKey);
        CacheEntry cachedResult = tempResultStore.get(resultKey);
        if (cachedResult == null) {
            log.warn("[NICE] peekRawNiceData - No cache entry found for resultKey: {}", resultKey);
            return null; 
        }
        if (System.currentTimeMillis() > cachedResult.expiryTime) {
            log.warn("[NICE] peekRawNiceData - Cache entry expired for resultKey: {}. (Entry will be removed by actual consumer or cleanup)", resultKey);
            // Do not remove here, let the consumer or a dedicated cleanup task handle it.
            return null; // Or throw an exception if expired data shouldn't be peeked
        }
        // DO NOT CONSUME (remove) the key here. Consumption happens in getVerifiedFullNiceDataAndConsume.
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

    private void storeReqSeq(String reqSeq) {
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(REQ_SEQ_EXPIRY_MINUTES);
        tempReqSeqStore.put(reqSeq, new CacheEntry(null, expiryTime));
        log.info("[NICE] Stored reqSeq: {} with expiry: {} ({} mins)", reqSeq, expiryTime, REQ_SEQ_EXPIRY_MINUTES);
    }

    public boolean consumeAndValidateReqSeq(String reqSeq) {
        log.info("[NICE] consumeAndValidateReqSeq - Attempting to validate reqSeq: {}", reqSeq);
        log.info("[NICE] Current tempReqSeqStore size: {}. Does it contain reqSeq '{}': {}",
                tempReqSeqStore.size(), reqSeq, tempReqSeqStore.containsKey(reqSeq));
        if (tempReqSeqStore.isEmpty()) {
            log.warn("[NICE] tempReqSeqStore is EMPTY when trying to validate reqSeq: {}", reqSeq);
        } else {
            log.info("[NICE] Keys currently in tempReqSeqStore: {}", tempReqSeqStore.keySet());
        }

        CacheEntry entry = tempReqSeqStore.get(reqSeq);

        if (entry == null) {
            log.warn("[NICE] consumeAndValidateReqSeq - reqSeq not found or already consumed: {}", reqSeq);
            return false;
        }
        if (System.currentTimeMillis() > entry.expiryTime) {
            log.warn("[NICE] consumeAndValidateReqSeq - reqSeq expired: {}", reqSeq);
            tempReqSeqStore.remove(reqSeq);
            return false;
        }
        tempReqSeqStore.remove(reqSeq); // 성공적으로 검증 후 사용된 sCPRequest 삭제
        log.info("[NICE] consumeAndValidateReqSeq - Successfully validated and consumed reqSeq: {}. tempReqSeqStore size after removal: {}",
                reqSeq, tempReqSeqStore.size());
        return true;
    }
} 