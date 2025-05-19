# NICE 안심본인인증 연동 가이드 (Spring Boot + Next.js)

## 1. 개요

본 문서는 NICE평가정보의 안심본인인증 서비스를 Spring Boot 백엔드 API 서버와 Next.js 프론트엔드 클라이언트 환경에 연동하는 방법을 안내합니다. 사용자는 프론트엔드에서 본인인증을 요청하고, 실제 인증 처리는 백엔드와 NICE 서버 간의 통신을 통해 이루어지며, 최종 결과는 다시 프론트엔드에 안전하게 전달됩니다.

### 1.1. 시스템 아키텍처

```
+-------------+        +---------------------+        +--------------------------+        +-----------------+
| Next.js     | ---->  | Spring Boot API     | ---->  | NICE 안심본인인증 서버 | <----> | 사용자 브라우저 |
| (Client)    | <----  | (Backend)           | <----  | (nice.checkplus.co.kr)   |        | (NICE 팝업)   |
+-------------+        +---------------------+        +--------------------------+        +-----------------+
     ^      |                ^       |
     |      | (Redirect w/  |       | (EncodeData 전달)
     |      |  temp key)     |       |
     +------+----------------+       +---------------------------------------------------------+
       (Fetch result
        w/ temp key)
```

1.  **사용자 (Next.js)**: 본인인증 요청 시작.
2.  **Next.js -> Spring Boot API**: 본인인증 초기화 요청 (`/api/v1/nice/checkplus/initiate`).
3.  **Spring Boot API**:
    - `REQ_SEQ` 생성 및 임시 저장 (예: Redis).
    - NICE 서버로 전달할 `EncodeData` 생성 (사이트 정보, 콜백 URL 포함).
    - `EncodeData`를 Next.js로 전달.
4.  **Next.js**: 전달받은 `EncodeData`로 NICE 인증 팝업창 실행.
5.  **사용자 브라우저 (NICE 팝업)**: 사용자가 NICE 인증 절차 수행.
6.  **NICE 서버 -> Spring Boot API**: 인증 결과(성공/실패)를 Spring Boot API의 지정된 콜백 URL (`/api/v1/nice/checkplus/success` 또는 `/fail`)로 전달 (암호화된 결과 데이터 포함).
7.  **Spring Boot API**:
    - NICE로부터 받은 데이터 복호화 및 `REQ_SEQ` 검증.
    - 인증 결과(사용자 정보 또는 에러 정보)를 임시 저장소에 새 임시 키와 함께 저장.
    - Next.js의 특정 페이지로 리다이렉션 (결과 조회를 위한 임시 키 전달).
8.  **Next.js**: 리다이렉션된 페이지에서 임시 키를 사용하여 Spring Boot API에 최종 결과 요청 (`/api/v1/nice/checkplus/result/{tempKey}`).
9.  **Spring Boot API -> Next.js**: 임시 키에 해당하는 실제 사용자 정보 전달.
10. **Next.js**: 사용자에게 인증 결과 표시 또는 후속 처리.

## 2. 사전 준비 사항

### 2.1. NICE평가정보 계약 정보

- **사이트 코드 (Site Code)**: NICE로부터 발급받은 회원사 고유 코드.
- **사이트 패스워드 (Site Password)**: NICE로부터 발급받은 회원사 고유 패스워드.

> 이 정보들은 Spring Boot 백엔드 서버의 안전한 설정 파일 (예: `application.yml` 또는 환경 변수)에 저장되어야 하며, 클라이언트 측에 노출되어서는 안 됩니다.

### 2.2. `NiceID.jar` 라이브러리 설정 (백엔드)

1.  **로컬 Maven 레파지토리 설치**:
    NICE로부터 제공받은 `NiceID_v1.2.jar` (또는 최신 버전) 파일을 로컬 Maven 레파지토리에 설치합니다. 프로젝트 루트에 `libs` 폴더를 만들고 JAR 파일을 위치시킨 후 다음 명령어를 실행합니다 (경로 및 버전은 실제 파일에 맞게 수정).
    ```bash
    mvn install:install-file -Dfile=libs/NiceID_v1.2.jar -DgroupId=com.niceid -DartifactId=niceid -Dversion=1.2 -Dpackaging=jar
    ```
2.  **`pom.xml` 의존성 추가**:
    ```xml
    <dependency>
        <groupId>com.niceid</groupId>
        <artifactId>niceid</artifactId>
        <version>1.2</version> <!-- 설치한 버전에 맞게 수정 -->
    </dependency>
    ```

### 2.3. 네트워크 및 방화벽 설정 (백엔드 서버)

백엔드 서버에서 NICE 서버와 통신할 수 있도록 다음 주소 및 포트에 대한 아웃바운드 연결이 허용되어야 합니다 (NICE 개발 가이드 V3.2 기준):

1.  **본인인증 서비스 (팝업)**:
    - URL: `nice.checkplus.co.kr`
    - IP: `121.131.196.215` (변동 가능성 있으므로 가급적 URL 기준)
    - PORT: `80`, `443`
2.  **유량제어**:
    - URL: `ifc.niceid.co.kr`
    - IP: `121.131.196.193` (변동 가능성 있으므로 가급적 URL 기준)
    - PORT: `80`, `443`
3.  **본인인증 이미지**:
    - URL: `img.niceid.co.kr` (유동 IP)

## 3. 백엔드 API 구현 가이드 (Spring Boot)

### 3.1. 설정 (`application.yml` 또는 `application.properties`)

```yaml
nice:
  checkplus:
    site-code: "YOUR_SITE_CODE" # NICE에서 발급받은 사이트 코드
    site-password: "YOUR_SITE_PASSWORD" # NICE에서 발급받은 사이트 패스워드
    # 서버의 기본 URL (콜백 URL 생성 시 사용)
    base-callback-url: "https://your-api-server.com" # 실제 API 서버 주소로 변경
    # Next.js 리다이렉션 URL
    frontend-redirect-success-url: "https://your-nextjs-app.com/auth/nice/callback?status=success" # 실제 Next.js 성공 콜백 페이지
    frontend-redirect-fail-url: "https://your-nextjs-app.com/auth/nice/callback?status=fail" # 실제 Next.js 실패 콜백 페이지
```

