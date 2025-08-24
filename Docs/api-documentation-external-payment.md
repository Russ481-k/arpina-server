# **외부 연동 API 명세서 - 결제 데이터 조회**

## 1. API 개요

본 문서는 귀사 시스템과의 연동을 위한 **결제 데이터 조회 API**의 명세와 사용법을 안내합니다.

- **주요 기능:** 특정 기간 내에 발생한 결제 데이터를 관련 신청 정보 및 사용자 정보와 함께 조회합니다.
- **Endpoint:** `GET /api/v1/external/payment-data`
- **반환 형식:** `JSON`

---

## 2. 인증 방식

본 API는 허가된 파트너사만 호출할 수 있도록 **API 키**와 **IP 화이트리스트** 방식을 사용한 이중 인증을 적용합니다.

- **API Key 인증**

  - 사전에 발급된 고유 API 키를 모든 요청의 HTTP 헤더에 포함해야 합니다.
  - **Header Name:** `X-API-KEY`

- **IP 화이트리스트 (IP Whitelisting)**
  - 귀사에서 API를 호출할 서버의 공인 IP 주소를 사전에 저희에게 전달해야 합니다.
  - 등록되지 않은 IP 주소에서의 API 호출은 자동으로 차단됩니다.

## 3. 요청 명세

### 가. 기본 정보

- **HTTP Method:** `GET`
- **Endpoint:** `https://help.handylab.co.kr/api/v1/external/payment-data`

### 나. 헤더 (Headers)

| Key         | Type   | 필수   | 설명                  |
| :---------- | :----- | :----- | :-------------------- |
| `X-API-KEY` | String | **예** | 발급받은 고유 API 키. |
| `Accept`    | String | 권장   | `application/json`    |

### 다. 쿼리 파라미터 (Query Parameters)

| Parameter   | Type     | 필수   | 설명                                                          | 형식                  |
| :---------- | :------- | :----- | :------------------------------------------------------------ | :-------------------- |
| `startDate` | `String` | **예** | 조회할 기간의 시작 일시. 해당 시점을 포함합니다.              | `YYYY-MM-DDTHH:mm:ss` |
| `endDate`   | `String` | **예** | 조회할 기간의 종료 일시. 해당 시점까지의 데이터를 포함합니다. | `YYYY-MM-DDTHH:mm:ss` |

**요청 예시 URL:**

```
https://help.handylab.co.kr/api/v1/external/payment-data?startDate=2023-10-01T00:00:00&endDate=2023-10-31T23:59:59
```

---

## 4. 응답 명세

### 가. 성공 (Status Code: 200 OK)

요청이 성공하면, 지정된 기간 내의 결제 데이터 목록이 JSON 형식으로 반환됩니다.

**응답 본문 예시:**

```json
{
  "data": [
    {
      "paymentId": 101,
      "moid": "enroll_20231027_12345",
      "tid": "T1234567890ABC",
      "status": "PAID",
      "paidAmount": 75000,
      "paidAt": "2023-10-27T10:00:00",
      "payMethod": "CARD",
      "exportStatus": 1,
      "enrollmentInfo": {
        "enrollId": 54321,
        "status": "PAID",
        "lessonTitle": "초급 수영 마스터반 (성인)",
        "applicationDate": "2023-10-27T09:55:12"
      },
      "userInfo": {
        "uuid": "a1b2c3d4-e5f6-7890-g7h8-i9j0k1l2m3n4",
        "name": "홍길동",
        "email": "gildong.hong@example.com",
        "phone": "010-1234-5678",
        "gender": "0"
      }
    },
    {
      "paymentId": 102,
      "moid": "enroll_20231028_67890",
      "tid": "T0987654321XYZ",
      "status": "PAID",
      "paidAmount": 75000,
      "paidAt": "2023-10-28T14:30:00",
      "payMethod": "VBANK",
      "enrollmentInfo": {
        "enrollId": 54322,
        "status": "PAID",
        "lessonTitle": "초급 수영 마스터반 (성인)",
        "applicationDate": "2023-10-28T14:25:05"
      },
      "userInfo": {
        "uuid": "b2c3d4e5-f6g7-8901-h8i9-j0k1l2m3n4o5",
        "name": "김영희",
        "email": "younghee.kim@example.com",
        "phone": "010-9876-5432",
        "gender": "1"
      }
    }
  ]
}
```

### 나. 응답 필드 상세 설명

