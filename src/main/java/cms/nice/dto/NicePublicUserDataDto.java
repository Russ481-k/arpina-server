package cms.nice.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NicePublicUserDataDto {
    private String reqSeq;       // CP 요청번호
    private String resSeq;       // 처리결과 고유번호 (NICE로부터 받음)
    private String authType;     // 인증 수단
    private String name;         // 이름 (UTF-8 디코딩된 이름 또는 원본 이름)
    private String utf8Name;     // UTF-8로 디코딩 시도된 이름 (NICE 응답 필드 기준)
    private String birthDate;    // 생년월일 (YYYYMMDD)
    private String gender;       // 성별 (0: 여성, 1: 남성 - NICE 기준에 따름)
    private String nationalInfo; // 내외국인 정보 (0: 내국인, 1: 외국인 - NICE 기준)
    private String mobileCo;     // 이동통신사 코드
    private String mobileNo;     // 휴대폰 번호
    // DI, CI 등 민감 정보는 공개 DTO에서 제외될 수 있음
} 