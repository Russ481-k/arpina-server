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
  private boolean usesLocker; // Added to indicate locker usage
  private String membershipType; // Added to show the applied membership/discount type
  private RenewalWindow renewalWindow;
  private boolean isRenewal;
  private String cancelStatus;
  private String cancelReason;
  // Added fields per swim-user.md documentation
  private boolean canAttemptPayment; // Whether user can attempt payment for this enrollment
  private String paymentPageUrl; // URL to KISPG payment page (if canAttemptPayment is true)

  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LessonDetails {
    private Long lessonId;
    private String title;
    private String name;
    private String period;
    private String startDate;
    private String endDate;
    private String time;
    private String days;
    private String timePrefix;
    private String timeSlot;
    private Integer capacity;
    private Integer remaining;
    private BigDecimal price;
    private String instructor;
    private String location;
    private String reservationId;
    private String receiptId;
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