| 경로                    | 필드명            | 타입     | 설명                                                                   |
| :---------------------- | :---------------- | :------- | :--------------------------------------------------------------------- |
| `data`                  | -                 | Array    | 결제 데이터 객체의 배열.                                               |
| `data[]`                | -                 | Object   | 단일 결제 정보를 담는 객체.                                            |
| `data[].paymentId`      | `paymentId`       | Long     | 결제 고유 ID.                                                          |
| `data[].moid`           | `moid`            | String   | 주문 번호.                                                             |
| `data[].tid`            | `tid`             | String   | PG사 거래 ID.                                                          |
| `data[].status`         | `status`          | String   | 결제 상태 (예: `PAID`, `CANCELED`).                                    |
| `data[].paidAmount`     | `paidAmount`      | Integer  | 결제된 총 금액 (원).                                                   |
| `data[].paidAt`         | `paidAt`          | Datetime | 결제 완료 일시 (ISO 8601 형식).                                        |
| `data[].payMethod`      | `payMethod`       | String   | 결제 수단 (예: `CARD`, `VBANK`).                                       |
| `data[].exportStatus`   | `exportStatus`    | Integer  | 데이터 조회 상태 (0: 미조회, 1: 조회됨).                               |
| `data[].enrollmentInfo` | -                 | Object   | 해당 결제와 연관된 신청 정보.                                          |
| `...enrollId`           | `enrollId`        | Long     | 신청 고유 ID.                                                          |
| `...status`             | `status`          | String   | 신청의 결제 상태 (예: `PAID`, `UNPAID`).                               |
| `...lessonTitle`        | `lessonTitle`     | String   | 신청한 강습의 이름.                                                    |
| `...applicationDate`    | `applicationDate` | Datetime | 신청 일시 (ISO 8601 형식).                                             |
| `data[].userInfo`       | -                 | Object   | 해당 결제를 진행한 사용자 정보.                                        |
| `...uuid`               | `uuid`            | String   | 사용자 고유 UUID.                                                      |
| `...name`               | `name`            | String   | 사용자 이름.                                                           |
| `...email`              | `email`           | String   | 사용자 이메일 주소.                                                    |
| `...phone`              | `phone`           | String   | 사용자 연락처.                                                         |
| `...gender`             | `gender`          | String   | 사용자 성별. 현재 값은 NICE 기준(`0`: 여성, `1`: 남성)으로 제공됩니다. |

### 다. 실패 응답

| Status Code                 | 원인                                       | 응답 본문 예시                                                                                         |
| :-------------------------- | :----------------------------------------- | :----------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | 필수 파라미터 누락 또는 형식 오류          | `{"timestamp": "...", "status": 400, "error": "Bad Request", "message": "Required...", "path": "..."}` |
| `401 Unauthorized`          | API 키가 없거나 유효하지 않음              | `Unauthorized: Invalid API Key`                                                                        |
| `403 Forbidden`             | 허용되지 않은 IP 주소에서 접근             | `Forbidden: IP not allowed`                                                                            |
| `404 Not Found`             | 경로 불일치(프록시가 프리픽스를 변경/제거) | 올바른 엔드포인트: `/api/v1/external/payment-data`                                                     |
| `500 Internal Server Error` | 서버 내부 오류                             | `{ "timestamp": "...", "status": 500, "error": "Internal Server Error", "errorCode": "CM_0001", ... }` |

---

## 5. API 호출 예시 코드

### 가. Java (Java 11+ HttpClient)

```java
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {
    // 설정값
    private static final String API_BASE_URL = "https://help.handylab.co.kr";
    private static final String API_KEY = "여기에_발급받은_API_KEY를_입력하세요";

    public static void main(String[] args) {
        // 조회할 기간 설정 (시간 포함)
        String startDate = "2023-10-01T00:00:00";
        String endDate = "2023-10-31T23:59:59";

        try {
            String responseBody = getPaymentData(startDate, endDate);
            System.out.println("API 응답:");
            System.out.println(responseBody);
        } catch (IOException | InterruptedException e) {
            System.err.println("API 호출 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getPaymentData(String startDate, String endDate) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String endpoint = String.format("/api/v1/external/payment-data?startDate=%s&endDate=%s", startDate, endDate);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + endpoint))
                .header("X-API-KEY", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API 요청 실패: " + response.statusCode() + " " + response.body());
        }

        return response.body();
    }
}
```

---

## 6. 동작/비즈니스 규칙

- **데이터 범위**: `startDate` ≤ `paidAt` ≤ `endDate`인 결제 데이터가 반환됩니다.
- **exportStatus 동작**: 각 결제 레코드의 `exportStatus`는 최초 조회 시 0→1로 갱신되며, 이후 재조회 시 1로 유지됩니다.
- **응답 정렬**: 응답 순서는 보장되지 않습니다. 필요한 경우 클라이언트에서 `paidAt`으로 정렬하세요.

