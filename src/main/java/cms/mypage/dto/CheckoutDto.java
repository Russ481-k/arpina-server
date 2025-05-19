package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CheckoutDto {
  private String merchantUid;
  private BigDecimal amount;
  private String lessonTitle;
  private String userName;
  private String pgProvider;
} 