### 3.2. `NiceService.java`

```java
import NiceID.Check.CPClient; // NICE 제공 라이브러리
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.HashMap; // 결과 파싱용
import java.util.UUID;    // 임시 키 생성용
// import org.springframework.data.redis.core.StringRedisTemplate; // Redis 사용 시
// import java.util.concurrent.TimeUnit; // Redis TTL 사용 시

@Service
public class NiceService {

    @Value("${nice.checkplus.site-code}")
    private String siteCode;

    @Value("${nice.checkplus.site-password}")
    private String sitePassword;

    @Value("${nice.checkplus.base-callback-url}")
    private String baseCallbackUrl;

    // 예시: RedisTemplate 주입 (실제로는 Redis 설정 필요)
    // private final StringRedisTemplate redisTemplate;

    // public NiceService(StringRedisTemplate redisTemplate) {
    //    this.redisTemplate = redisTemplate;
    // }


    /**
     * 본인인증 시작을 위한 암호화 데이터 생성
     * @return Map: "encodeData" (NICE 전달용 암호화 데이터), "requestKey" (REQ_SEQ 검증을 위한 임시 키)
     */
    public HashMap<String, String> initiateVerification() {
        CPClient niceCheck = new CPClient();

        String requestNumber = niceCheck.getRequestNO(siteCode); // CP요청번호 (REQ_SEQ) 생성

        // REQ_SEQ 보안 강화를 위해 임시 키와 매핑하여 Redis 등에 저장 (유효시간 5-10분)
        // String requestKey = UUID.randomUUID().toString();
        // redisTemplate.opsForValue().set("NICE_REQ_SEQ:" + requestKey, requestNumber, 10, TimeUnit.MINUTES);
        // 여기서는 단순화를 위해 requestNumber 자체를 키로 사용한다고 가정 (실제 환경에서는 위와 같이 임시 키 사용 권장)
        // 또는 세션 대신 사용할 임시 저장소에 requestNumber를 저장합니다. 예를 들어, Map이나 DB.
        // session.setAttribute("REQ_SEQ", requestNumber); // JSP 방식
        // **중요**: 이 `requestNumber`는 콜백 시 검증되어야 합니다.

        String authType = ""; // 비워두면 전체 인증수단, 특정 인증수단만 원할 시 값 설정 (M: 휴대폰, X: 공인인증서 등)
        String customize = ""; // 모바일 커스터마이징 (M: 모바일 내부 실행)

        String returnUrl = baseCallbackUrl + "/api/v1/nice/checkplus/success"; // 성공 시 API 콜백
        String errorUrl = baseCallbackUrl + "/api/v1/nice/checkplus/fail";    // 실패 시 API 콜백

        // PlainData 구성: "키길이:키값길이:키값..." 형식
        String plainData = "7:REQ_SEQ" + requestNumber.getBytes().length + ":" + requestNumber +
                           "8:SITECODE" + siteCode.getBytes().length + ":" + siteCode +
                           "9:AUTH_TYPE" + authType.getBytes().length + ":" + authType +
                           "7:RTN_URL" + returnUrl.getBytes().length + ":" + returnUrl +
                           "7:ERR_URL" + errorUrl.getBytes().length + ":" + errorUrl +
                           "9:CUSTOMIZE" + customize.getBytes().length + ":" + customize;

        String encodeData = "";
        int result = niceCheck.fnEncode(siteCode, sitePassword, plainData);
        if (result == 0) { // 성공
            encodeData = niceCheck.getCipherData();
        } else { // 암호화 실패
            // 에러 처리: result 값에 따라 (개발가이드 참조: -1, -2, -3, -9 등)
            throw new RuntimeException("NICE CheckPlus 데이터 암호화 실패: " + result);
        }

        HashMap<String, String> response = new HashMap<>();
        response.put("encodeData", encodeData);
        // response.put("requestKey", requestKey); // REQ_SEQ 검증을 위한 임시 키 반환
        // 여기서는 컨트롤러에서 REQ_SEQ 자체를 임시키로 사용하도록 유도
        response.put("reqSeq", requestNumber);

        return response;
    }

    /**
     * NICE 성공 콜백 처리
     * @param encodeData NICE로부터 받은 암호화된 결과 데이터
     * @return Map: 사용자 정보 (DI, CI, 이름 등), "resultKey" (프론트에서 결과 조회용 임시 키)
     */
    public HashMap<String, Object> processSuccessCallback(String encodeData) {
        CPClient niceCheck = new CPClient();
        String plainData = "";

        int result = niceCheck.fnDecode(siteCode, sitePassword, encodeData);
        if (result == 0) { // 성공
            plainData = niceCheck.getPlainData();
        } else {
            // 복호화 실패 처리 (개발가이드 참조: -1, -4, -5, -6, -9, -12 등)
            throw new RuntimeException("NICE CheckPlus 성공 데이터 복호화 실패: " + result);
        }

        // PlainData 파싱 (예: "키길이:키값길이:키값..." -> HashMap)
        HashMap parsedData = niceCheck.fnParse(plainData);

        String reqSeq = (String) parsedData.get("REQ_SEQ");

        // **중요**: 저장된 REQ_SEQ (또는 requestKey로 조회한 REQ_SEQ)와 비교 검증
        // String storedReqSeq = redisTemplate.opsForValue().get("NICE_REQ_SEQ:" + reqSeq); // 만약 requestKey를 사용했다면 해당 키로 조회
        // if (reqSeq == null || !reqSeq.equals(storedReqSeqFromTempStore)) {
        //     throw new SecurityException("유효하지 않은 REQ_SEQ 입니다.");
        // }
        // redisTemplate.delete("NICE_REQ_SEQ:" + reqSeq); // 검증 후 임시 REQ_SEQ 삭제

        // 사용자 정보 추출 (NICE 개발 가이드 3.3. 결과 항목 안내 참조)
        String name = (String) parsedData.get("UTF8_NAME"); // UTF-8 이름 권장, URLDecode 필요할 수 있음
        if (name != null) {
            try {
                name = java.net.URLDecoder.decode(name, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                // 대체로 EUC-KR 이름 사용
                name = (String) parsedData.get("NAME");
            }
        } else {
             name = (String) parsedData.get("NAME");
        }

        String di = (String) parsedData.get("DI");
        String ci = (String) parsedData.get("CI");
        String birthDate = (String) parsedData.get("BIRTHDATE");
        String gender = (String) parsedData.get("GENDER"); // 0: 여성, 1: 남성
        // ... 기타 필요한 정보 ...

        HashMap<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", name);
        userInfo.put("di", di);
        userInfo.put("ci", ci);
        userInfo.put("birthDate", birthDate);
        userInfo.put("gender", gender);
        // ...

        // 최종 사용자 정보를 임시 저장소(Redis 등)에 저장하고, 프론트엔드가 조회할 새 임시 키 발급
        String resultKey = UUID.randomUUID().toString();
        // redisTemplate.opsForValue().set("NICE_RESULT:" + resultKey, new ObjectMapper().writeValueAsString(userInfo), 5, TimeUnit.MINUTES);
        // 여기서는 userInfo Map 자체를 결과로 리턴하고, resultKey도 같이 리턴하여 Controller가 처리하도록 함

        HashMap<String, Object> response = new HashMap<>();
        response.put("userData", userInfo);
        response.put("resultKey", resultKey); // 프론트엔드가 이 키로 최종 결과를 가져갈 수 있도록 함

        return response;
    }

    /**
     * NICE 실패 콜백 처리
     * @param encodeData NICE로부터 받은 암호화된 결과 데이터
     * @return Map: 에러 정보, "resultKey" (프론트에서 에러 상세 조회용 임시 키, 선택적)
     */
    public HashMap<String, Object> processFailureCallback(String encodeData) {
        CPClient niceCheck = new CPClient();
        String plainData = "";

        int result = niceCheck.fnDecode(siteCode, sitePassword, encodeData);
        if (result == 0) { // 성공
            plainData = niceCheck.getPlainData();
        } else {
            // 복호화 실패 처리
            throw new RuntimeException("NICE CheckPlus 실패 데이터 복호화 실패: " + result);
        }

        HashMap parsedData = niceCheck.fnParse(plainData);
        String reqSeq = (String) parsedData.get("REQ_SEQ");
        String errorCode = (String) parsedData.get("ERR_CODE"); // NICE 응답코드 문서 참조
        String authType = (String) parsedData.get("AUTH_TYPE");

        // REQ_SEQ 검증 (성공 콜백과 유사하게 처리)
        // ...

        HashMap<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("errorCode", errorCode);
        errorInfo.put("authType", authType);
        errorInfo.put("reqSeq", reqSeq);

        // 에러 정보도 임시 저장 후 resultKey로 프론트엔드에 전달 가능
        String resultKey = UUID.randomUUID().toString();
        // redisTemplate.opsForValue().set("NICE_RESULT_ERROR:" + resultKey, new ObjectMapper().writeValueAsString(errorInfo), 5, TimeUnit.MINUTES);

        HashMap<String, Object> response = new HashMap<>();
        response.put("errorData", errorInfo);
        response.put("resultKey", resultKey);

        return response;
    }

    /**
     * 프론트엔드에서 최종 결과 조회
     * @param resultKey 임시 결과 키
     * @return Object: 저장된 사용자 정보 또는 에러 정보
     */
    public Object getVerificationResult(String resultKey) {
        // String jsonData = redisTemplate.opsForValue().get("NICE_RESULT:" + resultKey);
        // if (jsonData == null) {
        //     jsonData = redisTemplate.opsForValue().get("NICE_RESULT_ERROR:" + resultKey);
        //     redisTemplate.delete("NICE_RESULT_ERROR:" + resultKey);
        // } else {
        //     redisTemplate.delete("NICE_RESULT:" + resultKey);
        // }
        // if (jsonData == null) {
        //     throw new RuntimeException("유효하지 않거나 만료된 결과 키입니다.");
        // }
        // return new ObjectMapper().readValue(jsonData, HashMap.class); // 예시, 실제 타입에 맞게 변환

        // 이 가이드에서는 Controller가 직접 Service의 다른 메소드 결과(userInfo, errorInfo)를 임시로 저장하고
        // 이 메소드는 그 저장된 값을 가져오는 것으로 가정 (실제로는 Redis 사용 권장)
        // 임시 저장된 데이터를 조회하는 로직 (예: Controller 내의 Map 또는 전용 Service)
        throw new UnsupportedOperationException("임시 저장소에서 결과 조회 로직 구현 필요");
    }
}
```

