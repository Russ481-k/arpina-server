package cms.user.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import cms.user.domain.UserRoleType;

@Getter
@Setter
public class SiteManagerRegisterRequest {
    @NotBlank(message = "사용자 ID는 필수입니다.")
    @Size(min = 4, max = 50, message = "사용자 ID는 4자 이상 50자 이하여야 합니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()])[A-Za-z\\d!@#$%^&*()]{8,}$",
            message = "비밀번호는 최소 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.")
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "유효한 이메일 주소를 입력해주세요.")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
    private String email;

    @Size(max = 255, message = "아바타 URL은 255자 이하여야 합니다.")
    private String avatarUrl;

    @Size(max = 36, message = "그룹 ID는 36자 이하여야 합니다.")
    private String groupId;

    @NotBlank(message = "이름은 필수입니다.")
    private String fullName;

    @Pattern(regexp = "^\\d{3}-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phoneNumber;

    @NotBlank(message = "사이트 이름은 필수입니다.")
    private String siteName;

    @NotBlank(message = "사이트 설명은 필수입니다.")
    private String siteDescription;

    @NotBlank(message = "사이트 URL은 필수입니다.")
    private String siteUrl;

    private String organizationId;

    private UserRoleType role;
} 
 
 
 