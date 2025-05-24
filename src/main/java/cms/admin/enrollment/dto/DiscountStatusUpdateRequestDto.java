package cms.admin.enrollment.dto;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
// Enroll.DiscountStatusType을 직접 참조하려면 Enroll 클래스가 같은 모듈 내에 있거나, 별도의 공통 DTO/Enum 모듈로 분리 필요.
// 여기서는 String으로 받고 서비스 레이어에서 Enum으로 변환한다고 가정합니다.

@Getter
@Setter
public class DiscountStatusUpdateRequestDto {
    @NotBlank(message = "할인 종류는 필수입니다.")
    private String discountType;

    @NotNull(message = "할인 상태는 필수입니다.")
    private String discountStatus; // "PENDING", "APPROVED", "DENIED"

    private String adminComment;
} 