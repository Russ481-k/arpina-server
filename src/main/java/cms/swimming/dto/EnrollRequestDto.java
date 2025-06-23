package cms.swimming.dto;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollRequestDto {
    @NotNull(message = "강습 ID는 필수입니다")
    private Long lessonId;

    // Added for membership type
    private String membershipType;

    // usesLocker is already expected by the backend service, but let's ensure it's
    // here
    // if it wasn't already implicitly handled. The frontend sends usesLocker.
    @NotNull(message = "사물함 사용 여부는 필수입니다")
    private Boolean usesLocker;

    private Integer discountId;
}