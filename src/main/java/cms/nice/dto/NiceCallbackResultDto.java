package cms.nice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // null 필드는 JSON 변환 시 제외
public class NiceCallbackResultDto {
    private String status; // "SUCCESS", "ID_SENT", "PASSWORD_RESET_SENT", "ACCOUNT_NOT_FOUND", "ERROR"
    private String message; // 사용자 안내 메시지
    private String errorCode; // "NO_EMAIL", "EMAIL_SEND_FAILED", "PASSWORD_RESET_FAILED", "INVALID_SERVICE_TYPE"
    private String serviceType; // "REGISTER", "FIND_ID", "RESET_PASSWORD"
    private String userEmail; // 아이디/비밀번호 찾기 시 발송된 이메일 주소 (마스킹된 형태 또는 전체)
    private String identifiedName; // 본인인증으로 확인된 사용자 이름

    // REGISTER 성공 시에만 포함될 수 있는 상세 사용자 데이터
    private NiceUserDataDto userData;

    // Factory methods for convenience
    public static NiceCallbackResultDto successForRegister(String serviceType, NiceUserDataDto userData) {
        return NiceCallbackResultDto.builder()
                .status("SUCCESS")
                .message("본인인증이 완료되었습니다.")
                .serviceType(serviceType)
                .userData(userData)
                .identifiedName(userData.getName())
                .build();
    }

    public static NiceCallbackResultDto idFound(String serviceType, String email, String name) {
        return NiceCallbackResultDto.builder()
                .status("ID_SENT")
                .message("가입하신 아이디(이메일)로 아이디 정보를 발송했습니다.")
                .serviceType(serviceType)
                .userEmail(email) // 필요시 마스킹 처리
                .identifiedName(name)
                .build();
    }

    public static NiceCallbackResultDto passwordResetSent(String serviceType, String email, String name) {
        return NiceCallbackResultDto.builder()
                .status("PASSWORD_RESET_SENT")
                .message("가입하신 아이디(이메일)로 임시 비밀번호를 발송했습니다. 로그인 후 비밀번호를 변경해주세요.")
                .serviceType(serviceType)
                .userEmail(email) // 필요시 마스킹 처리
                .identifiedName(name)
                .build();
    }

    public static NiceCallbackResultDto accountNotFound(String serviceType, String name) {
        return NiceCallbackResultDto.builder()
                .status("ACCOUNT_NOT_FOUND")
                .message("본인인증 정보와 일치하는 가입 계정을 찾을 수 없습니다.")
                .serviceType(serviceType)
                .identifiedName(name)
                .build();
    }

    public static NiceCallbackResultDto error(String serviceType, String errorCode, String message, String name) {
        return NiceCallbackResultDto.builder()
                .status("ERROR")
                .message(message)
                .errorCode(errorCode)
                .serviceType(serviceType)
                .identifiedName(name)
                .build();
    }
} 