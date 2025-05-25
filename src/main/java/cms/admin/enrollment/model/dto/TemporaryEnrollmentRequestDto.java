package cms.admin.enrollment.model.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TemporaryEnrollmentRequestDto {

    @NotNull(message = "강습 ID는 필수입니다.")
    private Long lessonId;

    @NotBlank(message = "사용자 이름은 필수입니다.")
    @Size(max = 50, message = "사용자 이름은 50자를 초과할 수 없습니다.")
    private String userName;

    // 휴대폰 번호는 선택적일 수 있으나, 중복 방지 및 식별을 위해 권장됩니다.
    // 필요시 @NotBlank 추가 및 정규식 패턴 검증 추가 가능
    @Size(max = 20, message = "휴대폰 번호는 20자를 초과할 수 없습니다.")
    private String userPhone;

    // 사물함 사용 여부, 기본값은 false
    private Boolean usesLocker = false;

    // 임시 등록 시 사용자 성별 (사물함 사용 시 필수)
    // User.Gender enum 사용 또는 String ("MALE", "FEMALE")
    // 이 필드는 usesLocker가 true일 때만 유효성 검사를 수행하도록 그룹화된 제약 조건 또는 커스텀 Validator를 사용할 수 있습니다.
    private String userGender;

    @Size(max = 255, message = "메모는 255자를 초과할 수 없습니다.")
    private String memo;
} 