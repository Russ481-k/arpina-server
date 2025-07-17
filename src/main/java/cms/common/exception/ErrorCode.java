package cms.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common Errors (CM_xxxx)
    INTERNAL_SERVER_ERROR("CM_0001", "서버 내부 오류가 발생했습니다. 관리자에게 문의해주세요.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_INPUT_VALUE("CM_0002", "입력값이 유효하지 않습니다. 다시 확인해주세요.", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED("CM_0003", "요청에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    AUTHENTICATION_FAILED("CM_0004", "로그인이 필요한 서비스입니다. 로그인 후 이용해 주세요.", HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED("CM_0005", "로그인 세션이 만료되었습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
    RESOURCE_NOT_FOUND("CM_0006", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("CM_0007", "허용되지 않은 HTTP 메소드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    REQUEST_TIMEOUT("CM_0008", "요청 처리 시간이 초과되었습니다.", HttpStatus.REQUEST_TIMEOUT),
    SERVICE_UNAVAILABLE("CM_0009", "현재 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    DATA_INTEGRITY_VIOLATION("CM_0010", "데이터 무결성 제약조건을 위반했습니다. 입력값을 확인해주세요.", HttpStatus.CONFLICT),
    INVALID_REQUEST("CM_0011", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),

    // User Errors (US_xxxx)
    USER_NOT_FOUND("US_0001", "해당 사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_USERNAME("US_0002", "이미 사용 중인 사용자 ID입니다.", HttpStatus.CONFLICT), // username 중복은 DUPLICATE_USERNAME 사용
    DUPLICATE_EMAIL("US_0003", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT), // email 중복은 DUPLICATE_EMAIL 사용
    PASSWORD_MISMATCH("US_0004", "비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_FORMAT("US_0005", "비밀번호 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED("US_0006", "계정이 잠겼습니다. 관리자에게 문의하세요.", HttpStatus.FORBIDDEN),
    ACCOUNT_EXPIRED("US_0007", "계정이 만료되었습니다.", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("US_0008", "비활성화된 계정입니다.", HttpStatus.FORBIDDEN),
    INVALID_USER_GENDER("US_0009", "사용자의 성별 코드가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    DUPLICATE_DI("US_0010", "이미 해당 본인인증 정보로 가입된 계정이 존재합니다.", HttpStatus.CONFLICT),
    INVALID_CURRENT_PASSWORD("US_0011", "현재 비밀번호가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    PROFILE_UPDATE_FAILED("US_0012", "프로필 업데이트 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    TEMP_PASSWORD_ISSUE_FAILED("US_0013", "임시 비밀번호 발급 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // NICE Verification Errors (NV_xxxx)
    NICE_VERIFICATION_FAILED("NV_0001", "NICE 본인인증에 실패했거나 인증 정보가 만료되었습니다.", HttpStatus.BAD_REQUEST),
    NICE_VERIFICATION_MISSING_KEY("NV_0002", "NICE 본인인증 정보가 누락되었습니다. 본인인증을 다시 진행해주세요.", HttpStatus.BAD_REQUEST),
    NICE_VERIFICATION_ERROR("NV_0003", "NICE 본인인증 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // Lesson Errors (LS_xxxx)
    LESSON_NOT_FOUND("LS_0001", "강습 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    LESSON_CAPACITY_EXCEEDED("LS_0002", "강습 정원이 초과되었습니다.", HttpStatus.BAD_REQUEST),
    LESSON_ALREADY_ENROLLED("LS_0003", "이미 해당 강습에 등록되어 있습니다.", HttpStatus.CONFLICT),
    INVALID_LESSON_STATUS("LS_0004", "강습 상태가 유효하지 않아 작업을 처리할 수 없습니다.", HttpStatus.BAD_REQUEST),
    LESSON_REGISTRATION_PERIOD_INVALID("LS_0005", "강습 신청/변경 기간이 아닙니다.", HttpStatus.BAD_REQUEST),
    LESSON_CANNOT_BE_DELETED("LS_0006", "해당 강습에 신청 내역이 존재하여 삭제할 수 없습니다.", HttpStatus.CONFLICT),

    // Enrollment Errors (EN_xxxx)
    ENROLLMENT_NOT_FOUND("EN_0001", "수강 신청 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ENROLLMENT_CANCELLATION_NOT_ALLOWED("EN_0002", "수강 신청 취소가 허용되지 않는 상태입니다.", HttpStatus.BAD_REQUEST),
    ENROLLMENT_PAYMENT_EXPIRED("EN_0003", "수강 신청 결제 가능 시간이 만료되었습니다.", HttpStatus.BAD_REQUEST),
    MONTHLY_ENROLLMENT_LIMIT_EXCEEDED("EN_0004", "월별 수강 신청 가능 횟수를 초과했습니다.", HttpStatus.BAD_REQUEST),
    DUPLICATE_ENROLLMENT_ATTEMPT("EN_0005", "이미 해당 강습에 대한 신청(결제대기 또는 완료) 내역이 존재합니다.", HttpStatus.CONFLICT),
    NOT_UNPAID_ENROLLMENT_STATUS("EN_0006", "결제 대기 상태의 수강 신청이 아닙니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_PAGE_SLOT_UNAVAILABLE("EN_0007", "정원 마감 또는 신청 가능한 슬롯이 없습니다.", HttpStatus.CONFLICT),
    ALREADY_CANCELLED_ENROLLMENT("EN_0008", "이미 취소 처리 중이거나 완료된 신청입니다.", HttpStatus.CONFLICT),
    RENEWAL_PERIOD_INVALID("EN_0009", "재수강 신청 기간이 아닙니다.", HttpStatus.BAD_REQUEST),
    ENROLLMENT_PREVIOUSLY_CANCELLED_BY_ADMIN("EN_0010", "해당 강습에 대한 이전 신청이 관리자에 의해 취소된 내역이 있어 재신청할 수 없습니다.",
            HttpStatus.FORBIDDEN),
    DUPLICATE_ENROLLMENT("EN_0011", "이미 해당 강습에 신청 내역이 존재합니다.", HttpStatus.CONFLICT),
    REGISTRATION_PERIOD_INVALID("EN_0012", "신청 기간이 아닙니다.", HttpStatus.BAD_REQUEST),

    // Payment Errors (PM_xxxx)
    PAYMENT_INFO_NOT_FOUND("PM_0001", "결제 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_MISMATCH("PM_0002", "결제 금액이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_FAILED("PM_0003", "결제 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    REFUND_NOT_POSSIBLE("PM_0004", "환불이 불가능한 상태입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATUS_FOR_OPERATION("PM_0005", "현재 결제 상태에서는 해당 작업을 수행할 수 없습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_WEBHOOK_INVALID_REQUEST("PM_0006", "잘못된 결제 웹훅 요청입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_PROCESSED("PM_0007", "이미 처리된 결제입니다.", HttpStatus.CONFLICT),
    PAYMENT_CANCEL_NOT_ALLOWED("PM_0008", "해당 결제는 현재 취소할 수 없는 상태입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_REFUND_FAILED("PM_0009", "결제 환불 처리 중 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    PAYMENT_GATEWAY_APPROVAL_FAILED("PM_0010", "결제 게이트웨이 승인에 실패했습니다.", HttpStatus.BAD_REQUEST),
    CANNOT_CALCULATE_REFUND("PM_0011", "환불액을 계산할 수 없는 상태입니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_CANCEL_FAILED("PM_0012", "PG사 결제 취소에 실패했습니다. (PG사 거부)", HttpStatus.BAD_REQUEST),
    PAYMENT_GATEWAY_ERROR("PM_0013", "PG사와의 통신 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    PG_TRANSACTION_NOT_FOUND("PM_0014", "PG사에서 해당 거래를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // Locker Errors (LK_xxxx)
    LOCKER_NOT_AVAILABLE("LK_0001", "사용 가능한 사물함이 없습니다.", HttpStatus.CONFLICT),
    LOCKER_INVENTORY_NOT_FOUND("LK_0002", "해당 성별의 사물함 재고 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    LOCKER_ASSIGNMENT_FAILED("LK_0003", "사물함 배정에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    LOCKER_GENDER_REQUIRED("LK_0004", "사물함 사용 시 사용자의 성별 정보가 필요합니다.", HttpStatus.BAD_REQUEST),
    LOCKER_ALREADY_ASSIGNED_TO_USER("LK_0005", "이미 해당 사용자에게 사물함이 배정되었습니다.", HttpStatus.CONFLICT),

    // File Errors (FL_xxxx)
    FILE_UPLOAD_FAILED("FL_0001", "파일 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND("FL_0002", "파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_FILE_FORMAT("FL_0003", "지원하지 않는 파일 형식입니다.", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("FL_0004", "파일 크기가 너무 큽니다.", HttpStatus.PAYLOAD_TOO_LARGE),

    // Content Errors (CT_xxxx)
    CONTENT_BLOCK_NOT_FOUND("CT_0001", "콘텐츠 블록을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CONTENT_BLOCK_HISTORY_NOT_FOUND("CT_0002", "콘텐츠 블록 히스토리를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // Template Errors (TP_xxxx)
    TEMPLATE_NOT_FOUND("TP_0001", "템플릿을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}