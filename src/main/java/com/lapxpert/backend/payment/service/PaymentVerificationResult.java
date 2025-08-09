package com.lapxpert.backend.payment.service;

/**
 * Payment verification result for payment gateway callbacks
 * Extracted from PaymentGatewayService interface to support standalone payment services
 */
public class PaymentVerificationResult {
    private final boolean valid;
    private final boolean successful;
    private final String transactionId;
    private final String orderId;
    private final String errorMessage;
    
    public PaymentVerificationResult(boolean valid, boolean successful, String transactionId, String orderId, String errorMessage) {
        this.valid = valid;
        this.successful = successful;
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.errorMessage = errorMessage;
    }
    
    // Getters
    public boolean isValid() { return valid; }
    public boolean isSuccessful() { return successful; }
    public String getTransactionId() { return transactionId; }
    public String getOrderId() { return orderId; }
    public String getErrorMessage() { return errorMessage; }
    
    // Static factory methods
    public static PaymentVerificationResult success(String transactionId, String orderId) {
        return new PaymentVerificationResult(true, true, transactionId, orderId, null);
    }
    
    public static PaymentVerificationResult failure(String transactionId, String orderId, String errorMessage) {
        return new PaymentVerificationResult(true, false, transactionId, orderId, errorMessage);
    }
    
    public static PaymentVerificationResult invalid(String errorMessage) {
        return new PaymentVerificationResult(false, false, null, null, errorMessage);
    }
}
