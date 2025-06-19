# 🔄 사물함 재고 관리 개선 사항

## 📋 개선 전 문제점 분석

### ❌ 즉각 반영되지 않던 시점들

1. **환불 거부 시**: 사물함 재할당 로직 누락
2. **잘못된 재고 관리**: 관리자 취소, 사용자 취소, 만료 처리 시 환불이 아님에도 재고 변동

### ✅ 기존에 잘 작동하던 시점들

- 결제 승인 시 (`KispgPaymentServiceImpl`)
- 환불 승인 시 (`EnrollmentServiceImpl.approveEnrollmentCancellationAdmin`)

---

## 🎯 **사물함 재고 관리 핵심 원칙**

### **사물함 재고는 오직 실제 결제/환불에만 연동**

| 상황          | 결제/환불 여부 | 사물함 재고 변동 | 처리 방식 |
| ------------- | -------------- | ---------------- | --------- |
| **결제 완료** | ✅ 결제        | 🔼 증가          | 즉시 반영 |
| **환불 완료** | ✅ 환불        | 🔽 감소          | 즉시 반영 |
| **환불 거부** | ✅ 복원        | 🔼 증가          | 즉시 반영 |
| 관리자 취소   | ❌ 상태 변경만 | ⏸️ 변동 없음     | 재고 유지 |
| 사용자 취소   | ❌ 상태 변경만 | ⏸️ 변동 없음     | 재고 유지 |
| 만료 처리     | ❌ 상태 변경만 | ⏸️ 변동 없음     | 재고 유지 |

---

## 🔧 주요 개선 사항

### 1. **환불 거부 시 사물함 재할당 로직 추가** ✅

**파일**: `src/main/java/cms/enroll/service/impl/EnrollmentServiceImpl.java`

```java
// 환불 거부 시 사물함을 사용하려고 했으나 할당되지 않은 경우 재할당 시도
if (enroll.isUsesLocker() && !enroll.isLockerAllocated()) {
    User user = enroll.getUser();
    if (user != null && user.getGender() != null && !user.getGender().isEmpty()) {
        String lockerGender = convertGenderCode(user.getGender());
        if (lockerGender != null) {
            try {
                lockerService.incrementUsedQuantity(lockerGender);
                enroll.setLockerAllocated(true);
                logger.info("환불 거부 처리에 따라 {} 사물함이 성공적으로 재할당되었습니다.", lockerGender);
            } catch (Exception e) {
                logger.error("사물함 재할당 실패: {}", e.getMessage());
                enroll.setUsesLocker(false);
            }
        }
    }
}
```

**효과**:

- 환불 거부 후 정상 상태로 복원 시 사물함도 함께 복원
- 재고 부족 시 자동으로 `usesLocker=false`로 설정

### 2. **잘못된 사물함 재고 변동 제거** ✅

**수정된 파일들**:

- `EnrollmentAdminServiceImpl.java`: 관리자 취소 시 재고 변동 제거
- `EnrollmentServiceImpl.java`: 사용자 취소 시 재고 변동 제거
- `ExpiredUnpaidEnrollmentCleanupJob.java`: 만료 처리 시 재고 변동 제거

**변경 사항**:

```java
// ❌ 관리자 취소는 환불이 아니므로 사물함 재고에 영향을 주지 않음
// 사물함 할당 상태는 유지하되, 향후 실제 환불 시에만 재고 반납
logger.info("미결제 건(ID:{}) 관리자 취소 - 사물함 재고는 변경하지 않음 (환불이 아님)", enrollId);
```

**효과**:

- 취소/만료는 단순 상태 변경으로 처리
- 사물함 재고는 실제 결제/환불에만 연동
- 데이터 일관성 및 정확성 향상

### 3. **임시 등록 시 사물함 할당 실패 처리 개선** ✅

**파일**: `src/main/java/cms/admin/enrollment/service/impl/EnrollmentAdminServiceImpl.java`

**개선 사항**:

- 사물함 할당 실패 시 예외 던지기 대신 graceful handling
- 실패 사유를 관리자 메모에 자동 기록
- 더 상세한 에러 로깅

```java
catch (BusinessRuleException e) {
    newEnrollment.setUsesLocker(false);
    newEnrollment.setLockerAllocated(false);
    // 사물함 할당 실패를 관리자에게 알리기 위해 메모에 추가
    String failureNote = "[사물함 할당 실패: " + e.getMessage() + "]";
    newEnrollment.setCancelReason(originalMemo + " " + failureNote);
}
```

**효과**:

- 임시 등록이 사물함 부족으로 실패하지 않음
- 관리자가 실패 사유를 쉽게 확인 가능

---

## 📊 사물함 재고 관리 완전성 매트릭스

