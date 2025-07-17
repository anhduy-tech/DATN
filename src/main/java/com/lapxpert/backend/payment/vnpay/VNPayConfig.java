package com.lapxpert.backend.payment.vnpay;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/**
 * Enhanced VNPay configuration class with improved security practices,
 * better error handling, and comprehensive validation.
 *
 * Security enhancements:
 * - Secure random number generation
 * - Enhanced IP address detection with proxy support
 * - Improved HMAC-SHA512 implementation with proper error handling
 * - Better parameter validation and encoding
 * - Comprehensive logging for security auditing
 */
@Slf4j
@Component
public class VNPayConfig {
    public static String vnp_PayUrl;
    public static String vnp_Returnurl;
    public static String vnp_TmnCode;
    public static String vnp_HashSecret;
    public static String vnp_apiUrl;

    // Use setter injection for static fields
    @Value("${vnpay.pay-url}")
    public void setVnpPayUrl(String vnpPayUrl) {
        VNPayConfig.vnp_PayUrl = vnpPayUrl;
    }

    @Value("${vnpay.return-url}")
    public void setVnpReturnurl(String vnpReturnurl) {
        VNPayConfig.vnp_Returnurl = vnpReturnurl;
    }

    @Value("${vnpay.tmn-code}")
    public void setVnpTmnCode(String vnpTmnCode) {
        VNPayConfig.vnp_TmnCode = vnpTmnCode;
    }

    @Value("${vnpay.hash-secret}")
    public void setVnpHashSecret(String vnpHashSecret) {
        VNPayConfig.vnp_HashSecret = vnpHashSecret;
    }

    @Value("${vnpay.api-url}")
    public void setVnpApiUrl(String vnpApiUrl) {
        VNPayConfig.vnp_apiUrl = vnpApiUrl;
    }

    /**
     * Initialize and validate VNPay configuration after all properties are injected.
     * Follows ShippingComparisonConfig validation pattern with Vietnamese logging.
     */
    @PostConstruct
    public void init() {
        log.info("Đang khởi tạo cấu hình VNPay...");
        validateConfiguration();
    }

    public static String md5(String message) {
        String digest = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(message.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            digest = sb.toString();
        } catch (UnsupportedEncodingException ex) {
            digest = "";
        } catch (NoSuchAlgorithmException ex) {
            digest = "";
        }
        return digest;
    }

    public static String Sha256(String message) {
        String digest = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(message.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            digest = sb.toString();
        } catch (UnsupportedEncodingException ex) {
            digest = "";
        } catch (NoSuchAlgorithmException ex) {
            digest = "";
        }
        return digest;
    }

