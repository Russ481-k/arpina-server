package cms.nice.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NiceUserDataDto {
    private String name;
    private String utf8Name;
    private String birthDate;
    private String gender; // 0: female, 1: male
    private String di;     // 중복가입 확인정보 (Duplicate Info)
    private String ci;     // 연계정보 (Connecting Information)
    private String nationalInfo; // 0: Korean, 1: Foreigner
    private String mobileCo;
    private String mobileNo;
    private String reqSeq; // CP 요청번호
    private String resSeq; // 처리결과 고유번호
    private String authType; // 인증 수단

    // Fields for checking if user is already joined
    private boolean alreadyJoined;
    private String existingUsername; // If alreadyJoined is true
} 