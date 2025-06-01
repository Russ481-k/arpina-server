package cms.nice.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NiceReqSeqDataDto {
    private String reqSeq;
    private String serviceType; // "REGISTER", "FIND_ID", "RESET_PASSWORD"
    private long timestamp; // 생성 시간 (필요시 사용)
} 