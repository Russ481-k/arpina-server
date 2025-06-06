package cms.auth.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Getter
@Setter
public class VerifyEmailRequestDto {

    @NotBlank(message = "이메일은 필수 항목입니다.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "인증 코드는 필수 항목입니다.")
    @Size(min = 6, max = 6, message = "인증 코드는 6자리여야 합니다.")
    private String code;
}