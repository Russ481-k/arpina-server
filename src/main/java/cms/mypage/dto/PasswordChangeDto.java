package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeDto {
  private String currentPw;
  private String newPw;
} 