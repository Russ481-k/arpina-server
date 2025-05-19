package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
public class PaymentDto {
  private Long paymentId;
  private Long enrollId;
  private Integer amount;
  private OffsetDateTime paidAt;
  private String status; // SUCCESS | CANCELED | PARTIAL
} 