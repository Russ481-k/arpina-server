package cms.auth.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import cms.user.domain.UserRoleType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRegistrationRequest {
    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "사용자 이름은 필수입니다.")
    private String name;

    @Email(message = "유효한 이메일 주소를 입력하세요.")
    private String email;

    private String phoneNumber;
    private UserRoleType role;
    private String status;

    @NotBlank(message = "조직 ID는 필수입니다.")
    private String organizationId;

    @NotBlank(message = "그룹 ID는 필수입니다.")
    private String groupId;
} 