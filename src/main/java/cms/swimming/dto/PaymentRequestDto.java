package cms.swimming.dto;

import lombok.*;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequestDto {
    @NotEmpty(message = "결제 거래 ID는 필수입니다")
    private String tid;
    
    @NotNull(message = "결제 금액은 필수입니다")
    @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다")
    private Integer amount;
} 