package com.lapxpert.backend.payment.controller;

import com.lapxpert.backend.common.util.IpAddressUtils;
import com.lapxpert.backend.payment.util.PaymentAuditLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base controller for payment gateways
 * Provides common functionality for VNPay and MoMo controllers
 */
@Slf4j
public abstract class BasePaymentController<T> {

    protected final T paymentGatewayService;
    protected final PaymentAuditLogger paymentAuditLogger;

    protected BasePaymentController(T paymentGatewayService, PaymentAuditLogger paymentAuditLogger) {
        this.paymentGatewayService = paymentGatewayService;
        this.paymentAuditLogger = paymentAuditLogger;
    }

    /**
     * Get the payment gateway name for logging and audit purposes
     * @return Payment gateway name (e.g., "VNPay", "MoMo")
     */
    protected abstract String getGatewayName();



    /**
     * Validate payment amount
     * @param amount Payment amount
     * @throws IllegalArgumentException if amount is invalid
     */
    protected void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }
    }

    /**
     * Validate order information
     * @param orderInfo Order information
     * @throws IllegalArgumentException if order info is invalid
     */
    protected void validateOrderInfo(String orderInfo) {
        if (orderInfo == null || orderInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Order information cannot be null or empty");
        }

        if (orderInfo.length() > 255) {
            throw new IllegalArgumentException("Order information cannot exceed 255 characters");
        }
    }

    /**
     * Validate order ID
     * @param orderId Order ID
     * @throws IllegalArgumentException if order ID is invalid
     */
    protected void validateOrderId(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }
    }

    /**
     * Validate URL
     * @param url URL to validate
     * @param paramName Parameter name for error message
     * @throws IllegalArgumentException if URL is invalid
     */
    protected void validateUrl(String url, String paramName) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException(paramName + " must be a valid HTTP/HTTPS URL");
        }
    }

    /**
     * Validate IP address format (basic validation)
     * @param ipAddress IP address to validate
     * @return true if valid, false otherwise
     */
    protected boolean isValidIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }

        // Basic IPv4 validation
        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validate common payment parameters
     * @param amount Payment amount
     * @param orderInfo Order information
     * @param orderId Order ID
     * @param returnUrl Return URL
     * @throws IllegalArgumentException if validation fails
     */
    protected void validatePaymentParameters(long amount, String orderInfo, String orderId, String returnUrl) {
        log.debug("Validating payment parameters for gateway: {}", getGatewayName());

        validateAmount(amount);
        validateOrderInfo(orderInfo);
        validateOrderId(orderId);
        validateUrl(returnUrl, "Return URL");

        log.debug("Payment parameters validation successful for gateway: {}", getGatewayName());
    }

    /**
     * Get client IP address from request
     * @param request HTTP servlet request
     * @return Client IP address
     */
    protected String getClientIpAddress(HttpServletRequest request) {
        String clientIp = IpAddressUtils.getClientIpAddress(request);
        log.debug("Client IP detected for {}: {}", getGatewayName(), 
                 IpAddressUtils.sanitizeIpForLogging(clientIp));
        return clientIp;
    }

    /**
     * Create audit log entry for payment operations
     * @param operation Operation name (e.g., "CREATE_PAYMENT", "PROCESS_CALLBACK")
     * @param orderId Order ID
     * @param amount Payment amount
     * @param clientIp Client IP address
     * @param additionalInfo Additional information for audit
     */
    protected void createAuditLog(String operation, String orderId, Long amount,
                                String clientIp, Map<String, Object> additionalInfo) {
        paymentAuditLogger.createAuditLog(getGatewayName(), operation, orderId, amount, clientIp, additionalInfo);
    }

    /**
     * Create standardized success response
     * @param data Response data
     * @param message Success message
     * @return ResponseEntity with success response
     */
    protected ResponseEntity<Map<String, Object>> createSuccessResponse(Object data, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("gateway", getGatewayName());
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Create standardized error response
     * @param message Error message
     * @param errorCode Error code
     * @param httpStatus HTTP status
     * @return ResponseEntity with error response
     */
    protected ResponseEntity<Map<String, Object>> createErrorResponse(String message, String errorCode, HttpStatus httpStatus) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("errorCode", errorCode);
        response.put("gateway", getGatewayName());
        response.put("timestamp", LocalDateTime.now());
        
        log.error("Payment error response - Gateway: {}, Error: {}, Code: {}", 
                 getGatewayName(), message, errorCode);
        
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * Global exception handler for payment operations
     * @param e Exception
     * @return Error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Map<String, Object>> handleValidationException(IllegalArgumentException e) {
        return createErrorResponse(e.getMessage(), "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
    }

    /**
     * Global exception handler for general exceptions
     * @param e Exception
     * @return Error response
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unexpected error in {} payment gateway", getGatewayName(), e);
        return createErrorResponse("Internal server error", "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Validate callback/IPN request
     * @param request HTTP servlet request
     * @return true if request is valid, false otherwise
     */
    protected boolean validateCallbackRequest(HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        
        // Log callback attempt
        log.info("Callback request received for {} from IP: {}", 
                getGatewayName(), IpAddressUtils.sanitizeIpForLogging(clientIp));
        return true;
    }
}
