package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileDto {
  private String name;
  private String userId; // Assuming this maps to User.username or User.uuid
  private String phone;
  private String address;
  private String email;
  private String carNo;
  private String gender;
} 