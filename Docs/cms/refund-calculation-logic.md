# 환불 금액 계산 로직 수정 문서

## 📋 개요

이 문서는 CMS 시스템의 수강신청 환불 금액 계산 로직의 문제점과 수정 사항을 상세히 설명합니다.

**수정 일자**: 2025년 1월  
**수정 파일**: `src/main/java/cms/enroll/service/impl/EnrollmentServiceImpl.java`  
**관련 API**: `POST /api/v1/cms/enrollments/{enrollId}/calculate-refund-preview`

---

## 🔍 기존 문제점

### 1. **Payment 테이블 의존성 문제**

- 환불 계산이 `Payment` 테이블의 `lessonAmount`, `lockerAmount` 필드에 의존
- 기존 데이터에서 해당 필드들이 `null` 또는 `0`으로 저장되어 있음
- 결과적으로 `paidLessonAmount = 0`이 되어 사용일수와 관계없이 차감액이 0원이 됨

### 2. **데이터 일관성 문제**

```java
// 기존 로직 (문제점)
BigDecimal paidLessonAmount = BigDecimal.valueOf(
    payment.getLessonAmount() != null ? payment.getLessonAmount() : 0
);
// lessonAmount가 null이면 0이 되어 환불 계산 실패
```

### 3. **환불 계산 실패 사례**

```json
// API 요청
{"manualUsedDays": 3}

// 기존 잘못된 응답
{
    "paidLessonAmount": 0,
    "lessonUsageDeduction": 0,  // 차감액이 0원
    "finalRefundAmount": 110000  // 전액 환불 (잘못됨)
}
```

---

## ✅ 수정 내용

### 1. **동적 금액 분리 계산**

데이터베이스 필드에 의존하지 않고 총 결제금액에서 동적으로 강습료와 사물함료를 분리:

```java
// 수정된 로직
BigDecimal totalPaidAmount = BigDecimal.valueOf(payment.getPaidAmt());

if (enroll.isUsesLocker()) {
    paidLockerAmount = BigDecimal.valueOf(lockerFeeConfig);  // 5,000원
    paidLessonAmount = totalPaidAmount.subtract(paidLockerAmount);
} else {
    paidLockerAmount = BigDecimal.ZERO;
    paidLessonAmount = totalPaidAmount;
}
```

### 2. **고정 일일 요금 적용**

기존의 비례 계산 방식을 고정 일일 요금 방식으로 변경:

```java
// 기존: 비례 계산 (복잡하고 오류 발생)
BigDecimal lessonUsageDeduction = paidLessonAmount
    .multiply(BigDecimal.valueOf(effectiveUsedDays))
    .divide(BigDecimal.valueOf(totalLessonDays), 0, RoundingMode.DOWN);

// 수정: 고정 일일 요금 (3,500원/일)
BigDecimal lessonUsageDeduction = LESSON_DAILY_RATE
    .multiply(BigDecimal.valueOf(effectiveUsedDays));
```

### 3. **차감액 상한선 설정**

차감액이 실제 지불한 강습료를 초과하지 않도록 안전장치 추가:

```java
if (lessonUsageDeduction.compareTo(paidLessonAmount) > 0) {
    lessonUsageDeduction = paidLessonAmount;
}
```

---

## 🧮 새로운 계산 방식

### **Step 1: 금액 분리**

```
총 결제금액 = payment.getPaidAmt()

IF 사물함 사용:
    강습료 = 총 결제금액 - 5,000원
    사물함료 = 5,000원
ELSE:
    강습료 = 총 결제금액
    사물함료 = 0원
```

### **Step 2: 사용일수 결정**

```
IF manualUsedDays 제공:
    effectiveUsedDays = manualUsedDays
ELSE:
    effectiveUsedDays = systemCalculatedUsedDays

effectiveUsedDays = max(0, effectiveUsedDays)  // 음수 방지
```

### **Step 3: 강습료 차감액 계산**

```
강습료 차감액 = min(effectiveUsedDays × 3,500원, 실제 강습료)
```

### **Step 4: 사물함 차감액 계산**

```
IF 사물함 사용:
    사물함 차감액 = 전액 (5,000원)
ELSE:
    사물함 차감액 = 0원
```

### **Step 5: 최종 환불액 계산**

```
최종 환불액 = 총 결제금액 - 강습료 차감액 - 사물함 차감액
```