    //Util for VNPAY
    public static String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                sb.append(fieldName);
                sb.append("=");
                try {
                    // URL encode the field value to match VNPayService.createOrder() behavior
                    sb.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    // Fallback to original value if encoding fails
                    sb.append(fieldValue);
                }
            }
            if (itr.hasNext()) {
                sb.append("&");
            }
        }
        return hmacSHA512(vnp_HashSecret,sb.toString());
    }

    /**
     * Enhanced HMAC-SHA512 signature generation with proper error handling and validation.
     *
     * @param key Secret key for HMAC generation
     * @param data Data to be signed
     * @return HMAC-SHA512 signature in lowercase hexadecimal format
     * @throws IllegalArgumentException if key or data is null/empty
     */
    public static String hmacSHA512(final String key, final String data) {
        if (key == null || key.trim().isEmpty()) {
            log.error("HMAC-SHA512 key is null or empty");
            throw new IllegalArgumentException("HMAC key cannot be null or empty");
        }

        if (data == null || data.trim().isEmpty()) {
            log.error("HMAC-SHA512 data is null or empty");
            throw new IllegalArgumentException("HMAC data cannot be null or empty");
        }

        try {
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);

            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }

            String signature = sb.toString();
            log.debug("HMAC-SHA512 signature generated successfully");
            return signature;

        } catch (Exception ex) {
            log.error("Error generating HMAC-SHA512 signature: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to generate HMAC-SHA512 signature", ex);
        }
    }

    /**
     * Enhanced IP address detection with support for proxy headers and proper validation.
     *
     * @param request HTTP servlet request
     * @return Client IP address
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            log.warn("HttpServletRequest is null, returning default IP");
            return "127.0.0.1";
        }

        String ipAddress = null;

        try {
            // Check various proxy headers in order of preference
            String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "X-Forwarded",
                "X-Cluster-Client-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
            };

            for (String headerName : headerNames) {
                ipAddress = request.getHeader(headerName);
                if (ipAddress != null && !ipAddress.trim().isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) {
                    // Handle comma-separated IPs (first one is usually the real client IP)
                    if (ipAddress.contains(",")) {
                        ipAddress = ipAddress.split(",")[0].trim();
                    }
                    break;
                }
            }

            // Fallback to remote address
            if (ipAddress == null || ipAddress.trim().isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }

            // Final fallback
            if (ipAddress == null || ipAddress.trim().isEmpty()) {
                ipAddress = "127.0.0.1";
            }

            // Validate IP format (basic validation)
            if (!isValidIPAddress(ipAddress)) {
                log.warn("Invalid IP address detected: {}, using default", ipAddress);
                ipAddress = "127.0.0.1";
            }

            log.debug("Client IP address detected: {}", ipAddress);
            return ipAddress;

        } catch (Exception e) {
            log.error("Error detecting client IP address: {}", e.getMessage(), e);
            return "127.0.0.1";
        }
    }

    /**
     * Basic IP address validation.
     *
     * @param ip IP address to validate
     * @return true if IP address format is valid
     */
    private static boolean isValidIPAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        // Basic IPv4 validation
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
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

        // Basic IPv6 validation (simplified)
        if (ip.contains(":")) {
            return ip.matches("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$") ||
                   ip.matches("^::1$") ||
                   ip.matches("^::$");
        }

        return false;
    }

    /**
     * Generate cryptographically secure random number string.
     *
     * @param len Length of the random number string
     * @return Random numeric string
     * @throws IllegalArgumentException if length is invalid
     */
    public static String getRandomNumber(int len) {
        if (len <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }

        if (len > 50) {
            throw new IllegalArgumentException("Length cannot exceed 50 characters");
        }

        try {
            SecureRandom secureRandom = new SecureRandom();
            String chars = "0123456789";
            StringBuilder sb = new StringBuilder(len);

            for (int i = 0; i < len; i++) {
                sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }

            String randomNumber = sb.toString();
            log.debug("Generated secure random number of length: {}", len);
            return randomNumber;

        } catch (Exception e) {
            log.error("Error generating secure random number: {}", e.getMessage(), e);
            // Fallback to regular Random if SecureRandom fails
            Random rnd = new Random();
            String chars = "0123456789";
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
            return sb.toString();
        }
    }

    /**
     * Enhanced VNPay configuration validation following ShippingComparisonConfig pattern.
     * Validates all configuration properties with comprehensive checks including URL format,
     * secret key strength, and environment-specific validation.
     *
     * @return true if configuration is valid
     */
    public static boolean validateConfiguration() {
        boolean isValid = true;

        log.debug("Đang xác thực cấu hình VNPay:");
        log.debug("TMN Code: {}", vnp_TmnCode != null ? vnp_TmnCode.substring(0, Math.min(8, vnp_TmnCode.length())) + "..." : "null");
        log.debug("Payment URL: {}", vnp_PayUrl);
        log.debug("API URL: {}", vnp_apiUrl);
        log.debug("Return URL: {}", vnp_Returnurl);

        // Validate TMN Code
        if (vnp_TmnCode == null || vnp_TmnCode.trim().isEmpty()) {
            log.error("Mã TMN Code VNPay chưa được cấu hình (giá trị: '{}')", vnp_TmnCode);
            isValid = false;
        } else if (vnp_TmnCode.length() < 8) {
            log.error("Mã TMN Code VNPay quá ngắn (tối thiểu 8 ký tự): {}", vnp_TmnCode.length());
            isValid = false;
        }

        // Validate Hash Secret with strength check
        if (vnp_HashSecret == null || vnp_HashSecret.trim().isEmpty()) {
            log.error("Hash Secret VNPay chưa được cấu hình (giá trị: '{}')", vnp_HashSecret);
            isValid = false;
        } else if (!isValidHashSecret(vnp_HashSecret)) {
            log.error("Hash Secret VNPay không đủ mạnh (tối thiểu 32 ký tự, chứa chữ và số)");
            isValid = false;
        }

        // Validate Payment URL format
        if (vnp_PayUrl == null || vnp_PayUrl.trim().isEmpty()) {
            log.error("URL thanh toán VNPay chưa được cấu hình (giá trị: '{}')", vnp_PayUrl);
            isValid = false;
        } else if (!isValidVNPayUrl(vnp_PayUrl)) {
            log.error("URL thanh toán VNPay không hợp lệ: {}", vnp_PayUrl);
            isValid = false;
        }

        // Validate API URL format
        if (vnp_apiUrl == null || vnp_apiUrl.trim().isEmpty()) {
            log.error("URL API VNPay chưa được cấu hình (giá trị: '{}')", vnp_apiUrl);
            isValid = false;
        } else if (!isValidVNPayUrl(vnp_apiUrl)) {
            log.error("URL API VNPay không hợp lệ: {}", vnp_apiUrl);
            isValid = false;
        }

        // Validate Return URL format
        if (vnp_Returnurl == null || vnp_Returnurl.trim().isEmpty()) {
            log.error("URL trả về VNPay chưa được cấu hình (giá trị: '{}')", vnp_Returnurl);
            isValid = false;
        } else if (!isValidReturnUrl(vnp_Returnurl)) {
            log.error("URL trả về VNPay không hợp lệ: {}", vnp_Returnurl);
            isValid = false;
        }

        // Environment-specific validation
        if (!validateEnvironmentSpecificConfig()) {
            log.error("Cấu hình VNPay không phù hợp với môi trường hiện tại");
            isValid = false;
        }

        // Log validation results in Vietnamese
        if (isValid) {
            log.info("Xác thực cấu hình VNPay thành công");
            log.info("TMN Code: {}, Payment URL: {}",
                vnp_TmnCode != null ? vnp_TmnCode.substring(0, Math.min(8, vnp_TmnCode.length())) + "..." : "null",
                vnp_PayUrl);
            log.info("Cấu hình VNPay đã sẵn sàng cho thanh toán");
        } else {
            log.error("Xác thực cấu hình VNPay thất bại - Vui lòng kiểm tra các thông số cấu hình");
        }

        return isValid;
    }

    /**
     * Validate hash secret strength following security best practices.
     *
     * @param hashSecret Hash secret to validate
     * @return true if hash secret meets security requirements
     */
    private static boolean isValidHashSecret(String hashSecret) {
        if (hashSecret == null || hashSecret.trim().isEmpty()) {
            return false;
        }

        // Minimum length requirement (VNPay typically uses 32+ character secrets)
        if (hashSecret.length() < 32) {
            return false;
        }

        // Must contain both letters and numbers for strength
        boolean hasLetter = hashSecret.matches(".*[a-zA-Z].*");
        boolean hasDigit = hashSecret.matches(".*[0-9].*");

        return hasLetter && hasDigit;
    }

    /**
     * Validate VNPay URL format (payment and API URLs).
     *
     * @param url URL to validate
     * @return true if URL format is valid for VNPay
     */
    private static boolean isValidVNPayUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        // Must be HTTPS for security
        if (!url.startsWith("https://")) {
            return false;
        }

        // Must contain VNPay domain patterns
        return url.contains("vnpay.vn") || url.contains("sandbox.vnpayment.vn");
    }

    /**
     * Validate return URL format.
     *
     * @param returnUrl Return URL to validate
     * @return true if return URL format is valid
     */
    private static boolean isValidReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.trim().isEmpty()) {
            return false;
        }

        // Must be HTTP or HTTPS
        if (!returnUrl.startsWith("http://") && !returnUrl.startsWith("https://")) {
            return false;
        }

        // Should not contain VNPay domains (return URLs should point to merchant)
        return !returnUrl.contains("vnpay.vn") && !returnUrl.contains("sandbox.vnpayment.vn");
    }

    /**
     * Validate environment-specific configuration.
     *
     * @return true if configuration is appropriate for current environment
     */
    private static boolean validateEnvironmentSpecificConfig() {
        // Check if using sandbox URLs in production-like environment
        boolean isSandboxConfig = (vnp_PayUrl != null && vnp_PayUrl.contains("sandbox")) ||
                                 (vnp_apiUrl != null && vnp_apiUrl.contains("sandbox"));

        // For now, we'll allow both sandbox and production configurations
        // In a real environment, you might want to check against environment variables
        // to ensure sandbox is only used in development/testing

        if (isSandboxConfig) {
            log.info("Đang sử dụng cấu hình VNPay sandbox - Phù hợp cho môi trường phát triển/kiểm thử");
        } else {
            log.info("Đang sử dụng cấu hình VNPay production - Phù hợp cho môi trường sản xuất");
        }

        return true; // Allow both configurations for flexibility
    }
}
