package com.lapxpert.backend.payment.controller;

import com.lapxpert.backend.hoadon.enums.PhuongThucThanhToan;
import com.lapxpert.backend.hoadon.service.HoaDonService;
import com.lapxpert.backend.payment.service.MoMoService;
import com.lapxpert.backend.payment.service.PaymentVerificationResult;
import com.lapxpert.backend.payment.util.PaymentAuditLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Simplified MoMo controller for development/graduation project use.
 * Extends BasePaymentController for common payment functionality and Vietnamese business requirements.
 *
 * This controller is conditionally registered based on the momo.sdk.enabled property.
 * When disabled, MoMo payment endpoints will not be available.
 *
 * Development approach:
 * - Order status updated directly in return URL callback (no IPN complexity)
 * - Simplified for academic demonstration without SSL/HTTPS requirements
 * - SDK-based signature validation and verification
 * - Enhanced parameter validation through BasePaymentController
 * - Improved error handling with security-conscious responses
 * - Better IP address detection and logging
 * - Proper audit logging for all payment operations
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "momo.sdk.enabled", havingValue = "true", matchIfMissing = false)
public class MoMoController extends BasePaymentController<MoMoService> {

    private final HoaDonService hoaDonService;

    public MoMoController(MoMoService moMoGatewayService, HoaDonService hoaDonService, PaymentAuditLogger paymentAuditLogger) {
        super(moMoGatewayService, paymentAuditLogger);
        this.hoaDonService = hoaDonService;
        log.info("MoMo Controller initialized with SDK integration and enhanced security");
    }

    @Override
    protected String getGatewayName() {
        return "MoMo";
    }



    /**
     * Enhanced payment return handler for MoMo payment completion.
     * Handles user return from MoMo payment page with improved security and error handling.
     *
     * @param request HTTP request containing MoMo response parameters
     * @param response HTTP response for redirect
     * @throws IOException if redirect fails
     */
    @GetMapping("/momo-payment")
    public void handlePaymentReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clientIp = getClientIpAddress(request);
        String orderId = request.getParameter("orderId");
        String transId = request.getParameter("transId");
        String resultCode = request.getParameter("resultCode");
        String message = request.getParameter("message");
        String amount = request.getParameter("amount");

        log.info("MoMo payment return from IP: {} - OrderId: {}, TransId: {}, ResultCode: {}, Amount: {}",
                clientIp, orderId, transId, resultCode, amount);

