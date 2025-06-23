package cms.mypage.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.time.LocalDate;

@Getter
@Setter
public class PaymentDto {
  private Long paymentId;
  private Long enrollId;
  private Integer amount;
  private OffsetDateTime paidAt;
  private String status; // SUCCESS | CANCELED | PARTIAL

  // 수강 관련 정보 추가
  private String lessonTitle; // 수강명
  private LocalDate lessonStartDate; // 수강 시작일
  private LocalDate lessonEndDate; // 수강 종료일
  private String lessonTime; // 강습시간 (예: "(월,화,수,목,금) 오전 06:00~06:50")
  private String instructorName; // 강사명
  private String locationName; // 강습 장소
  private Integer lessonPrice; // 강습비
  private Integer lockerFee; // 사물함 요금
  private Boolean usesLocker; // 사물함 사용 여부
  private String discountType; // 할인 유형
  private Integer discountPercentage; // 할인율 (%)
  private Integer finalAmount; // 최종 결제 금액
  private String membershipType; // 회원권 유형
}