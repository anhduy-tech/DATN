package com.lapxpert.backend.payment.controller;

import com.lapxpert.backend.hoadon.service.HoaDonService;
import com.lapxpert.backend.hoadon.enums.PhuongThucThanhToan;
import com.lapxpert.backend.payment.util.PaymentAuditLogger;
import com.lapxpert.backend.payment.vnpay.VNPayConfig;
import com.lapxpert.backend.payment.vnpay.VNPayService;
import com.lapxpert.backend.payment.vnpay.mapper.VNPayErrorCodeMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Simplified VNPay controller for development/graduation project use.
 * Extends BasePaymentController for common payment functionality and Vietnamese business requirements.
 *
 * Development approach:
 * - Order status updated directly in return URL callback (no IPN complexity)
 * - Simplified for academic demonstration without SSL/HTTPS requirements
 * - Enhanced parameter validation through BasePaymentController
 * - Improved error handling with security-conscious responses
 * - Better IP address detection and logging
 * - Proper audit logging for all payment operations
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class VNPayController extends BasePaymentController<VNPayService> {

    private final HoaDonService hoaDonService;

    public VNPayController(VNPayService vnPayService, HoaDonService hoaDonService, PaymentAuditLogger paymentAuditLogger) {
        super(vnPayService, paymentAuditLogger);
        this.hoaDonService = hoaDonService;
    }

    @Override
    protected String getGatewayName() {
        return "VNPay";
    }



    /**
     * Create VNPay payment order with enhanced validation and error handling.
     *
     * @param orderTotal Payment amount in VND (số tiền thanh toán)
     * @param orderInfo Order information (thông tin đơn hàng)
     * @param request HTTP request for IP detection
     * @return Payment URL response
     */
    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestParam("amount") int orderTotal,
            @RequestParam("orderInfo") String orderInfo,
            HttpServletRequest request) {

        try {
            String clientIp = getClientIpAddress(request);
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

            // Validate payment parameters using base controller
            validatePaymentParameters(orderTotal, orderInfo, "VNPay-" + System.currentTimeMillis(), baseUrl);

            // Create audit log for payment creation
            Map<String, Object> auditInfo = new HashMap<>();
            auditInfo.put("userAgent", request.getHeader("User-Agent"));
            auditInfo.put("referer", request.getHeader("Referer"));
            createAuditLog("CREATE_PAYMENT", "VNPay-" + System.currentTimeMillis(), (long) orderTotal, clientIp, auditInfo);

            String vnpayUrl = paymentGatewayService.createOrder(orderTotal, orderInfo, baseUrl);

            // Prepare response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("paymentUrl", vnpayUrl);
            responseData.put("paymentMethod", "VNPAY");

            return createSuccessResponse(responseData, "Tạo liên kết thanh toán VNPay thành công");

        } catch (IllegalArgumentException e) {
            log.warn("Invalid VNPay payment parameters: {}", e.getMessage());
            return createErrorResponse(e.getMessage(), "INVALID_PARAMETERS",
                org.springframework.http.HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error creating VNPay payment order: {}", e.getMessage(), e);
            return createErrorResponse("Không thể tạo liên kết thanh toán VNPay", "PAYMENT_CREATION_FAILED",
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Enhanced payment return handler with improved security and error handling.
     *
     * @param request HTTP request containing VNPay response
     * @param response HTTP response for redirect
     * @throws IOException if redirect fails
     */
    @GetMapping("/vnpay-payment")
    public void handlePaymentReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String clientIp = VNPayConfig.getIpAddress(request);
        String vnp_TxnRef = request.getParameter("vnp_TxnRef");
        String vnp_TransactionNo = request.getParameter("vnp_TransactionNo");

        log.info("VNPay payment return from IP: {} - TxnRef: {}, TransactionNo: {}",
                clientIp, vnp_TxnRef, vnp_TransactionNo);

        try {
            int paymentStatus = paymentGatewayService.orderReturn(request);

            String status;
            if (paymentStatus == 1) {
                status = "success";
                // Get success message using error code mapper
                String successMessage = paymentGatewayService.getUserFriendlyErrorMessage("00");
                log.info("VNPay payment return successful - TxnRef: {} from IP: {} - {}",
                        vnp_TxnRef, clientIp, successMessage);

                // DEVELOPMENT APPROACH: Update order status directly in return URL callback
                // This is simplified for graduation project - no IPN/SSL complexity needed
                try {
                    Long orderId = Long.parseLong(vnp_TxnRef);
                    String vnpAmount = request.getParameter("vnp_Amount");

                    // Check if this is a mixed payment scenario by extracting amount
                    if (vnpAmount != null && !vnpAmount.trim().isEmpty()) {
                        try {
                            // VNPay amounts are in cents, convert to VND
                            long amountInCents = Long.parseLong(vnpAmount);
                            BigDecimal paymentAmount = new BigDecimal(amountInCents).divide(new BigDecimal(100));

                            // Use mixed payment confirmation method with amount
                            hoaDonService.confirmMixedPaymentComponent(orderId, PhuongThucThanhToan.VNPAY,
                                paymentAmount, vnp_TransactionNo);
                            log.info("Order {} VNPay payment component confirmed successfully - Amount: {} VND from IP: {}",
                                    orderId, paymentAmount, clientIp);
                        } catch (NumberFormatException e) {
                            log.error("Invalid amount in VNPay return URL from IP: {} - Amount: {}",
                                     clientIp, vnpAmount);
                            // Fallback to original method
                            hoaDonService.confirmPayment(orderId, PhuongThucThanhToan.VNPAY);
                        }
                    } else {
                        // Fallback to original method if amount is not available
                        hoaDonService.confirmPayment(orderId, PhuongThucThanhToan.VNPAY);
                        log.info("Order {} payment confirmed successfully via VNPay return URL from IP: {} (fallback method)",
                                orderId, clientIp);
                    }
                } catch (NumberFormatException e) {
                    log.error("Invalid order ID in VNPay return URL from IP: {} - TxnRef: {}",
                             clientIp, vnp_TxnRef);
                } catch (Exception e) {
                    log.error("Failed to confirm payment for order {} from IP: {} - {}",
                             vnp_TxnRef, clientIp, e.getMessage(), e);
                }
            } else if (paymentStatus == 0) {
                status = "failed";
                // Get detailed error information using error code mapper
                String responseCode = request.getParameter("vnp_ResponseCode");
                VNPayErrorCodeMapper.ErrorInfo errorInfo = paymentGatewayService.getErrorInfo(responseCode);
                log.warn("VNPay payment return failed - TxnRef: {} from IP: {} - ResponseCode: {} - {} (Severity: {}, Retryable: {})",
                        vnp_TxnRef, clientIp, responseCode, errorInfo.getVietnameseMessage(),
                        errorInfo.getSeverity(), errorInfo.isRetryable());
            } else {
                status = "invalid";
                log.error("VNPay payment return invalid signature - TxnRef: {} from IP: {}", vnp_TxnRef, clientIp);
            }

            // Enhanced URL construction with proper encoding and validation - redirect to payment-return like MoMo
            StringBuilder redirectUrl = new StringBuilder("http://localhost:5173/orders/payment-return?");
            redirectUrl.append("status=").append(status);
            redirectUrl.append("&paymentMethod=VNPAY");

            if (vnp_TxnRef != null) {
                redirectUrl.append("&orderId=").append(URLEncoder.encode(vnp_TxnRef, StandardCharsets.UTF_8));
            }

            if (vnp_TransactionNo != null) {
                redirectUrl.append("&transactionId=").append(URLEncoder.encode(vnp_TransactionNo, StandardCharsets.UTF_8));
            }

            String paymentTime = request.getParameter("vnp_PayDate");
            if (paymentTime != null) {
                redirectUrl.append("&paymentTime=").append(URLEncoder.encode(paymentTime, StandardCharsets.UTF_8));
            }

            String amount = request.getParameter("vnp_Amount");
            if (amount != null) {
                redirectUrl.append("&totalPrice=").append(URLEncoder.encode(amount, StandardCharsets.UTF_8));
            }

            // Add enhanced error information for failed payments - match MoMo pattern
            if ("failed".equals(status)) {
                String responseCode = request.getParameter("vnp_ResponseCode");
                if (responseCode != null) {
                    redirectUrl.append("&resultCode=").append(URLEncoder.encode(responseCode, StandardCharsets.UTF_8));

                    // Add user-friendly error message
                    String userMessage = paymentGatewayService.getUserFriendlyErrorMessage(responseCode);
                    redirectUrl.append("&message=").append(URLEncoder.encode("Thanh toán VNPay thất bại: " + userMessage, StandardCharsets.UTF_8));
                }
            }

            log.info("Redirecting VNPay payment return to frontend - TxnRef: {} from IP: {}", vnp_TxnRef, clientIp);
            response.sendRedirect(redirectUrl.toString());

        } catch (Exception e) {
            log.error("Error handling VNPay payment return - TxnRef: {} from IP: {} - {}",
                     vnp_TxnRef, clientIp, e.getMessage(), e);

            // Redirect to error page - match MoMo pattern
            String errorUrl = "http://localhost:5173/orders/payment-return?status=error&paymentMethod=VNPAY&message=" +
                            URLEncoder.encode("Lỗi xử lý thanh toán VNPay", StandardCharsets.UTF_8);
            response.sendRedirect(errorUrl);
        }
    }

    // IPN endpoint removed for simplified development approach
    // Order status is updated directly in the return URL callback above
    // This avoids SSL/HTTPS requirements needed for production IPN implementation
}