---

## 📊 계산 예시

### **예시 1: 사물함 사용, 3일 이용**

```
- 총 결제금액: 110,000원
- 사물함 사용: true
- 사용일수: 3일

계산 과정:
1. 강습료: 110,000 - 5,000 = 105,000원
2. 사물함료: 5,000원
3. 강습료 차감액: 3 × 3,500 = 10,500원
4. 사물함 차감액: 5,000원 (전액)
5. 최종 환불액: 110,000 - 10,500 - 5,000 = 94,500원
```

### **예시 2: 사물함 미사용, 5일 이용**

```
- 총 결제금액: 100,000원
- 사물함 사용: false
- 사용일수: 5일

계산 과정:
1. 강습료: 100,000원
2. 사물함료: 0원
3. 강습료 차감액: 5 × 3,500 = 17,500원
4. 사물함 차감액: 0원
5. 최종 환불액: 100,000 - 17,500 - 0 = 82,500원
```

### **예시 3: 과도한 사용일수 (상한선 적용)**

```
- 총 결제금액: 50,000원
- 사물함 사용: false
- 사용일수: 20일 (20 × 3,500 = 70,000원 > 50,000원)

계산 과정:
1. 강습료: 50,000원
2. 계산된 차감액: 20 × 3,500 = 70,000원
3. 상한선 적용: min(70,000, 50,000) = 50,000원
4. 최종 환불액: 50,000 - 50,000 = 0원 (환불 불가)
```

---

## 🔧 주요 상수 설정

```java
// EnrollmentServiceImpl.java
private static final BigDecimal LESSON_DAILY_RATE = BigDecimal.valueOf(3500);

// application.yml 또는 설정
cms.locker.fee: 5000  // lockerFeeConfig
```

---

## 📡 API 응답 형식

### **요청**

```http
POST /api/v1/cms/enrollments/{enrollId}/calculate-refund-preview
Content-Type: application/json

{
    "manualUsedDays": 3
}
```

### **응답**

```json
{
  "success": true,
  "message": "예상 환불액 계산 성공",
  "data": {
    "systemCalculatedUsedDays": 1,
    "manualUsedDays": 3,
    "effectiveUsedDays": 3,
    "originalLessonPrice": 105000,
    "paidLessonAmount": 105000,
    "paidLockerAmount": 5000,
    "lessonUsageDeduction": 10500,
    "lockerDeduction": 5000,
    "finalRefundAmount": 94500,
    "fullRefund": false
  }
}
```

---

## ✨ 개선 효과

### **1. 데이터 독립성**

- Payment 테이블의 불완전한 데이터에 의존하지 않음
- 기존 데이터베이스 수정 없이 정상 작동

### **2. 계산 정확성**

- 명확한 일일 요금 기준 (3,500원/일)
- 예측 가능하고 일관된 계산 결과

### **3. 안정성**

- 차감액 상한선으로 과도한 차감 방지
- 음수 사용일수 처리

### **4. 투명성**

- 각 계산 단계가 명확히 분리됨
- API 응답에 모든 계산 과정 포함

---

## 🚨 주의사항

### **1. 사물함 요금 설정**

- `lockerFeeConfig` 값이 정확히 설정되어야 함
- 현재 기본값: 5,000원

### **2. 일일 요금 변경 시**

- `LESSON_DAILY_RATE` 상수 수정 필요
- 시스템 재시작 후 적용

### **3. 환불 불가 조건**

- `finalRefundAmount < 0`인 경우 환불 불가 처리
- 프론트엔드에서 적절한 안내 메시지 표시 필요

---

## 🔄 향후 개선 방안

### **1. 설정 외부화**

```yaml
# application.yml
cms:
  refund:
    lesson-daily-rate: 3500
    locker-fee: 5000
```

### **2. 환불 정책 다양화**

- 수강 기간별 차등 요금
- 조기 환불 할인/할증
- 특별 프로모션 적용

### **3. 로그 및 감사**

- 환불 계산 과정 상세 로깅
- 환불 승인/거부 이력 관리

---

## 📞 문의 및 지원

환불 계산 로직 관련 문의사항이 있으시면 개발팀에 연락 바랍니다.

**관련 파일**:

- `EnrollmentServiceImpl.java`
- `CalculatedRefundDetailsDto.java`
- `EnrollmentAdminController.java`