## 7. 테스트/트러블슈팅 가이드

- 401(Unauthorized): `X-API-KEY` 누락/오타/만료 여부를 확인하세요.
- 403(Forbidden): 호출 출발지 공인 IP가 화이트리스트(`EXTERNAL_API_WHITELIST_IPS`)에 등록되어야 합니다.
- 404(Not Found):
  - 엔드포인트 오타 여부 확인: `/api/v1/external/payment-data` 사용
  - 프록시가 `/api/v1` 프리픽스를 제거/변경하지 않는지 확인(Nginx 설정 참고)
- 500(Internal Server Error): 일시적 장애 또는 예외. 동일 파라미터로 재시도 후 계속 발생 시 응답 본문과 함께 문의 바랍니다.

### Postman 사용 시 주의

- 하단 상태바에서 반드시 **Desktop Agent**로 전환해 호출하세요. Cloud Agent는 출발지 IP가 달라 화이트리스트에 걸릴 수 있습니다.
- Settings → Proxy에서 **Use System Proxy / Global Proxy**를 비활성화하세요.
- Postman Console(⌘/Ctrl+Alt+C)을 열어 Raw Request에 다음이 정확히 찍히는지 확인하세요:
  - 헤더: `X-API-KEY: <발급키>`
  - URL: `https://help.handylab.co.kr/api/v1/external/payment-data?startDate=...&endDate=...`

## 8. 보안 권고

- API 키는 외부에 노출하지 마세요(형상관리, 클라이언트 코드 등).
- 주기적인 키 로테이션을 권장합니다.
- 필요한 최소 IP만 화이트리스트에 등록하세요.

### 나. JavaScript (Node.js + axios)

```javascript
const axios = require("axios");

// 설정값
const API_BASE_URL = "https://help.handylab.co.kr";
const API_KEY = "여기에_발급받은_API_KEY를_입력하세요";

async function getPaymentData(startDate, endDate) {
  const endpoint = "/api/v1/external/payment-data";
  const url = `${API_BASE_URL}${endpoint}`;

  try {
    const response = await axios.get(url, {
      headers: {
        "X-API-KEY": API_KEY,
        Accept: "application/json",
      },
      params: {
        startDate: startDate,
        endDate: endDate,
      },
      timeout: 10000, // 10초 타임아웃
    });

    if (response.status === 200) {
      return response.data;
    }
  } catch (error) {
    console.error("API 호출 중 오류 발생:");
    if (error.response) {
      // 서버가 상태 코드로 응답 (2xx 범위 외)
      console.error("Status:", error.response.status);
      console.error("Data:", error.response.data);
    } else if (error.request) {
      // 요청은 성공했으나 응답을 받지 못함
      console.error("Request:", error.request);
    } else {
      // 요청 설정 중 오류 발생
      console.error("Error Message:", error.message);
    }
    throw error;
  }
}

// API 호출 실행
(async () => {
  // 조회할 기간 설정 (시간 포함)
  const startDate = "2023-10-01T00:00:00";
  const endDate = "2023-10-31T23:59:59";

  try {
    const data = await getPaymentData(startDate, endDate);
    console.log("API 응답:");
    console.log(JSON.stringify(data, null, 2));
  } catch (e) {
    // 에러 처리
  }
})();
```

### 다. Python (requests 라이브러리)

```python
import requests
import json

# 설정값
API_BASE_URL = "https://help.handylab.co.kr"
API_KEY = "여기에_발급받은_API_KEY를_입력하세요"

def get_payment_data(start_date, end_date):
    """지정된 기간의 결제 데이터를 조회합니다."""

    endpoint = "/api/v1/external/payment-data"
    url = f"{API_BASE_URL}{endpoint}"

    headers = {
        "X-API-KEY": API_KEY,
        "Accept": "application/json"
    }

    params = {
        "startDate": start_date,
        "endDate": end_date
    }

    try:
        response = requests.get(url, headers=headers, params=params, timeout=10)

        # 요청이 성공했는지 확인 (200 OK)
        response.raise_for_status()

        return response.json()

    except requests.exceptions.HTTPError as http_err:
        print(f"HTTP 오류 발생: {http_err}")
        print(f"응답 내용: {response.text}")
    except requests.exceptions.RequestException as req_err:
        print(f"API 요청 오류 발생: {req_err}")

    return None

if __name__ == "__main__":
    # 조회할 기간 설정 (시간 포함)
    start_date = "2023-10-01T00:00:00"
    end_date = "2023-10-31T23:59:59"

    payment_data = get_payment_data(start_date, end_date)

    if payment_data:
        print("API 응답:")
        print(json.dumps(payment_data, indent=2, ensure_ascii=False))

```
