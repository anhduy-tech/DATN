package com.lapxpert.backend.payment.vnpay.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive VNPay error code mapper for business-specific error messages.
 * Maps all VNPay response codes to Vietnamese business terminology following
 * LapXpert standards and BasePaymentController error response patterns.
 * 
 * This addresses the API compliance gap where limited error code mapping exists
 * for VNPay response codes, providing better user experience with clear Vietnamese
 * error messages and appropriate error severity classification.
 */
@Slf4j
@Component
public class VNPayErrorCodeMapper {

    /**
     * Error severity levels for classification and handling
     */
    public enum ErrorSeverity {
        INFO,       // Informational messages (success, processing)
        WARNING,    // Warning messages (suspicious transactions, partial success)
        CRITICAL    // Critical errors (failures, security issues)
    }

    /**
     * Error information container with Vietnamese business message and severity
     */
    public static class ErrorInfo {
        private final String vietnameseMessage;
        private final String userFriendlyMessage;
        private final ErrorSeverity severity;
        private final String technicalDescription;
        private final boolean retryable;

        public ErrorInfo(String vietnameseMessage, String userFriendlyMessage, 
                        ErrorSeverity severity, String technicalDescription, boolean retryable) {
            this.vietnameseMessage = vietnameseMessage;
            this.userFriendlyMessage = userFriendlyMessage;
            this.severity = severity;
            this.technicalDescription = technicalDescription;
            this.retryable = retryable;
        }

        // Getters
        public String getVietnameseMessage() { return vietnameseMessage; }
        public String getUserFriendlyMessage() { return userFriendlyMessage; }
        public ErrorSeverity getSeverity() { return severity; }
        public String getTechnicalDescription() { return technicalDescription; }
        public boolean isRetryable() { return retryable; }
    }

    /**
     * Comprehensive VNPay response code mapping based on official VNPay documentation.
     * All messages follow Vietnamese business terminology standards.
     */
    private static final Map<String, ErrorInfo> ERROR_CODE_MAP = new HashMap<>();

    static {
        // Success codes
        ERROR_CODE_MAP.put("00", new ErrorInfo(
            "Giao dịch thành công",
            "Thanh toán đã được xử lý thành công",
            ErrorSeverity.INFO,
            "Transaction completed successfully",
            false
        ));

        // Warning codes - Suspicious or partial success
        ERROR_CODE_MAP.put("07", new ErrorInfo(
            "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên hệ CSKH VNPay)",
            "Giao dịch thành công nhưng cần xác minh thêm. Vui lòng liên hệ hỗ trợ nếu có thắc mắc",
            ErrorSeverity.WARNING,
            "Money deducted successfully but transaction is suspicious",
            false
        ));

        ERROR_CODE_MAP.put("09", new ErrorInfo(
            "Giao dịch không thành công do: Thẻ/Tài khoản của khách hàng chưa đăng ký dịch vụ InternetBanking tại ngân hàng",
            "Thẻ/Tài khoản chưa đăng ký dịch vụ thanh toán trực tuyến. Vui lòng liên hệ ngân hàng để kích hoạt",
            ErrorSeverity.CRITICAL,
            "Card/Account not registered for InternetBanking service",
            false
        ));

        ERROR_CODE_MAP.put("10", new ErrorInfo(
            "Giao dịch không thành công do: Khách hàng xác thực thông tin thẻ/tài khoản không đúng quá 3 lần",
            "Xác thực thông tin không đúng quá 3 lần. Vui lòng thử lại sau hoặc liên hệ ngân hàng",
            ErrorSeverity.CRITICAL,
            "Customer authentication failed more than 3 times",
            true
        ));

        ERROR_CODE_MAP.put("11", new ErrorInfo(
            "Giao dịch không thành công do: Đã hết hạn chờ thanh toán. Xin quý khách vui lòng thực hiện lại giao dịch",
            "Phiên thanh toán đã hết hạn. Vui lòng tạo yêu cầu thanh toán mới",
            ErrorSeverity.WARNING,
            "Payment session expired",
            true
        ));

        ERROR_CODE_MAP.put("12", new ErrorInfo(
            "Giao dịch không thành công do: Thẻ/Tài khoản của khách hàng bị khóa",
            "Thẻ/Tài khoản đã bị khóa. Vui lòng liên hệ ngân hàng để được hỗ trợ",
            ErrorSeverity.CRITICAL,
            "Card/Account is locked",
            false
        ));

        ERROR_CODE_MAP.put("13", new ErrorInfo(
            "Giao dịch không thành công do: Quý khách nhập sai mật khẩu xác thực giao dịch (OTP)",
            "Mật khẩu OTP không đúng. Vui lòng kiểm tra lại mã OTP từ ngân hàng",
            ErrorSeverity.WARNING,
            "Incorrect OTP password",
            true
        ));

        ERROR_CODE_MAP.put("24", new ErrorInfo(
            "Giao dịch không thành công do: Khách hàng hủy giao dịch",
            "Giao dịch đã bị hủy theo yêu cầu của khách hàng",
            ErrorSeverity.INFO,
            "Customer cancelled the transaction",
            true
        ));

        ERROR_CODE_MAP.put("51", new ErrorInfo(
            "Giao dịch không thành công do: Tài khoản của quý khách không đủ số dư để thực hiện giao dịch",
            "Tài khoản không đủ số dư. Vui lòng nạp thêm tiền hoặc chọn phương thức thanh toán khác",
            ErrorSeverity.CRITICAL,
            "Insufficient account balance",
            false
        ));

        ERROR_CODE_MAP.put("65", new ErrorInfo(
            "Giao dịch không thành công do: Tài khoản của Quý khách đã vượt quá hạn mức giao dịch trong ngày",
            "Đã vượt quá hạn mức giao dịch trong ngày. Vui lòng thử lại vào ngày hôm sau hoặc liên hệ ngân hàng",
            ErrorSeverity.WARNING,
            "Daily transaction limit exceeded",
            true
        ));

        ERROR_CODE_MAP.put("75", new ErrorInfo(
            "Ngân hàng thanh toán đang bảo trì",
            "Ngân hàng đang trong thời gian bảo trì. Vui lòng thử lại sau hoặc chọn phương thức thanh toán khác",
            ErrorSeverity.WARNING,
            "Payment bank is under maintenance",
            true
        ));

        ERROR_CODE_MAP.put("79", new ErrorInfo(
            "Giao dịch không thành công do: KH nhập sai mật khẩu thanh toán quá số lần quy định",
            "Nhập sai mật khẩu quá số lần cho phép. Vui lòng thử lại sau hoặc liên hệ ngân hàng",
            ErrorSeverity.CRITICAL,
            "Payment password entered incorrectly too many times",
            true
        ));

        // System and technical errors
        ERROR_CODE_MAP.put("99", new ErrorInfo(
            "Các lỗi khác (lỗi còn lại, không có trong danh sách mã lỗi đã liệt kê)",
            "Có lỗi xảy ra trong quá trình xử lý. Vui lòng thử lại hoặc liên hệ hỗ trợ",
            ErrorSeverity.CRITICAL,
            "Other errors not listed in error code list",
            true
        ));
    }