        try {
            // Create audit log for payment return
            Map<String, Object> auditInfo = new HashMap<>();
            auditInfo.put("transId", transId);
            auditInfo.put("resultCode", resultCode);
            auditInfo.put("message", message);
            auditInfo.put("returnType", "USER_RETURN");
            createAuditLog("PROCESS_RETURN", orderId, null, clientIp, auditInfo);

            // Prepare callback data for verification
            Map<String, String> callbackData = new HashMap<>();
            callbackData.put("orderId", orderId);
            callbackData.put("transId", transId);
            callbackData.put("resultCode", resultCode);
            callbackData.put("message", message);

            // Verify payment using SDK
            PaymentVerificationResult verificationResult =
                paymentGatewayService.verifyPaymentWithCallback(callbackData);

            if (verificationResult.isValid() && verificationResult.isSuccessful()) {
                // Payment successful - redirect to PaymentReturn.vue with proper parameters
                try {
                    Long originalOrderId = ((MoMoService) paymentGatewayService).extractOrderIdFromMoMoOrderId(orderId);

                    // DEVELOPMENT APPROACH: Update order status directly in return URL callback
                    // This is simplified for graduation project - no IPN/SSL complexity needed
                    try {
                        // Check if this is a mixed payment scenario by extracting amount
                        if (amount != null && !amount.trim().isEmpty()) {
                            try {
                                // MoMo amounts are in VND (not cents like VNPay)
                                BigDecimal paymentAmount = new BigDecimal(amount);

                                // Use mixed payment confirmation method with amount
                                hoaDonService.confirmMixedPaymentComponent(originalOrderId, PhuongThucThanhToan.MOMO,
                                    paymentAmount, transId);
                                log.info("Order {} MoMo payment component confirmed successfully - Amount: {} VND from IP: {} (MoMo OrderId: {})",
                                        originalOrderId, paymentAmount, clientIp, orderId);
                            } catch (NumberFormatException e) {
                                log.error("Invalid amount in MoMo return URL from IP: {} - Amount: {}",
                                         clientIp, amount);
                                // Fallback to original method
                                hoaDonService.confirmPayment(originalOrderId, PhuongThucThanhToan.MOMO);
                            }
                        } else {
                            // Fallback to original method if amount is not available
                            hoaDonService.confirmPayment(originalOrderId, PhuongThucThanhToan.MOMO);
                            log.info("Order {} payment confirmed successfully via MoMo return URL from IP: {} (MoMo OrderId: {}) (fallback method)",
                                    originalOrderId, clientIp, orderId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to confirm payment for order {} from IP: {} - {}",
                                 originalOrderId, clientIp, e.getMessage(), e);
                    }

                    // Build success redirect URL with all necessary parameters for PaymentReturn.vue
                    StringBuilder successUrlBuilder = new StringBuilder("http://localhost:5173/orders/payment-return");
                    successUrlBuilder.append("?status=success");
                    successUrlBuilder.append("&orderId=").append(originalOrderId);
                    successUrlBuilder.append("&paymentMethod=MOMO");
                    successUrlBuilder.append("&resultCode=0");
                    if (transId != null) {
                        successUrlBuilder.append("&transactionId=").append(URLEncoder.encode(transId, StandardCharsets.UTF_8));
                    }
                    if (message != null) {
                        successUrlBuilder.append("&message=").append(URLEncoder.encode(message, StandardCharsets.UTF_8));
                    }

                    String successRedirectUrl = successUrlBuilder.toString();
                    response.sendRedirect(successRedirectUrl);
                    log.info("MoMo payment successful for order {} - redirecting to PaymentReturn page (MoMo OrderId: {})", originalOrderId, orderId);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid order ID format in MoMo return - MoMo OrderId: {} - Error: {}", orderId, e.getMessage());
                    String errorRedirectUrl = "http://localhost:5173/orders/payment-return?status=error&paymentMethod=MOMO&message=" +
                        URLEncoder.encode("Lỗi định dạng mã đơn hàng", StandardCharsets.UTF_8);
                    response.sendRedirect(errorRedirectUrl);
                }
            } else {
                // Payment failed - redirect to PaymentReturn.vue with error parameters
                String errorMessage = verificationResult.getErrorMessage() != null ?
                    verificationResult.getErrorMessage() : "Thanh toán MoMo thất bại";

                StringBuilder errorUrlBuilder = new StringBuilder("http://localhost:5173/orders/payment-return");
                errorUrlBuilder.append("?status=error");
                errorUrlBuilder.append("&paymentMethod=MOMO");
                errorUrlBuilder.append("&message=").append(URLEncoder.encode(errorMessage, StandardCharsets.UTF_8));
                if (resultCode != null) {
                    errorUrlBuilder.append("&resultCode=").append(URLEncoder.encode(resultCode, StandardCharsets.UTF_8));
                }
                if (transId != null) {
                    errorUrlBuilder.append("&transactionId=").append(URLEncoder.encode(transId, StandardCharsets.UTF_8));
                }

                String errorRedirectUrl = errorUrlBuilder.toString();
                response.sendRedirect(errorRedirectUrl);
                log.warn("MoMo payment failed for order {} - redirecting to PaymentReturn page: {}", orderId, errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Error handling MoMo payment return for order {}: {}", orderId, e.getMessage(), e);
            String errorRedirectUrl = "http://localhost:5173/orders/payment-return?status=error&paymentMethod=MOMO&message=" +
                URLEncoder.encode("Lỗi xử lý thanh toán MoMo", StandardCharsets.UTF_8);
            response.sendRedirect(errorRedirectUrl);
        }
    }

    // IPN endpoint removed for simplified development approach
    // Order status is updated directly in the return URL callback above
    // This avoids SSL/HTTPS requirements needed for production IPN implementation
}
