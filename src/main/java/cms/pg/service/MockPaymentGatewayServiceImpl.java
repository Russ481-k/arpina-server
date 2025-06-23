package cms.pg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service("mockPaymentGatewayService")
public class MockPaymentGatewayServiceImpl implements PaymentGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(MockPaymentGatewayServiceImpl.class);
    private final Map<String, Integer> mockPaidAmounts = new HashMap<>(); // Stores token -> amount
    private final Map<String, String> mockTransactionStatus = new HashMap<>(); // Stores token -> status (e.g. PAID,
                                                                               // REFUND_REQUESTED)

    @Override
    public boolean verifyPayment(String pgToken, String merchantUid, Integer expectedAmount) {
        logger.info("[Mock PG] Verifying payment for pgToken: {}, merchantUid: {}, expectedAmount: {}", pgToken,
                merchantUid, expectedAmount);
        // Simulate successful verification
        mockPaidAmounts.put(pgToken, expectedAmount);
        mockTransactionStatus.put(pgToken, "PAID");
        logger.info("[Mock PG] Payment verified successfully for pgToken: {}. Status: PAID", pgToken);
        return true;
    }

    @Override
    public Integer getPaidAmount(String pgToken) {
        Integer amount = mockPaidAmounts.getOrDefault(pgToken, 0);
        logger.info("[Mock PG] Getting paid amount for pgToken: {}. Amount: {}", pgToken, amount);
        if (!"PAID".equals(mockTransactionStatus.get(pgToken))) {
            logger.warn("[Mock PG] Paid amount requested for non-PAID transaction. Token: {}, Status: {}", pgToken,
                    mockTransactionStatus.get(pgToken));
            // Depending on strictness, could return 0 or throw error.
            // For now, return stored amount, but real PG would likely not return amount for
            // unverified/failed payment.
        }
        return amount;
    }

    @Override
    public boolean requestRefund(String pgToken, String merchantUid, Integer amount, String reason) {
        logger.info("[Mock PG] Requesting refund for pgToken: {}, merchantUid: {}, amount: {}, reason: {}", pgToken,
                merchantUid, amount, reason);

        if (!mockPaidAmounts.containsKey(pgToken) || !"PAID".equals(mockTransactionStatus.get(pgToken))) {
            logger.warn(
                    "[Mock PG] Refund request failed for pgToken: {}. Token not found or not in PAID status. Current status: {}",
                    pgToken, mockTransactionStatus.get(pgToken));
            return false;
        }

        // Simulate successful refund request submission
        // In a real scenario, this might just be an acknowledgement from PG, actual
        // refund happens later.
        mockTransactionStatus.put(pgToken, "REFUND_REQUESTED");
        // We don't reduce mockPaidAmounts here, as the money isn't confirmed back yet.
        // The actual amount refunded might be confirmed via a webhook or another
        // callback.
        logger.info("[Mock PG] Refund request for pgToken: {} processed successfully. Status set to REFUND_REQUESTED.",
                pgToken);
        return true;
    }
}