package cms.nice.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NiceErrorDataDto {
    private String errorCode;
    private String authType;
    private String reqSeq;
    private String message; // 추가적인 에러 메시지 필드
    private String serviceType; // "REGISTER", "FIND_ID", "RESET_PASSWORD", or "UNKNOWN"
} 