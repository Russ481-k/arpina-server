package cms.admin.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
@Schema(description = "KISPG 결제 내역 조회 요청 DTO")
public class KispgQueryRequestDto {

    @Schema(description = "KISPG 거래 ID (tid 또는 moid 중 하나는 필수)", example = "arpina001m01012506092309056594")
    private String tid;

    @Schema(description = "가맹점 주문번호 (tid 또는 moid 중 하나는 필수)", example = "temp_26_73204f9d_1749478143938")
    private String moid;

    @Schema(description = "결제 금액 (필수)", example = "82000", required = true)
    @NotEmpty(message = "결제 금액은 필수입니다.")
    private String amt;
}