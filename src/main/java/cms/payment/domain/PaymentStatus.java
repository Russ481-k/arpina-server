package cms.payment.domain;

public enum PaymentStatus {
    PAID("PAID", "결제 완료"),
    FAILED("FAILED", "결제 실패"),
    CANCELED("CANCELED", "결제 취소"),
    PARTIAL_REFUNDED("PARTIAL_REFUNDED", "부분 환불 완료"),
    REFUND_REQUESTED("REFUND_REQUESTED", "환불 요청됨");

    private final String dbValue;
    private final String description;

    PaymentStatus(String dbValue, String description) {
        this.dbValue = dbValue;
        this.description = description;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDescription() {
        return description;
    }

    // 데이터베이스 값으로부터 Enum 상수를 찾는 메소드 (JPA에서는 @Enumerated(EnumType.STRING) 사용 시 자동 처리)
    // 필요에 따라 public으로 변경하여 서비스 로직에서 사용할 수 있음
    private static PaymentStatus fromDbValue(String dbValue) {
        for (PaymentStatus status : values()) {
            if (status.getDbValue().equalsIgnoreCase(dbValue)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown database value for PaymentStatus: " + dbValue);
    }

    // API 응답 등을 위해 DB 저장 값을 반환 (Jackson 등에서 Enum 이름을 기본으로 사용)
    // @JsonValue // 필요 시 Jackson에 특정 값 반환 명시
    // public String toJsonValue() {
    // return dbValue;
    // }
}