# 파일 삭제 스케줄러 안전성 가이드

## 📋 개요

2025년 7월 26일 스케줄러 오작동으로 인한 파일 대량 삭제 사고를 방지하기 위해 구현된 안전장치들에 대한 문서입니다.

## 🚨 사고 요약

- **발생일**: 2025년 7월 26일 새벽 3시
- **원인**: 파일 ID 추출 로직이 `help.handylab.co.kr` 도메인을 올바르게 처리하지 못함
- **피해**: 약 145개의 정상 파일이 삭제됨
- **영향범위**: 최근 게시글들의 이미지 파일

## 🛡️ 구현된 안전장치

### 1. 파일 ID 추출 로직 개선

#### Before (문제 상황)

```java
// help.handylab.co.kr 도메인을 외부 도메인으로 잘못 인식
if (src.contains("help.handylab.co.kr")) {
    return null; // 파일 ID 추출 실패
}
```

#### After (수정 후)

```java
// 모든 도메인에서 /api/v1/cms/file/public/view/ 패턴 지원
// blob URL만 필터링
if (src.startsWith("blob:")) {
    return null;
}
```

### 2. 스케줄러 사전 안전 검사

#### 실행 전 검증

```java
@Scheduled(cron = "0 0 3 * * FRI#1")
public void cleanupOrphanedFiles() {
    // 🛡️ 사전 안전 검사
    if (!performSafetyCheck()) {
        log.error("🚨 SAFETY CHECK FAILED - Aborting cleanup!");
        return; // 스케줄러 중단
    }
    // ... 파일 삭제 로직
}
```

#### 검증 내용

- 최근 게시글 5개 샘플링
- 파일 ID 추출 로직 정상 작동 확인
- 성공률 90% 미만 시 스케줄러 중단

### 3. 삭제 임계값 모니터링

```java
// 🚨 이상 상황 감지
if (deletedCount > 10) {
    log.warn("⚠️ HIGH DELETION COUNT: {} files deleted", deletedCount);
}

double deletionRate = (double) deletedCount / totalFiles * 100;
if (deletionRate > 20.0) {
    log.error("🚨 CRITICAL: {}% of files deleted!", deletionRate);
}
```

### 4. 파일 ID 추출 단위 테스트

```java
@Test
@DisplayName("help.handylab.co.kr 도메인에서 파일 ID 추출 테스트")
void testParseFileIdFromHelpdHandylabDomain() {
    String src = "https://help.handylab.co.kr/api/v1/cms/file/public/view/341";
    Long fileId = parseFileIdFromSrc(src);
    assertEquals(341L, fileId);
}
```

## 🔧 운영 가이드

### 1. 스케줄러 실행 전 체크리스트

- [ ] 최근 게시글에 이미지가 정상적으로 표시되는지 확인
- [ ] 파일 ID 추출 테스트 실행: `mvn test -Dtest=FileIdExtractionTest`
- [ ] 로그에서 안전 검사 통과 확인

### 2. 모니터링 포인트

#### 정상 로그 패턴

```
🔍 Performing safety check before cleanup...
✅ Safety check passed - File ID extraction logic appears to be working correctly
Successfully deleted 2 orphaned files
```

#### 위험 로그 패턴

```
❌ Safety check failed - File ID extraction logic may have issues
🚨 SAFETY CHECK FAILED - Aborting cleanup to prevent data loss!
⚠️ HIGH DELETION COUNT: 15 files deleted - Please review!
🚨 CRITICAL: 25.0% of files deleted (30). This may indicate a problem!
```

### 3. 긴급 대응 절차

#### 1단계: 스케줄러 즉시 중단

```java
// @Scheduled(cron = "0 0 3 * * FRI#1") // 주석 처리
```

#### 2단계: 원인 분석

- 파일 ID 추출 로직 테스트 실행
- 최근 게시글의 JSON 구조 확인
- 도메인 설정 변경 여부 확인

#### 3단계: 데이터 복구

- 백업에서 파일 복구 (우선순위 1)
- 게시글 내용 수동 수정 (백업 없는 경우)

## 📊 테스트 케이스

### 파일 ID 추출 테스트

| 입력 URL                                                      | 예상 결과 | 테스트 통과 |
| ------------------------------------------------------------- | --------- | ----------- |
| `https://help.handylab.co.kr/api/v1/cms/file/public/view/341` | `341L`    | ✅          |
| `blob:https://example.com/uuid`                               | `null`    | ✅          |
| `https://example.com/api/v1/cms/file/public/view/123`         | `123L`    | ✅          |

### JSON 파싱 테스트

| JSON 상태   | 예상 결과          | 테스트 통과 |
| ----------- | ------------------ | ----------- |
| 정상 JSON   | 파일 ID Set        | ✅          |
| 빈 JSON     | 빈 Set             | ✅          |
| 잘못된 JSON | 빈 Set (예외 없음) | ✅          |

## 🚀 향후 개선 사항

### 1. 실시간 모니터링

- 파일 삭제 알림 시스템
- 대시보드 연동

### 2. 백업 자동화

- 스케줄러 실행 전 자동 백업
- 롤백 기능

### 3. 추가 안전장치

- 화이트리스트 기반 도메인 검증
- 삭제 전 사용자 승인 요청

## 📞 문의

파일 삭제 관련 문제 발생 시:

1. 즉시 스케줄러 중단
2. 로그 분석
3. 개발팀 연락

---

**⚠️ 중요**: 이 가이드는 실제 서비스에서 발생한 사고 경험을 바탕으로 작성되었습니다. 모든 안전장치를 숙지하고 정기적으로 테스트하시기 바랍니다.
