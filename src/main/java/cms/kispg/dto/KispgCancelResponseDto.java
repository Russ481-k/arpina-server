package cms.kispg.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class KispgCancelResponseDto {

    @JsonProperty("resultCd")
    private String resultCd;

    @JsonProperty("resultMsg")
    private String resultMsg;

    @JsonProperty("payMethod")
    private String payMethod;

    @JsonProperty("tid")
    private String tid;

    @JsonProperty("appDtm")
    private String appDtm;

    @JsonProperty("appNo")
    private String appNo;

    @JsonProperty("ordNo")
    private String ordNo;

    @JsonProperty("amt")
    private String amt;

    @JsonProperty("cancelYN")
    private String cancelYN;

    @JsonProperty("mbsReserved")
    private String mbsReserved;
}