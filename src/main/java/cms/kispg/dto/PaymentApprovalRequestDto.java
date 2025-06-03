package cms.kispg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentApprovalRequestDto {
    private String tid; // KISPG TID (optional, might be part of kispgPaymentResult)
    private String moid; // Our system's MOID (order ID)
    private String amt; // Amount (optional, might be part of kispgPaymentResult or for validation)
    private KispgPaymentResultDto kispgPaymentResult; // Result from KISPG payment page
    private String membershipType; // User selected membership type (e.g., "general", "merit")
} 