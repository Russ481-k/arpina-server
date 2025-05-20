package cms.swimming.dto;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollRequestDto {
    @NotNull(message = "강습 ID는 필수입니다")
    private Long lessonId;
    
    private Long lockerId; // 사물함 ID는 필수가 아닐 수 있음
    
    private boolean wantsLocker; // Added for requesting a locker without specifying an ID
    
    @NotNull(message = "결제 정보는 필수입니다")
    private PaymentRequestDto paymentInfo;

    private String paymentMethod; // Added paymentMethod
} 