| 시점              | 결제/환불 여부 | 사물함 재고 변동 | 상태        |
| ----------------- | -------------- | ---------------- | ----------- |
| **결제 승인**     | ✅ 결제        | 🔼 즉시 증가     | 완료        |
| **환불 승인**     | ✅ 환불        | 🔽 즉시 감소     | 완료        |
| **환불 거부**     | ✅ 복원        | 🔼 즉시 증가     | **🆕 개선** |
| **관리자 취소**   | ❌ 상태변경    | ⏸️ 변동 없음     | **🆕 수정** |
| **사용자 취소**   | ❌ 상태변경    | ⏸️ 변동 없음     | **🆕 수정** |
| **만료된 미결제** | ❌ 상태변경    | ⏸️ 변동 없음     | **🆕 수정** |
| **임시 등록**     | ✅ 결제        | 🔼 즉시 증가     | **🆕 개선** |
| **강좌 완료**     | ❌ 상태변경    | 🔽 매일 3시      | 완료        |
| **월별 초기화**   | ❌ 관리작업    | 🔄 매월 20일     | 완료        |

---

## 🔍 성별 코드 변환 표준화

모든 로직에서 일관된 성별 코드 변환을 사용합니다:

```java
// 표준 변환 로직
String convertGenderCode(String userGender) {
    if ("0".equals(userGender)) {
        return "FEMALE";
    } else if ("1".equals(userGender)) {
        return "MALE";
    } else {
        return null; // 유효하지 않은 성별 코드
    }
}
```

**적용된 파일들**:

- `EnrollmentServiceImpl.java`
- `EnrollmentAdminServiceImpl.java`
- `KispgPaymentServiceImpl.java`

---

## 💡 **핵심 개선 포인트**

### Before (문제점)

```
❌ 관리자 취소 → 사물함 재고 감소 (잘못됨)
❌ 사용자 취소 → 사물함 재고 감소 (잘못됨)
❌ 만료 처리 → 사물함 재고 감소 (잘못됨)
❌ 환불 거부 → 사물함 재할당 없음 (누락)
```

### After (개선됨)

```
✅ 관리자 취소 → 상태만 변경, 재고 유지
✅ 사용자 취소 → 상태만 변경, 재고 유지
✅ 만료 처리 → 상태만 변경, 재고 유지
✅ 환불 거부 → 사물함 재할당
```

---

## 🚀 추가 개선 권장사항

### 1. **실시간 모니터링**

```java
// WebSocket을 통한 실시간 사물함 재고 알림
@EventListener
public void onLockerInventoryChanged(LockerInventoryChangedEvent event) {
    websocketService.broadcast("/topic/locker-inventory", event);
}
```

### 2. **재고 부족 알림**

```java
// 사물함 재고가 10% 이하로 떨어질 때 관리자 알림
if (inventory.getAvailableQuantity() <= inventory.getTotalQuantity() * 0.1) {
    notificationService.sendLowInventoryAlert(inventory.getGender());
}
```

### 3. **데이터 일관성 검증**

```java
// 정기적으로 실제 할당된 사물함 수와 재고 테이블 일치 여부 확인
@Scheduled(cron = "0 0 1 * * ?") // 매일 새벽 1시
public void validateLockerInventoryConsistency() {
    // 실제 결제 완료된 사물함 사용자 수 vs 재고 테이블 used_quantity 비교
}
```

---

## ✅ 테스트 시나리오

### 환불 거부 테스트

1. 사물함이 있는 수강 신청 생성
2. 환불 요청
3. 환불 거부 → 사물함 재할당 확인

### 관리자 취소 테스트

1. 사물함이 있는 결제 완료 수강 신청 생성
2. 관리자 취소 → 사물함 재고 변동 없음 확인

### 사용자 취소 테스트

1. 사물함이 있는 미결제 수강 신청 생성
2. 사용자 취소 → 사물함 재고 변동 없음 확인

### 만료 처리 테스트

1. 사물함이 있는 미결제 수강 신청 생성
2. 만료 시간 경과 후 스케줄러 실행
3. 만료 처리 → 사물함 재고 변동 없음 확인

---

## 📈 예상 효과

1. **재고 정확성**: 실제 결제/환불에만 연동되어 100% 정확성 확보
2. **논리적 일관성**: 취소/만료는 상태 변경, 결제/환불은 재고 변동으로 명확히 분리
3. **관리 효율성**: 잘못된 재고 변동으로 인한 수동 조정 필요성 완전 제거
4. **운영 안정성**: 예외 상황에 대한 robust한 처리

**핵심**: 사물함 재고는 오직 **실제 돈의 흐름(결제/환불)**에만 연동되어 관리됩니다.
