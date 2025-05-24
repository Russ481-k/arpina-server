package cms.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- 공통 오류 코드 (C001 ~ C099) ---
    INVALID_INPUT_VALUE("C001", "입력 값이 유효하지 않습니다."),
    METHOD_NOT_ALLOWED("C002", "허용되지 않은 HTTP 메소드입니다."),
    INTERNAL_SERVER_ERROR("C003", "서버 내부 오류가 발생했습니다. 관리자에게 문의해주세요."),
    ACCESS_DENIED("C004", "요청한 리소스에 접근할 권한이 없습니다."),
    RESOURCE_NOT_FOUND("C005", "요청한 리소스를 찾을 수 없습니다."),
    AUTHENTICATION_FAILED("C006", "사용자 인증에 실패했습니다."),
    REQUEST_TIMEOUT("C007", "요청 처리 시간이 초과되었습니다."),

    // --- 사용자 및 프로필 관련 오류 코드 (U001 ~ U099) ---
    USER_NOT_FOUND("U001", "해당 사용자를 찾을 수 없습니다."),
    DUPLICATE_USER_ID("U002", "이미 사용 중인 아이디입니다."),
    PASSWORD_MISMATCH("U003", "기존 비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_MISMATCH("U004", "새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다."),
    PASSWORD_POLICY_VIOLATION("U005", "비밀번호 정책을 만족하지 못합니다."),
    PROFILE_UPDATE_FAILED("U006", "프로필 업데이트 중 오류가 발생했습니다."),
    TEMP_PASSWORD_ISSUE_FAILED("U007", "임시 비밀번호 발급 중 오류가 발생했습니다."),
    INVALID_CURRENT_PASSWORD("U008", "현재 비밀번호가 올바르지 않습니다."),
    INVALID_USER_GENDER("U009", "유효하지 않은 사용자 성별 값입니다."),

    // --- 수강신청 (Enrollment) 관련 오류 코드 (E001 ~ E099) ---
    ENROLLMENT_NOT_FOUND("E001", "수강 신청 정보를 찾을 수 없습니다."),
    LESSON_NOT_FOUND("E002", "강좌 정보를 찾을 수 없습니다."),
    LESSON_NOT_OPEN_FOR_ENROLLMENT("E003", "현재 신청 가능한 강좌가 아닙니다."),
    LESSON_CAPACITY_EXCEEDED("E004", "강좌의 정원이 초과되었습니다."),
    DUPLICATE_ENROLLMENT_ATTEMPT("E005", "이미 해당 강좌에 대한 신청 또는 결제 내역이 존재합니다."),
    MONTHLY_ENROLLMENT_LIMIT_EXCEEDED("E006", "한 달에 하나의 강좌만 신청할 수 있습니다."),
    ENROLLMENT_PAYMENT_EXPIRED("E007", "결제 가능 시간이 만료되었습니다."),
    LOCKER_NOT_AVAILABLE("E008", "현재 사용 가능한 라커가 없습니다."),
    LOCKER_ASSIGNMENT_FAILED("E009", "라커 배정 중 오류가 발생했습니다."),
    LOCKER_RELEASE_FAILED("E010", "라커 반납 처리 중 오류가 발생했습니다."),
    ENROLLMENT_CANCELLATION_NOT_ALLOWED("E011", "수강 신청을 취소할 수 없는 상태입니다."),
    ENROLLMENT_RENEWAL_NOT_ALLOWED("E012", "재수강(갱신)을 할 수 없는 상태입니다."),
    USER_GENDER_REQUIRED_FOR_LOCKER("E013", "라커를 신청하거나 반납하려면 사용자의 성별 정보가 필요합니다."),
    ALREADY_CANCELLED_ENROLLMENT("E014", "이미 취소 처리된 수강 신청입니다."),
    LESSON_LOCKER_CAPACITY_EXCEEDED_FOR_GENDER("E015", "해당 성별의 강습 사물함 정원이 초과되었습니다."),
    LOCKER_INVENTORY_NOT_FOUND("E016", "사물함 재고 정보를 찾을 수 없습니다."),
    PAYMENT_PAGE_SLOT_UNAVAILABLE("E017", "현재 해당 강습의 결제 페이지에 접근할 수 있는 인원이 가득 찼습니다. 잠시 후 다시 시도해주세요."),
    LESSON_CANNOT_BE_DELETED("E018", "해당 강습에 신청 내역이 존재하여 삭제할 수 없습니다."),
    LESSON_FULL("E019", "현재 해당 강습의 결제 페이지 접근 슬롯이 가득 찼습니다."),

    // --- 결제 (Payment) 관련 오류 코드 (P001 ~ P099) ---
    PAYMENT_INFO_NOT_FOUND("P001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH("P002", "결제 요청 금액과 실제 금액이 일치하지 않습니다."),
    PAYMENT_PROCESSING_FAILED("P003", "결제 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_REFUND_FAILED("P004", "환불 처리 중 오류가 발생했습니다."),
    ALREADY_PAID_ENROLLMENT("P005", "이미 결제가 완료된 수강 신청입니다."),
    NOT_UNPAID_ENROLLMENT_STATUS("P006", "결제 대기 상태의 수강 신청이 아닙니다."),
    PAYMENT_CANCEL_NOT_ALLOWED("P007", "결제를 취소할 수 없는 상태입니다.");




    private final String code;
    private final String defaultMessage;
} 