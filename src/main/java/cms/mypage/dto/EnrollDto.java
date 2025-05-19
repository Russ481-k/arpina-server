package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollDto {
  private Long enrollId;
  private LessonDetails lesson;
  private String status; // e.g., UNPAID, PAID, CANCELED (pay_status from Enroll entity)
  // Fields from EnrollmentServiceImpl's convertToMypageEnrollDto
  private OffsetDateTime applicationDate; 
  private OffsetDateTime paymentExpireDt; // Renamed from expireDt to be specific
  private LockerDetails locker;
  private RenewalWindow renewalWindow;
  private boolean isRenewal;
  private String cancelStatus;
  private String cancelReason;

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LessonDetails {
    private String title;
    private String period;
    private String time;
    private BigDecimal price;
  }

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LockerDetails {
    private Long id; // Changed from Integer to Long to match Locker entity ID
    private String zone;
    private boolean carryOver;
  }

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RenewalWindow {
    private boolean isOpen; // Added to indicate if window is active
    private OffsetDateTime open;
    private OffsetDateTime close;
  }
} 