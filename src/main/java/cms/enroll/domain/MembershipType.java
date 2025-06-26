package cms.enroll.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;

public enum MembershipType {
    GENERAL("general", "해당사항없음", 0),
    MERIT("merit", "국가 유공자 10%할인", 10),
    MULTI_CHILD("multi-child", "다자녀 3인이상 10%할인", 10),
    MULTICULTURAL("multicultural", "다문화 가정 10%할인", 10);

    private final String value;
    private final String label;
    private final int discountPercentage;

    MembershipType(String value, String label, int discountPercentage) {
        this.value = value;
        this.label = label;
        this.discountPercentage = discountPercentage;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public int getDiscountPercentage() {
        return discountPercentage;
    }

    @JsonCreator
    public static MembershipType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return GENERAL;
        }
        return Arrays.stream(MembershipType.values())
                .filter(type -> type.name().equalsIgnoreCase(value) || type.getValue().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown MembershipType value: " + value));
    }

    // Optional: a method to get by label if needed elsewhere
    public static MembershipType fromLabel(String label) {
        if (label == null) {
            return GENERAL;
        }
        return Arrays.stream(MembershipType.values())
                .filter(type -> type.getLabel().equalsIgnoreCase(label))
                .findFirst()
                .orElse(GENERAL);
    }
}