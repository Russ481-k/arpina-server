package cms.pg.service;

public interface PaymentGatewayService {
    /**
     * Verifies a payment with the payment gateway.
     * 
     * @param pgToken        The token received from the PG after user payment.
     * @param merchantUid    The merchant's unique ID for the transaction.
     * @param expectedAmount The expected amount for verification.
     * @return true if payment is verified, false otherwise.
     */
    boolean verifyPayment(String pgToken, String merchantUid, Integer expectedAmount);

    /**
     * Gets the actual paid amount for a verified transaction.
     * Call after verifyPayment is successful.
     * 
     * @param pgToken The token received from the PG.
     * @return The actual amount paid.
     */
    Integer getPaidAmount(String pgToken);

    /**
     * Requests a refund from the payment gateway.
     * 
     * @param pgToken     The original payment token.
     * @param merchantUid The merchant's unique ID for the transaction to be
     *                    refunded.
     * @param amount      The amount to refund (can be partial).
     * @param reason      The reason for the refund.
     * @return true if the refund request was successfully submitted, false
     *         otherwise.
     */
    boolean requestRefund(String pgToken, String merchantUid, Integer amount, String reason);
}