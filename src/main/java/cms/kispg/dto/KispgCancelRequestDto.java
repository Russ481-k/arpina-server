package cms.kispg.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KispgCancelRequestDto {

    @JsonProperty("payMethod")
    private String payMethod;

    @JsonProperty("tid")
    private String tid;

    @JsonProperty("mid")
    private String mid;

    @JsonProperty("ordNo")
    private String ordNo;

    @JsonProperty("canAmt")
    private String canAmt;

    @JsonProperty("canId")
    private String canId;

    @JsonProperty("canNm")
    private String canNm;

    @JsonProperty("canMsg")
    private String canMsg;

    @JsonProperty("canIp")
    private String canIp;

    @JsonProperty("partCanFlg")
    private String partCanFlg;

    @JsonProperty("refundBankCd")
    private String refundBankCd;

    @JsonProperty("refundAccnt")
    private String refundAccnt;

    @JsonProperty("refundNm")
    private String refundNm;

    @JsonProperty("encData")
    private String encData;

    @JsonProperty("ediDate")
    private String ediDate;

    @JsonProperty("goodsSplAmt")
    private String goodsSplAmt;

    @JsonProperty("goodsVat")
    private String goodsVat;

    @JsonProperty("goodsSvsAmt")
    private String goodsSvsAmt;

    @JsonProperty("charset")
    private String charset;

    @JsonProperty("mbsReserved")
    private String mbsReserved;
}