> **참고**: 위 `NiceService` 코드는 Redis 연동 부분이 주석 처리되어 있습니다. 실제 운영 환경에서는 `REQ_SEQ` 및 최종 사용자 정보의 임시 저장을 위해 Redis와 같은 외부 저장소를 사용하는 것이 좋습니다. 이 가이드에서는 개념 설명을 위해 단순화된 부분이 있습니다. `ObjectMapper` 사용 시 `jackson-databind` 의존성이 필요합니다.

### 3.3. `NiceController.java`

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest; // REQ_SEQ 세션 저장/조회 예시 (비권장)
import javax.servlet.http.HttpSession;       // REQ_SEQ 세션 저장/조회 예시 (비권장)
import java.util.HashMap;
import java.util.Map; // 임시 데이터 저장용
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; // 임시 데이터 저장용


@Controller
@RequestMapping("/api/v1/nice/checkplus")
public class NiceController {

    private final NiceService niceService;

    @Value("${nice.checkplus.frontend-redirect-success-url}")
    private String frontendSuccessUrl;

    @Value("${nice.checkplus.frontend-redirect-fail-url}")
    private String frontendFailUrl;

    // !!!주의: 프로덕션에서는 Redis와 같은 외부 세션/캐시 저장소 사용 권장!!!
    // REQ_SEQ 임시 저장용 (실제로는 Redis 사용)
    private final Map<String, String> tempReqSeqStore = new ConcurrentHashMap<>();
    // 최종 결과 임시 저장용 (실제로는 Redis 사용)
    private final Map<String, Object> tempResultStore = new ConcurrentHashMap<>();


    public NiceController(NiceService niceService) {
        this.niceService = niceService;
    }