    /**
     * Get error information for a VNPay response code.
     * 
     * @param responseCode VNPay response code
     * @return ErrorInfo containing Vietnamese message and severity, or default error if code not found
     */
    public ErrorInfo getErrorInfo(String responseCode) {
        if (responseCode == null || responseCode.trim().isEmpty()) {
            log.warn("VNPay response code is null or empty, returning default error");
            return getDefaultError();
        }

        ErrorInfo errorInfo = ERROR_CODE_MAP.get(responseCode.trim());
        if (errorInfo == null) {
            log.warn("Unknown VNPay response code: {}, returning default error", responseCode);
            return getUnknownCodeError(responseCode);
        }

        log.debug("VNPay error code {} mapped to: {} (Severity: {})", 
                 responseCode, errorInfo.getVietnameseMessage(), errorInfo.getSeverity());
        
        return errorInfo;
    }

    /**
     * Get user-friendly Vietnamese error message for display.
     * 
     * @param responseCode VNPay response code
     * @return User-friendly Vietnamese error message
     */
    public String getUserFriendlyMessage(String responseCode) {
        return getErrorInfo(responseCode).getUserFriendlyMessage();
    }

    /**
     * Get technical Vietnamese message for logging and debugging.
     * 
     * @param responseCode VNPay response code
     * @return Technical Vietnamese message
     */
    public String getVietnameseMessage(String responseCode) {
        return getErrorInfo(responseCode).getVietnameseMessage();
    }

    /**
     * Get error severity for the response code.
     * 
     * @param responseCode VNPay response code
     * @return Error severity level
     */
    public ErrorSeverity getErrorSeverity(String responseCode) {
        return getErrorInfo(responseCode).getSeverity();
    }

    /**
     * Check if the error is retryable.
     * 
     * @param responseCode VNPay response code
     * @return true if the error condition might be temporary and retryable
     */
    public boolean isRetryable(String responseCode) {
        return getErrorInfo(responseCode).isRetryable();
    }

    /**
     * Check if the response code indicates success.
     * 
     * @param responseCode VNPay response code
     * @return true if the response code indicates successful transaction
     */
    public boolean isSuccess(String responseCode) {
        return "00".equals(responseCode);
    }

    /**
     * Check if the response code indicates a critical error.
     * 
     * @param responseCode VNPay response code
     * @return true if the error is critical and requires immediate attention
     */
    public boolean isCriticalError(String responseCode) {
        return getErrorSeverity(responseCode) == ErrorSeverity.CRITICAL;
    }

    /**
     * Default error for null or empty response codes.
     */
    private ErrorInfo getDefaultError() {
        return new ErrorInfo(
            "Mã phản hồi VNPay không hợp lệ",
            "Có lỗi xảy ra trong quá trình xử lý thanh toán. Vui lòng thử lại",
            ErrorSeverity.CRITICAL,
            "Invalid or missing VNPay response code",
            true
        );
    }

    /**
     * Error for unknown response codes.
     */
    private ErrorInfo getUnknownCodeError(String responseCode) {
        return new ErrorInfo(
            "Mã lỗi VNPay không xác định: " + responseCode,
            "Có lỗi không xác định xảy ra. Vui lòng thử lại hoặc liên hệ hỗ trợ",
            ErrorSeverity.CRITICAL,
            "Unknown VNPay response code: " + responseCode,
            true
        );
    }
}
