package cms.payment.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
public class PaymentPageDetailsDto {
    private Long enrollId;
    private String lessonTitle;
    private BigDecimal lessonPrice;
    private String userGender; // "MALE" 또는 "FEMALE"
    private LockerOptionsDto lockerOptions;
    private BigDecimal amountToPay;
    private OffsetDateTime paymentDeadline;

    @Getter
    @Builder
    public static class LockerOptionsDto {
        private boolean lockerAvailableForUserGender;
        private int availableCountForUserGender;
        private BigDecimal lockerFee;
    }
}