    /**
     * 본인인증 시작 (프론트엔드에서 호출)
     * @return 암호화된 데이터 (EncodeData) 와 REQ_SEQ (또는 REQ_SEQ 조회용 키)
     */
    @PostMapping("/initiate")
    @ResponseBody
    public ResponseEntity<Map<String, String>> initiateVerification() {
        try {
            HashMap<String, String> initData = niceService.initiateVerification();
            String reqSeq = initData.get("reqSeq"); // Service에서 생성한 REQ_SEQ

            // REQ_SEQ 임시 저장 (key: REQ_SEQ, value: "valid" 또는 고유 식별자)
            // 유효시간 설정 필요 (예: 10분)
            tempReqSeqStore.put(reqSeq, "pending_verification"); // value는 상태 등을 나타낼 수 있음

            return ResponseEntity.ok(initData);
        } catch (Exception e) {
            // 로그 기록 및 에러 응답
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * NICE 성공 콜백 (NICE 서버에서 직접 호출)
     * @param encodeData NICE에서 전달하는 암호화된 결과 데이터 (폼 파라미터)
     * @return 프론트엔드 콜백 페이지로 리다이렉션 (결과 조회용 임시 키 포함)
     */
    @RequestMapping(value = "/success", method = {RequestMethod.GET, RequestMethod.POST})
    public RedirectView successCallback(@RequestParam("EncodeData") String encodeData) {
        try {
            // REQ_SEQ 검증은 processSuccessCallback 내부에서 수행되어야 함
            // (plainData에서 reqSeq를 꺼내 tempReqSeqStore와 비교)
            String plainData = decodeDataForReqSeq(encodeData); // 임시: REQ_SEQ만 먼저 꺼내기 위한 로직
            String reqSeqFromNice = parseReqSeq(plainData);

            if (reqSeqFromNice == null || !tempReqSeqStore.containsKey(reqSeqFromNice)) {
                 // 실패 처리 또는 에러 페이지로 리다이렉션
                return new RedirectView(frontendFailUrl + "&error=invalid_session");
            }
            tempReqSeqStore.remove(reqSeqFromNice); // 사용된 REQ_SEQ는 제거 또는 상태 변경


            HashMap<String, Object> result = niceService.processSuccessCallback(encodeData);
            String resultKey = (String) result.get("resultKey");
            Object userData = result.get("userData");

            // 최종 결과 임시 저장 (key: resultKey, value: userData)
            // 유효시간 설정 필요 (예: 5분)
            tempResultStore.put(resultKey, userData);

            return new RedirectView(frontendSuccessUrl + "&key=" + resultKey);
        } catch (Exception e) {
            // 로그 기록
            // 실패 페이지로 리다이렉션하며 에러 코드 전달 가능
            return new RedirectView(frontendFailUrl + "&error=" + e.getClass().getSimpleName());
        }
    }

    /**
     * EncodeData에서 REQ_SEQ만 추출하기 위한 간이 복호화 및 파싱 (실제 서비스에서는 더 견고하게 구현)
     */
    private String decodeDataForReqSeq(String encodeData) {
        CPClient cpClient = new CPClient();
        // 사이트코드, 패스워드는 실제 값으로 NiceService에서 가져오거나 Controller에 주입 필요
        // 여기서는 NiceService의 필드를 직접 접근할 수 없으므로, 임시값을 사용하거나,
        // NiceService에 관련 메소드를 추가해야 함.
        // 이 예제에서는 개념만 설명.
        String siteCode = "YOUR_SITE_CODE"; // 실제 값 사용
        String sitePassword = "YOUR_SITE_PASSWORD"; // 실제 값 사용
        int ret = cpClient.fnDecode(siteCode, sitePassword, encodeData);
        if (ret == 0) {
            return cpClient.getPlainData();
        }
        return null;
    }

    private String parseReqSeq(String plainData) {
        if (plainData == null) return null;
        HashMap map = new CPClient().fnParse(plainData); // CPClient 인스턴스화 필요
        return (String) map.get("REQ_SEQ");
    }


    /**
     * NICE 실패 콜백 (NICE 서버에서 직접 호출)
     * @param encodeData NICE에서 전달하는 암호화된 결과 데이터 (폼 파라미터)
     * @return 프론트엔드 콜백 페이지로 리다이렉션 (에러 정보 및 조회용 임시 키 포함)
     */
    @RequestMapping(value = "/fail", method = {RequestMethod.GET, RequestMethod.POST})
    public RedirectView failCallback(@RequestParam("EncodeData") String encodeData) {
         try {
            // REQ_SEQ 검증
            String plainData = decodeDataForReqSeq(encodeData);
            String reqSeqFromNice = parseReqSeq(plainData);

            if (reqSeqFromNice == null || !tempReqSeqStore.containsKey(reqSeqFromNice)) {
                return new RedirectView(frontendFailUrl + "&error=invalid_session_on_fail");
            }
            tempReqSeqStore.remove(reqSeqFromNice);


            HashMap<String, Object> result = niceService.processFailureCallback(encodeData);
            String resultKey = (String) result.get("resultKey"); // 에러 결과 조회용 키
            Object errorData = result.get("errorData");

            tempResultStore.put(resultKey, errorData); // 에러 정보도 저장

            HashMap<String, String> errorDetails = (HashMap<String, String>) errorData;
            String errorCode = errorDetails.getOrDefault("errorCode", "unknown");

            return new RedirectView(frontendFailUrl + "&errorCode=" + errorCode + "&key=" + resultKey);
        } catch (Exception e) {
            return new RedirectView(frontendFailUrl + "&error=" + e.getClass().getSimpleName());
        }
    }

    /**
     * 프론트엔드에서 최종 본인인증 결과 조회
     * @param resultKey 성공/실패 콜백에서 전달받은 임시 키
     * @return 사용자 정보 또는 에러 정보
     */
    @GetMapping("/result/{resultKey}")
    @ResponseBody
    public ResponseEntity<Object> getVerificationResult(@PathVariable String resultKey) {
        try {
            // Object resultData = niceService.getVerificationResult(resultKey); // Service에 구현된 메소드 호출
            Object resultData = tempResultStore.get(resultKey);
            if (resultData == null) {
                 return ResponseEntity.status(404).body(Map.of("error", "결과를 찾을 수 없거나 만료되었습니다."));
            }
            tempResultStore.remove(resultKey); // 조회 후 즉시 삭제 (일회용 키)
            return ResponseEntity.ok(resultData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
```

> **중요**: 위 `NiceController` 코드는 `REQ_SEQ` 및 최종 결과 임시 저장을 위해 `ConcurrentHashMap`을 사용했습니다. **동시성 문제가 있을 수 있고 서버 재시작 시 데이터가 유실되므로, 실제 운영 환경에서는 반드시 Redis, Memcached 또는 DB와 같은 외부 지속성 저장소를 사용해야 합니다.** `decodeDataForReqSeq`와 `parseReqSeq` 부분은 `NiceService`에서 사이트 코드/패스워드를 가져와 사용하도록 개선하거나, `NiceService`에 해당 기능을 수행하는 public 메소드를 만드는 것이 좋습니다.

### 3.4. 데이터 모델 (DTO) - 필요시 정의

API 응답을 위한 명확한 DTO (Data Transfer Object)를 정의하는 것이 좋습니다. 예를 들어:

- `NiceInitiateResponse.java`: `encodeData`, `reqSeq` 포함
- `NiceResultResponse.java`: 사용자 정보 (`name`, `di`, `ci` 등) 또는 에러 정보 (`errorCode`) 포함

### 3.5. 보안 및 인코딩

- **REQ_SEQ 검증**: `NiceService`의 `processSuccessCallback` 및 `processFailureCallback` 메소드 내에서 `initiateVerification` 시 저장했던 `REQ_SEQ`와 NICE 콜백으로 받은 `REQ_SEQ`가 일치하는지 반드시 검증합니다. 이는 CSRF 공격 및 데이터 위변조를 방지하는 데 중요합니다.
- **HTTPS**: 모든 통신 구간(클라이언트-백엔드, 백엔드-NICE)은 HTTPS를 사용해야 합니다.
- **문자 인코딩**:
  - NICE 개발 가이드에 따르면 이름(`NAME`)은 EUC-KR로, `UTF8_NAME`은 UTF-8(URL 인코딩됨)로 제공됩니다. `UTF8_NAME` 사용 시 `java.net.URLDecoder.decode(name, "UTF-8")`을 사용합니다.
  - `CPClient` 라이브러리 자체의 인코딩 처리 방식을 확인하고, 필요시 Spring Boot 환경에서 인코딩 필터 등을 알맞게 설정합니다. JSP 샘플의 `<%@ page contentType="text/html;charset=euc-kr" %>` 부분을 참고합니다.

## 4. 프론트엔드 구현 가이드 (Next.js)

### 4.1. 본인인증 시작 (예: `NiceAuthButton.tsx`)

```typescript jsx
// components/NiceAuthButton.tsx
import React, { useState } from "react";

const NiceAuthButton = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleNiceAuth = async () => {
    setIsLoading(true);
    setError(null);
    try {
      // 1. 백엔드 API 호출하여 EncodeData 요청
      const response = await fetch("/api/v1/nice/checkplus/initiate", {
        // Spring Boot API 엔드포인트
        method: "POST",
        // 필요시 헤더 추가 (CSRF 토큰 등)
      });

      if (!response.ok) {
        const errData = await response.json();
        throw new Error(errData.error || "본인인증 초기화에 실패했습니다.");
      }

      const data = await response.json();
      const encodeData = data.encodeData;
      // const reqSeq = data.reqSeq; // 백엔드가 reqSeq를 반환한다면, 디버깅이나 로깅에 사용 가능

      // 2. NICE 본인인증 팝업 실행
      const popupName = "popupChk";
      const width = 500;
      const height = 550;
      const left = (window.screen.width - width) / 2;
      const top = (window.screen.height - height) / 2;

      // window.name을 설정해야 NICE 팝업이 제대로 동작할 수 있음 (NICE 가이드 참조)
      window.name = "Parent_window";

      window.open(
        "",
        popupName,
        `width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes`
      );

      const form = document.createElement("form");
      form.setAttribute("method", "POST");
      form.setAttribute(
        "action",
        "https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb"
      ); // NICE 서버 URL
      form.setAttribute("target", popupName); // 팝업창 이름

      const hiddenFieldM = document.createElement("input");
      hiddenFieldM.setAttribute("type", "hidden");
      hiddenFieldM.setAttribute("name", "m");
      hiddenFieldM.setAttribute("value", "checkplusService"); // 고정값
      form.appendChild(hiddenFieldM);

      const hiddenFieldEncode = document.createElement("input");
      hiddenFieldEncode.setAttribute("type", "hidden");
      hiddenFieldEncode.setAttribute("name", "EncodeData");
      hiddenFieldEncode.setAttribute("value", encodeData);
      form.appendChild(hiddenFieldEncode);

      document.body.appendChild(form);
      form.submit();
      document.body.removeChild(form);
    } catch (err: any) {
      setError(err.message || "알 수 없는 에러가 발생했습니다.");
      console.error("NICE Auth Error:", err);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div>
      <button onClick={handleNiceAuth} disabled={isLoading}>
        {isLoading ? "처리 중..." : "NICE 본인인증"}
      </button>
      {error && <p style={{ color: "red" }}>에러: {error}</p>}
    </div>
  );
};

export default NiceAuthButton;
```

### 4.2. 콜백 처리 페이지 (예: `pages/auth/nice/callback.tsx`)

이 페이지는 NICE 인증 후 Spring Boot 백엔드 API에 의해 리다이렉션되는 페이지입니다.
(Spring Boot 설정의 `frontend-redirect-success-url`, `frontend-redirect-fail-url` 에 해당)

```typescript jsx
// pages/auth/nice/callback.tsx
import React, { useEffect, useState } from "react";
import { useRouter } from "next/router";

interface UserInfo {
  name?: string;
  di?: string;
  ci?: string;
  birthDate?: string;
  gender?: string;
  // 기타 필요한 정보
}

interface ErrorInfo {
  errorCode?: string;
  authType?: string;
  reqSeq?: string;
  // 기타 에러 정보
}

const NiceCallbackPage = () => {
  const router = useRouter();
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const [errorInfo, setErrorInfo] = useState<ErrorInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState<string>(
    "본인인증 결과를 처리 중입니다..."
  );

  useEffect(() => {
    const { status, key, errorCode } = router.query; // Spring Boot API가 리다이렉션 시 전달한 파라미터

    if (!key && status !== "success" && status !== "fail") {
      // key나 status가 없는 비정상 접근
      setIsLoading(false);
      setMessage("잘못된 접근입니다.");
      // Optional: 팝업을 직접 닫거나 부모 창으로 메시지 전달
      // if (window.opener) {
      //     window.opener.postMessage({ type: 'NICE_AUTH_INVALID_CALLBACK' }, '*');
      //     window.close();
      // }
      return;
    }

    const fetchResult = async (resultKey: string) => {
      try {
        const res = await fetch(`/api/v1/nice/checkplus/result/${resultKey}`); // Spring Boot API 엔드포인트
        if (!res.ok) {
          const errData = await res.json();
          throw new Error(
            errData.error || `결과 조회 실패 (상태: ${res.status})`
          );
        }
        const resultData = await res.json();

        if (status === "success") {
          setUserInfo(resultData);
          setMessage("본인인증에 성공했습니다.");
          // 성공 시 부모창에 메시지 전달 및 팝업 닫기
          if (window.opener) {
            window.opener.postMessage(
              { type: "NICE_AUTH_SUCCESS", payload: resultData },
              "*"
            ); // targetOrigin을 명시하는 것이 더 안전
            // window.close();
          }
        } else if (status === "fail") {
          setErrorInfo(resultData); // resultData가 에러 객체일 경우
          setMessage(
            `본인인증에 실패했습니다. (에러코드: ${
              resultData.errorCode || errorCode || "N/A"
            })`
          );
          if (window.opener) {
            window.opener.postMessage(
              { type: "NICE_AUTH_FAIL", payload: resultData },
              "*"
            );
            // window.close();
          }
        }
      } catch (err: any) {
        console.error("NICE Result Fetch Error:", err);
        setMessage(`결과 처리 중 에러: ${err.message}`);
        if (window.opener) {
          window.opener.postMessage(
            { type: "NICE_AUTH_ERROR", payload: { message: err.message } },
            "*"
          );
          // window.close();
        }
      } finally {
        setIsLoading(false);
        // 여기서 팝업을 닫을 수 있습니다.
        // setTimeout(() => window.close(), 3000); // 메시지 확인 후 자동 닫기
      }
    };

    if (typeof key === "string") {
      fetchResult(key);
    } else if (status === "fail" && typeof errorCode === "string") {
      // key 없이 errorCode만 넘어온 경우 (백엔드에서 resultKey를 안만들었을 때)
      setErrorInfo({ errorCode });
      setMessage(`본인인증에 실패했습니다. (에러코드: ${errorCode})`);
      setIsLoading(false);
      if (window.opener) {
        window.opener.postMessage(
          { type: "NICE_AUTH_FAIL", payload: { errorCode } },
          "*"
        );
        // window.close();
      }
    } else {
      // status는 있지만 key가 없는 경우 등 예외 처리
      setMessage("필수 정보가 누락되어 결과를 처리할 수 없습니다.");
      setIsLoading(false);
    }
  }, [router.query]);

  // 부모 창과 통신 설정 (window.postMessage)
  // 예: 부모창에서 이벤트 리스너 설정
  // window.addEventListener('message', (event) => {
  //   if (event.origin !== 'your-frontend-domain.com') return; // 보안: origin 체크
  //   if (event.data.type === 'NICE_AUTH_SUCCESS') {
  //     console.log('본인인증 성공 데이터:', event.data.payload);
  //     // 로그인 처리, 사용자 정보 업데이트 등
  //   } else if (event.data.type === 'NICE_AUTH_FAIL') {
  //     console.error('본인인증 실패 데이터:', event.data.payload);
  //   }
  // });

  if (isLoading) {
    return (
      <div>
        <p>{message}</p>
      </div>
    );
  }

  return (
    <div>
      <h1>본인인증 결과</h1>
      <p>{message}</p>
      {userInfo && (
        <div>
          <h2>사용자 정보 (성공)</h2>
          <pre>{JSON.stringify(userInfo, null, 2)}</pre>
        </div>
      )}
      {errorInfo && (
        <div>
          <h2>에러 정보 (실패)</h2>
          <pre>{JSON.stringify(errorInfo, null, 2)}</pre>
        </div>
      )}
      <button onClick={() => window.close()}>팝업 닫기</button>
    </div>
  );
};

export default NiceCallbackPage;
```

> **프론트엔드 주의사항**:
>
> - `window.opener.postMessage`를 사용하여 팝업창에서 부모창으로 인증 결과를 전달할 수 있습니다. 이 때 `targetOrigin`을 명시하여 보안을 강화하는 것이 좋습니다.
> - 부모창에서는 `window.addEventListener('message', callback)`을 사용하여 팝업으로부터 메시지를 수신하고 처리합니다.
> - NICE 팝업창은 `window.name = "Parent_window"` 설정이 필요할 수 있습니다 (NICE 가이드 참조).
> - 실제 서비스에서는 사용자 경험을 위해 로딩 상태, 에러 메시지 등을 더 친절하게 표시해야 합니다.

### 4.3. 상태 관리

인증 성공 시 받은 사용자 정보(DI, CI, 이름 등)는 React Context, Redux, Zustand 등 애플리케이션의 상태 관리 라이브러리를 통해 안전하게 관리하고 필요한 컴포넌트에서 사용합니다.

## 5. 전체 연동 흐름도 (요약)

1.  **클라이언트 (Next.js)**: `본인인증` 버튼 클릭
2.  **클라이언트 → 서버 (Spring Boot)**: `POST /api/v1/cms/nice/checkplus/initiate`
    - 서버: `REQ_SEQ` 생성 및 임시 저장, `EncodeData` 생성 후 클라이언트에 반환.
3.  **클라이언트**: `EncodeData`로 NICE 팝업 실행.
4.  **사용자**: NICE 팝업에서 인증 진행.
5.  **NICE → 서버**: `GET /api/v1/cms/nice/checkplus/success` 또는 `/fail` (암호화된 결과와 함께).
    - 서버: 데이터 복호화, `REQ_SEQ` 검증, 결과 임시 저장 (새 임시 키 사용).
    - 서버: 클라이언트의 특정 콜백 URL로 리다이렉트 (`?key=새임시키&status=...`).
6.  **클라이언트 (리다이렉션된 페이지)**: URL 파라미터에서 `key` 추출.
7.  **클라이언트 → 서버**: `GET /api/v1/nice/checkplus/result/{key}`
    - 서버: 임시 저장된 결과 조회 후 반환, 저장된 결과 삭제.
8.  **클라이언트**: 결과 수신 후 화면 표시 또는 후속 처리.

## 6. 주요 반환 데이터 항목 (NICE 개발 가이드 V3.2 기준)

인증 성공 시 `NiceService`의 `processSuccessCallback`에서 파싱하여 얻을 수 있는 주요 정보는 다음과 같습니다. (키 값은 NICE 가이드 참조)

- `REQ_SEQ`: CP 요청번호 (main 페이지에서 설정한 값과 동일해야 함)
- `RES_SEQ`: 처리결과 고유번호 (NICE에서 생성)
- `AUTH_TYPE`: 인증 수단 (M: 휴대폰, X: 인증서(공통), S: PASS인증서, U: 공동인증서)
- `NAME`: 이름 (EUC-KR)
- `UTF8_NAME`: 이름 (UTF-8, URLDecode 필요)
- `BIRTHDATE`: 생년월일 (YYYYMMDD)
- `GENDER`: 성별 코드 (0: 여성, 1: 남성)
- `NATIONALINFO`: 내/외국인 코드 (0: 내국인, 1: 외국인)
- `DI` (Duplicate Info): 중복가입 확인정보 (64Byte)
- `CI` (Connecting Information): 연계정보 (88Byte)
- `MOBILE_CO`: 통신사 정보 (핸드폰 인증 시)
- `MOBILE_NO`: 휴대폰 번호 (핸드폰 인증 시)

인증 실패 시 `processFailureCallback`에서 얻을 수 있는 주요 정보:

- `REQ_SEQ`
- `ERR_CODE`: 에러 코드 (NICE 응답코드 문서 참조)
- `AUTH_TYPE`

## 7. 주의사항 및 트러블슈팅

- **`REQ_SEQ` 검증의 중요성**: 모든 NICE 콜백 처리 시 `REQ_SEQ`를 반드시 검증하여 데이터 위변조 및 CSRF 공격을 방지해야 합니다. 임시 저장소(Redis 등)에 저장된 `REQ_SEQ`와 비교하고, 사용 후에는 즉시 삭제하거나 만료 처리합니다.
- **사이트 코드/패스워드 보안**: 절대로 클라이언트 측 코드에 하드코딩하거나 노출하지 마십시오. 백엔드 서버의 안전한 곳에 보관합니다.
- **HTTPS 필수**: 모든 통신 구간에서 HTTPS를 사용하십시오.
- **팝업 차단**: 브라우저의 팝업 차단 기능으로 인해 NICE 인증창이 열리지 않을 수 있습니다. 사용자에게 팝업 허용 안내가 필요할 수 있습니다.
- **문자 인코딩**: 이름 등 한글 데이터 처리 시 EUC-KR, UTF-8 인코딩을 주의 깊게 처리해야 합니다. `CPClient` 라이브러리의 동작 특성 및 Spring Boot의 기본 인코딩 설정을 확인하십시오.
- **NICE 서버 점검**: NICE 서버 점검 시간에는 서비스 이용이 불가할 수 있습니다.
- **로그 기록**: 백엔드 API에서 각 단계별 주요 정보 및 에러 발생 시 상세 로그를 기록하여 문제 발생 시 원인 파악을 용이하게 합니다.
- **NICE 개발 가이드 숙지**: NICE평가정보에서 제공하는 최신 버전의 "안심본인인증 서비스 개발 가이드"를 반드시 숙지하고, 변경사항이 있는지 주기적으로 확인합니다.
- **테스트**: 개발계(테스트용 사이트 코드/패스워드 사용)와 운영계 환경을 분리하여 충분히 테스트합니다.

---

본 가이드가 NICE 안심본인인증 연동 개발에 도움이 되기를 바랍니다.

## 8. 본인확인서비스 이용기관 취약점 자체점검 가이드 준수 확인

본 섹션은 "본인확인서비스 이용기관 취약점 자체점검 체크리스트" 항목들을 기반으로, 본 NICE 연동 가이드가 해당 보안 요구사항들을 어떻게 충족하는지 또는 어떻게 충족해야 하는지를 안내합니다. 실제 서비스 구현 시 각 항목을 면밀히 검토하고 필요한 조치를 취해야 합니다.

| 순번 | 웹사이트 본인확인 취약점 자체점검 항목                                                                                                                                                         | 본 가이드의 관련 내용 및 권장 조치 (Y/N/해당없음)                                                                                                                                                                                                                                                                                                 | 검토 여부 |
| :--: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :-------: |
|  1   | (불필요한 중요정보 평문 노출) 본인확인 이후 회원가입 단계에서 이용자에게 불필요한 개인정보(CI/DI)가 평문으로 드러나지 않도록 조치하였는가?                                                     | **Y**: 가이드에서는 CI/DI를 백엔드에서 처리하고, 프론트엔드에는 필요한 정보만 전달하거나, 회원가입 완료 후에는 직접 노출하지 않도록 권장합니다. (4.2. 콜백 처리 페이지, 4.3. 상태 관리 참조) 사용자에게 CI/DI 값을 직접 보여줄 필요는 없습니다.                                                                                                   | ☐ Y / ☐ N |
|  2   | (파라미터 변조) 본인확인 이후 회원가입 과정에서 이용자가 입력한 데이터를 서버 또는 Web to Web으로 전송할 때, 본인확인 결과정보(이름, 생년월일 등)를 다른 정보로 변조할 수 없도록 조치하였는가? | **Y**: 본인확인 결과정보는 NICE 서버로부터 백엔드 API가 직접 수신하고, 이 정보를 기준으로 사용자 입력값을 검증하거나 처리합니다. 프론트엔드에서 임의로 본인확인 결과정보를 변조하여 서버로 전송하는 것을 허용하지 않습니다. (3.3. NiceController - /success, /fail 콜백 처리, 5. 전체 연동 흐름도 참조)                                           | ☐ Y / ☐ N |
|  3   | (입력정보 일치여부) 본인확인기관(또는 대행사)으로부터 수신한 결과정보를 복호화한 값과 이용자가 입력한 값(회원가입 등) 간 일치 여부를 검증하였는가?                                             | **Y**: 회원가입 시 사용자가 입력한 정보 (예: 이름, 생년월일)와 NICE를 통해 인증된 정보를 백엔드에서 비교 검증하는 로직을 구현해야 합니다. 본 가이드는 NICE 결과 수신까지를 다루며, 이후 비즈니스 로직에서 이 검증이 포함되어야 합니다.                                                                                                            | ☐ Y / ☐ N |
|  4   | (복합인증 교차검증) 신원확인 단계(실명확인, 본인확인, 계좌점유, 신분증 진위여부 등)에서 각각의 입력·응답 값의 일치 여부를 검증하였는가?                                                        | **해당없음/부분적 Y**: 본 가이드는 NICE 안심본인인증 연동을 주로 다룹니다. 만약 서비스가 여러 신원확인 단계를 복합적으로 사용한다면, 각 단계에서 얻은 정보들을 교차 검증하는 로직을 백엔드에 별도로 구현해야 합니다. NICE 인증 결과 자체는 신뢰할 수 있는 정보로 간주됩니다.                                                                      | ☐ Y / ☐ N |
|  5   | (인증결과 우회) 본인확인 결과를 처리하는 과정에서 인증 실패시, 실패코드를 변조 또는 우회할 수 없도록 조치하였는가?                                                                             | **Y**: 인증 실패 시 NICE는 에러코드를 포함한 암호화된 데이터를 백엔드 콜백으로 전달합니다. 백엔드는 이 데이터를 복호화하여 실패를 인지하고, 프론트엔드에 안전한 방식으로 (예: 임시 키를 통한 결과 조회) 실패 상태를 전달합니다. 프론트엔드에서 임의로 성공으로 위장할 수 없습니다. (3.3. NiceController - /fail 콜백 처리, /result 조회)          | ☐ Y / ☐ N |
|  6   | (데이터 재사용) 동일 웹사이트에서 과거에 수집된 인증정보(암호화 데이터, 거래번호, 토큰, 세션 등)를 재사용하지 못하도록 조치하였는가?                                                           | **Y**: `REQ_SEQ`는 매 인증 시도마다 NICE 라이브러리(`niceCheck.getRequestNO(siteCode)`)를 통해 새롭게 생성되며, 백엔드는 이 `REQ_SEQ`를 콜백 시 검증하고 한 번 사용된 `REQ_SEQ` 또는 결과 조회용 임시 키는 즉시 만료/삭제 처리합니다. (3.2. NiceService - initiateVerification, 3.3. NiceController - REQ_SEQ 및 resultKey 관리 참조)             | ☐ Y / ☐ N |
|  7   | (암호키 노출) 본인확인서비스 테스트를 위한 샘플페이지 내 인증모듈 복호화 키와 실제 키가 동일하지 않도록 설정하고 해당 암호키가 노출되지 않도록 조치하였는가?                                   | **Y**: 사이트 코드 및 패스워드 (암복호화 키로 사용됨)는 백엔드 서버의 안전한 설정 파일 또는 환경 변수에 저장되며, 클라이언트나 샘플 페이지에 직접 노출되지 않습니다. 개발/테스트 환경과 운영 환경의 키는 분리하여 관리해야 합니다. (2.1. NICE평가정보 계약 정보, 3.1. 설정 참조)                                                                  | ☐ Y / ☐ N |
|  8   | (데이터 위·변조) 본인확인 결과정보가 웹사이트가 수신하는 과정에서 데이터를 위·변조 할 수 없도록 조치하였는가?                                                                                  | **Y**: NICE에서 제공하는 결과 데이터는 암호화되어 백엔드 API로 전달됩니다. 백엔드는 `CPClient` 라이브러리를 사용하여 이를 복호화하므로, 중간에서 데이터가 위변조될 경우 복호화에 실패하거나, 내부 해시값 검증(라이브러리 내장)에 실패할 수 있습니다. 또한 `REQ_SEQ` 검증을 통해 요청의 무결성을 확인합니다. (3.2. NiceService - 복호화 로직 참조) | ☐ Y / ☐ N |
|  9   | (관리자 페이지 노출) 유추하기 쉬운 URL로 관리자 페이지가 노출되지 않도록 조치하였는가?                                                                                                         | **해당없음/권장**: 본 가이드는 NICE 연동 자체를 다루지만, 일반적인 웹 보안 권장 사항으로 관리자 페이지는 예측하기 어려운 URL을 사용하고, 접근 제어(IP 제한, 별도 인증 등)를 강력하게 설정해야 합니다.                                                                                                                                             | ☐ Y / ☐ N |
|  10  | (전송구간 암호화) 서버와 클라이언트 간 통신 시 전송구간을 암호화하였는가?                                                                                                                      | **Y (강력 권장)**: 모든 통신 구간 (클라이언트<->백엔드 API, 백엔드 API<->NICE 서버)은 HTTPS를 사용해야 합니다. 본 가이드의 코드 예제 자체는 프로토콜을 명시하지 않으나, 운영 환경에서는 필수입니다. (3.5. 보안 및 인코딩 - HTTPS 참조)                                                                                                            | ☐ Y / ☐ N |
|  11  | (프로세스 검증 누락) 인증이 필요한 웹 사이트의 중요 페이지(관리자 페이지, 회원정보 변경 페이지 등)에 대한 접근통제를 수행하고 있는가?                                                          | **해당없음/권장**: NICE 본인인증은 주로 회원가입이나 비밀번호 찾기 등 특정 시점에 사용됩니다. 인증 이후 사용자가 접근하는 중요 페이지들에 대해서는 Spring Security 등 프레임워크의 인증/인가 기능을 사용하여 강력한 접근 통제를 별도로 구현해야 합니다.                                                                                           | ☐ Y / ☐ N |

**작성자 정보 (예시)**

- 기관명: (회사명)
- 담당자: (이름)
- 연락처: (전화번호)
- 이메일: (이메일 주소)
- 작성일자: (YYYY.MM.DD)
