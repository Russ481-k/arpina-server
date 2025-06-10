package cms.kispg.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KispgPaymentResultDto {
    private String resultCd;
    private String resultMsg;
    private String payMethod;
    private String tid;
    private String ordNo; // MOID와 동일
    private String amt;
    private String ediDate;
    private String encData;
    private String mbsReserved; // KISPG 응답의 mbsReserved (mbsReserved1과 다를 수 있음)
    // KISPG가 반환하는 다른 모든 필드를 여기에 추가할 수 있습니다.
}