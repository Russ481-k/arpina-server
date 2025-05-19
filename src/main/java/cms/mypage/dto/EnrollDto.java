package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Getter
@Setter
public class EnrollDto {
  private Long enrollId;
  private LessonDetails lesson;
  private String status; // e.g., UNPAID, PAID, CANCELED
  private OffsetDateTime expireDt; // Using OffsetDateTime for time zone awareness
  private LockerDetails locker;
  private RenewalWindow renewalWindow;

  @Getter
  @Setter
  public static class LessonDetails {
    private String title;
    private String period;
    private String time;
    private BigDecimal price;
  }

  @Getter
  @Setter
  public static class LockerDetails {
    private Integer id;
    private String zone;
    private boolean carryOver;
  }

  @Getter
  @Setter
  public static class RenewalWindow {
    private OffsetDateTime open;
    private OffsetDateTime close;
  }
} 