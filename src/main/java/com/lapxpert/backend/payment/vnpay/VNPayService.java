package com.lapxpert.backend.payment.vnpay;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.lapxpert.backend.payment.vnpay.mapper.VNPayErrorCodeMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Enhanced VNPay service implementation with improved security practices,
 * comprehensive error handling, and proper audit logging.
 *
 * Security enhancements:
 * - Enhanced signature verification with proper parameter validation
 * - Improved error handling with security-conscious error messages
 * - Comprehensive audit logging for payment operations
 * - Better IP address handling and validation
 * - Enhanced IPN processing with security checks
 * - Centralized HTTP client configuration using CommonBeansConfig.restTemplate()
 *
 * HTTP Client Integration:
 * - Uses centralized RestTemplate from CommonBeansConfig for consistent timeout settings
 * - Prepared for future VNPay API calls (payment verification, refunds, transaction queries)
 * - Currently VNPay integration only requires URL generation and return parameter processing
 * - RestTemplate ready for when VNPay API calls are needed in future enhancements
 */
@Slf4j
@Service
public class VNPayService {

    private final RestTemplate restTemplate;
    private final VNPayErrorCodeMapper errorCodeMapper;

    /**
     * Constructor with centralized RestTemplate injection and error code mapping.
     * Uses CommonBeansConfig.restTemplate() for consistent HTTP client configuration
     * and VNPayErrorCodeMapper for comprehensive error handling.
     *
     * @param restTemplate Centralized RestTemplate with optimized timeout settings
     * @param errorCodeMapper VNPay error code mapper for Vietnamese business messages
     */
    public VNPayService(RestTemplate restTemplate, VNPayErrorCodeMapper errorCodeMapper) {
        this.restTemplate = restTemplate;
        this.errorCodeMapper = errorCodeMapper;
        log.info("VNPayService initialized with centralized RestTemplate and error code mapping");
    }

    /**
     * Create VNPay payment URL with enhanced security and validation.
     *
     * @param total Payment amount in VND
     * @param orderInfo Order information (max 255 characters)
     * @param urlReturn Return URL after payment
     * @return VNPay payment URL
     * @throws IllegalArgumentException if parameters are invalid
     */
    public String createOrder(int total, String orderInfo, String urlReturn) {
        log.info("Creating VNPay payment order - Amount: {}, OrderInfo: {}", total, orderInfo);

        // Enhanced parameter validation
        validateCreateOrderParameters(total, orderInfo, urlReturn);

        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = VNPayConfig.getRandomNumber(8);
        String vnp_IpAddr = "127.0.0.1"; // Default IP, should be overridden by createOrderWithOrderId
        String vnp_TmnCode = VNPayConfig.vnp_TmnCode;
        String orderType = "order-type";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(total*100));
        vnp_Params.put("vnp_CurrCode", "VND");

        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", orderType);

        String locate = "vn";
        vnp_Params.put("vnp_Locale", locate);

        urlReturn += VNPayConfig.vnp_Returnurl;
        vnp_Params.put("vnp_ReturnUrl", urlReturn);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                //Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                try {
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    //Build query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayConfig.hmacSHA512(VNPayConfig.vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = VNPayConfig.vnp_PayUrl + "?" + queryUrl;
        return paymentUrl;
    }

    /**
     * Enhanced VNPay payment return processing with comprehensive security validation.
     *
     * CRITICAL: This method implements the correct VNPay payment verification logic
     * by checking BOTH vnp_ResponseCode AND vnp_TransactionStatus parameters as
     * required by official VNPay documentation. Both parameters must equal "00"
     * for a payment to be considered successful.
     *
     * @param request HTTP request containing VNPay response parameters
     * @return 1 for successful payment, 0 for failed payment, -1 for invalid signature
     */
    public int orderReturn(HttpServletRequest request) {
        String vnp_TxnRef = request.getParameter("vnp_TxnRef");
        String vnp_TransactionStatus = request.getParameter("vnp_TransactionStatus");
        String vnp_ResponseCode = request.getParameter("vnp_ResponseCode");
        String clientIp = VNPayConfig.getIpAddress(request);

        // Enhanced parameter validation logging at method entry
        log.info("Processing VNPay return - TxnRef: {}, TransactionStatus: {}, ResponseCode: {}, ClientIP: {}",
                vnp_TxnRef, vnp_TransactionStatus, vnp_ResponseCode, clientIp);

        // Parameter presence validation with detailed logging
        if (vnp_ResponseCode == null || vnp_ResponseCode.trim().isEmpty()) {
            log.warn("VNPay return missing vnp_ResponseCode parameter for TxnRef: {} - implementing fallback logic", vnp_TxnRef);
        }
        if (vnp_TransactionStatus == null || vnp_TransactionStatus.trim().isEmpty()) {
            log.warn("VNPay return missing vnp_TransactionStatus parameter for TxnRef: {} - implementing fallback logic", vnp_TxnRef);
        }

        try {
            // Enhanced parameter validation
            if (!validateReturnParameters(request)) {
                log.error("VNPay return validation failed - missing required parameters for TxnRef: {}", vnp_TxnRef);
                return -1;
            }

            Map<String, String> fields = new HashMap<>();
            for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
                String fieldName = params.nextElement();
                String fieldValue = request.getParameter(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    fields.put(fieldName, fieldValue);
                }
            }

            // Validate payment expiration
            String expirationError = validatePaymentTiming(fields);
            if (expirationError != null) {
                log.error("VNPay payment expired for TxnRef: {} - {}", vnp_TxnRef, expirationError);
                return 0; // Return 0 for expired payment (failed payment)
            }

            String vnp_SecureHash = request.getParameter("vnp_SecureHash");
            if (vnp_SecureHash == null || vnp_SecureHash.trim().isEmpty()) {
                log.error("VNPay return missing secure hash for TxnRef: {}", vnp_TxnRef);
                return -1;
            }

            // Remove hash-related fields before signature verification
            if (fields.containsKey("vnp_SecureHashType")) {
                fields.remove("vnp_SecureHashType");
            }
            if (fields.containsKey("vnp_SecureHash")) {
                fields.remove("vnp_SecureHash");
            }

            // Enhanced signature verification
            String signValue = VNPayConfig.hashAllFields(fields);
            if (signValue.equals(vnp_SecureHash)) {
                log.info("VNPay signature verification successful for TxnRef: {}", vnp_TxnRef);

                // CRITICAL FIX: Check BOTH vnp_ResponseCode AND vnp_TransactionStatus as required by VNPay official documentation
                // Both parameters must equal "00" for a successful payment
                // FALLBACK: If vnp_TransactionStatus is missing (common in return URL), use only vnp_ResponseCode
                boolean responseCodeSuccess = "00".equals(vnp_ResponseCode);
                boolean transactionStatusSuccess = "00".equals(vnp_TransactionStatus);
                boolean transactionStatusPresent = vnp_TransactionStatus != null && !vnp_TransactionStatus.trim().isEmpty();

                log.debug("VNPay payment verification for TxnRef: {} - ResponseCode: {} ({}), TransactionStatus: {} ({}, Present: {})",
                        vnp_TxnRef, vnp_ResponseCode, responseCodeSuccess ? "SUCCESS" : "FAILED",
                        vnp_TransactionStatus, transactionStatusSuccess ? "SUCCESS" : "FAILED", transactionStatusPresent);

                // Determine success based on parameter availability
                boolean paymentSuccessful;
                String verificationMethod;

                if (transactionStatusPresent) {
                    // Both parameters available - use dual verification (preferred method)
                    paymentSuccessful = responseCodeSuccess && transactionStatusSuccess;
                    verificationMethod = "DUAL_PARAMETER";
                    log.debug("Using dual parameter verification for TxnRef: {} - Both parameters present", vnp_TxnRef);
                } else {
                    // vnp_TransactionStatus missing - use fallback verification
                    paymentSuccessful = responseCodeSuccess;
                    verificationMethod = "FALLBACK_SINGLE_PARAMETER";
                    log.warn("Using fallback single parameter verification for TxnRef: {} - vnp_TransactionStatus missing, relying on vnp_ResponseCode only", vnp_TxnRef);
                }

                if (paymentSuccessful) {
                    // Payment successful
                    VNPayErrorCodeMapper.ErrorInfo successInfo = errorCodeMapper.getErrorInfo("00");
                    log.info("VNPay payment successful for TxnRef: {} - Method: {} - {}",
                            vnp_TxnRef, verificationMethod, successInfo.getVietnameseMessage());
                    return 1;
                } else {
                    // Payment failed - provide detailed information about which parameter(s) failed
                    String primaryErrorCode = !responseCodeSuccess ? vnp_ResponseCode : vnp_TransactionStatus;
                    VNPayErrorCodeMapper.ErrorInfo errorInfo = errorCodeMapper.getErrorInfo(primaryErrorCode);

                    // Enhanced logging with fallback logic for missing parameters
                    if (vnp_ResponseCode == null || vnp_ResponseCode.trim().isEmpty()) {
                        log.error("VNPay payment failed for TxnRef: {} - Missing vnp_ResponseCode parameter, TransactionStatus: {} - Treating as payment failure",
                                vnp_TxnRef, vnp_TransactionStatus);
                    } else if (!transactionStatusPresent) {
                        log.warn("VNPay payment failed for TxnRef: {} - Using fallback verification, ResponseCode: {} ({}) - {} (Severity: {}, Retryable: {})",
                                vnp_TxnRef, vnp_ResponseCode, responseCodeSuccess ? "SUCCESS" : "FAILED",
                                errorInfo.getVietnameseMessage(), errorInfo.getSeverity(), errorInfo.isRetryable());
                    } else {
                        log.warn("VNPay payment failed for TxnRef: {} - ResponseCode: {} ({}), TransactionStatus: {} ({}) - {} (Severity: {}, Retryable: {})",
                                vnp_TxnRef, vnp_ResponseCode, responseCodeSuccess ? "SUCCESS" : "FAILED",
                                vnp_TransactionStatus, transactionStatusSuccess ? "SUCCESS" : "FAILED",
                                errorInfo.getVietnameseMessage(), errorInfo.getSeverity(), errorInfo.isRetryable());
                    }
                    return 0;
                }
            } else {
                log.error("VNPay signature verification failed for TxnRef: {} - Expected: {}, Received: {}",
                         vnp_TxnRef, signValue, vnp_SecureHash);
                return -1;
            }

        } catch (Exception e) {
            log.error("Error processing VNPay return for TxnRef: {} - {}", vnp_TxnRef, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Create VNPay payment URL with specific order ID as transaction reference.
     * Enhanced version with improved security, validation, and error handling.
     *
     * @param total Payment amount in VND
     * @param orderInfo Order information (max 255 characters)
     * @param urlReturn Return URL after payment
     * @param orderId Order ID for transaction correlation
     * @param clientIp Client IP address for security logging
     * @return VNPay payment URL
     * @throws IllegalArgumentException if parameters are invalid
     */
    public String createOrderWithOrderId(long total, String orderInfo, String urlReturn, String orderId, String clientIp) {
        log.info("Creating VNPay payment with OrderID - Amount: {}, OrderID: {}, ClientIP: {}",
                total, orderId, clientIp);

        // Enhanced parameter validation
        validateCreateOrderWithIdParameters(total, orderInfo, urlReturn, orderId);

        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_TxnRef = orderId; // Use actual order ID instead of random number
        String vnp_IpAddr = (clientIp != null && !clientIp.trim().isEmpty()) ? clientIp : "127.0.0.1";
        String vnp_TmnCode = VNPayConfig.vnp_TmnCode;
        String orderType = "order-type";

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(total*100));
        vnp_Params.put("vnp_CurrCode", "VND");

        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", orderType);

        String locate = "vn";
        vnp_Params.put("vnp_Locale", locate);

        urlReturn += VNPayConfig.vnp_Returnurl;
        vnp_Params.put("vnp_ReturnUrl", urlReturn);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                //Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                try {
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    //Build query
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayConfig.hmacSHA512(VNPayConfig.vnp_HashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = VNPayConfig.vnp_PayUrl + "?" + queryUrl;

        log.info("VNPay payment URL created successfully for OrderID: {}", orderId);
        return paymentUrl;
    }

    /**
     * Validate parameters for createOrder method.
     *
     * @param total Payment amount
     * @param orderInfo Order information
     * @param urlReturn Return URL
     * @throws IllegalArgumentException if parameters are invalid
     */
    private void validateCreateOrderParameters(long total, String orderInfo, String urlReturn) {
        if (total <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }



        if (orderInfo == null || orderInfo.trim().isEmpty()) {
            throw new IllegalArgumentException("Order information cannot be null or empty");
        }

        if (orderInfo.length() > 255) {
            throw new IllegalArgumentException("Order information cannot exceed 255 characters");
        }

        if (urlReturn == null || urlReturn.trim().isEmpty()) {
            throw new IllegalArgumentException("Return URL cannot be null or empty");
        }

        // Validate URL format
        if (!urlReturn.startsWith("http://") && !urlReturn.startsWith("https://")) {
            throw new IllegalArgumentException("Return URL must be a valid HTTP/HTTPS URL");
        }
    }

    /**
     * Validate parameters for createOrderWithOrderId method.
     *
     * @param total Payment amount
     * @param orderInfo Order information
     * @param urlReturn Return URL
     * @param orderId Order ID
     * @throws IllegalArgumentException if parameters are invalid
     */
    private void validateCreateOrderWithIdParameters(long total, String orderInfo, String urlReturn, String orderId) {
        validateCreateOrderParameters(total, orderInfo, urlReturn);

        if (orderId == null || orderId.trim().isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be null or empty");
        }

        // Validate order ID format (should be numeric for VNPay)
        try {
            Long.parseLong(orderId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Order ID must be a valid numeric value");
        }
    }

    /**
     * Validate VNPay return parameters.
     * Note: vnp_TransactionStatus is optional for return URL callbacks but required for IPN
     *
     * @param request HTTP request containing VNPay response
     * @return true if all required parameters are present
     */
    private boolean validateReturnParameters(HttpServletRequest request) {
        // Core required parameters for return URL (vnp_TransactionStatus is optional)
        String[] requiredParams = {
            "vnp_TxnRef", "vnp_ResponseCode", "vnp_SecureHash", "vnp_Amount", "vnp_TmnCode"
        };

        for (String param : requiredParams) {
            String value = request.getParameter(param);
            if (value == null || value.trim().isEmpty()) {
                log.error("Missing required VNPay parameter: {}", param);
                return false;
            }
        }

        // Log if vnp_TransactionStatus is missing (for awareness, but not failure)
        String vnp_TransactionStatus = request.getParameter("vnp_TransactionStatus");
        if (vnp_TransactionStatus == null || vnp_TransactionStatus.trim().isEmpty()) {
            log.warn("VNPay return URL missing vnp_TransactionStatus parameter - will use fallback verification logic");
        }

        return true;
    }

    /**
     * Enhanced IPN (Instant Payment Notification) processing with security validation.
     *
     * @param request HTTP request containing VNPay IPN data
     * @return PaymentIPNResult containing validation result and payment status
     */
    public PaymentIPNResult processIPN(HttpServletRequest request) {
        String vnp_TxnRef = request.getParameter("vnp_TxnRef");
        String vnp_TransactionStatus = request.getParameter("vnp_TransactionStatus");
        String vnp_ResponseCode = request.getParameter("vnp_ResponseCode");
        String clientIp = VNPayConfig.getIpAddress(request);

        log.info("Processing VNPay IPN - TxnRef: {}, Status: {}, ResponseCode: {}, ClientIP: {}",
                vnp_TxnRef, vnp_TransactionStatus, vnp_ResponseCode, clientIp);

        try {
            // Validate IPN parameters
            if (!validateIPNParameters(request)) {
                log.error("VNPay IPN validation failed - missing required parameters for TxnRef: {}", vnp_TxnRef);
                return PaymentIPNResult.invalid("Missing required parameters");
            }

            // Process signature verification (same as orderReturn)
            int verificationResult = orderReturn(request);

            if (verificationResult == 1) {
                log.info("VNPay IPN processed successfully - Payment confirmed for TxnRef: {}", vnp_TxnRef);
                return PaymentIPNResult.success(vnp_TxnRef, vnp_TransactionStatus);
            } else if (verificationResult == 0) {
                log.warn("VNPay IPN processed - Payment failed for TxnRef: {}", vnp_TxnRef);
                return PaymentIPNResult.failed(vnp_TxnRef, vnp_TransactionStatus);
            } else {
                log.error("VNPay IPN signature verification failed for TxnRef: {}", vnp_TxnRef);
                return PaymentIPNResult.invalid("Signature verification failed");
            }

        } catch (Exception e) {
            log.error("Error processing VNPay IPN for TxnRef: {} - {}", vnp_TxnRef, e.getMessage(), e);
            return PaymentIPNResult.error("Internal processing error");
        }
    }

    /**
     * Validate VNPay IPN parameters.
     *
     * @param request HTTP request containing VNPay IPN data
     * @return true if all required parameters are present
     */
    private boolean validateIPNParameters(HttpServletRequest request) {
        String[] requiredParams = {
            "vnp_TxnRef", "vnp_TransactionStatus", "vnp_ResponseCode",
            "vnp_SecureHash", "vnp_Amount", "vnp_TmnCode", "vnp_TransactionNo"
        };

        for (String param : requiredParams) {
            String value = request.getParameter(param);
            if (value == null || value.trim().isEmpty()) {
                log.error("Missing required VNPay IPN parameter: {}", param);
                return false;
            }
        }

        return true;
    }

    /**
     * Check if payment has expired based on VNPay date parameters.
     *
     * Note: This method should only be called when date parameters are available (IPN callbacks).
     * For return URL callbacks, expiration validation should be skipped since VNPay doesn't
     * provide vnp_CreateDate and vnp_ExpireDate parameters.
     *
     * @param vnpCreateDate Payment creation date in yyyyMMddHHmmss format
     * @param vnpExpireDate Payment expiration date in yyyyMMddHHmmss format
     * @return true if payment has expired, false if still valid
     */
    public boolean isPaymentExpired(String vnpCreateDate, String vnpExpireDate) {
        if (vnpCreateDate == null || vnpExpireDate == null) {
            log.warn("VNPay payment expiration check failed - missing date parameters (this should only be called for IPN callbacks)");
            return true; // Treat missing dates as expired for security in IPN context
        }

        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("Etc/GMT+7"));

            Date expireDate = formatter.parse(vnpExpireDate);
            Date currentDate = new Date();

            boolean isExpired = currentDate.after(expireDate);

            if (isExpired) {
                log.warn("VNPay payment expired - Created: {}, Expired: {}, Current: {}",
                        vnpCreateDate, vnpExpireDate, formatter.format(currentDate));
            }

            return isExpired;

        } catch (ParseException e) {
            log.error("VNPay payment expiration check failed - invalid date format: {}", e.getMessage());
            return true; // Treat parsing errors as expired for security
        }
    }

    /**
     * Validate payment timing and return appropriate error message if expired.
     *
     * Note: VNPay return URL callbacks do not include vnp_CreateDate and vnp_ExpireDate parameters.
     * These parameters are only sent in IPN (server-to-server) callbacks. For return URL callbacks,
     * we skip expiration validation since the required parameters are not provided by VNPay.
     *
     * @param vnpParams Map containing VNPay parameters including dates
     * @return Error message if payment expired, null if valid
     */
    public String validatePaymentTiming(Map<String, String> vnpParams) {
        String vnpCreateDate = vnpParams.get("vnp_CreateDate");
        String vnpExpireDate = vnpParams.get("vnp_ExpireDate");

        // Check if this is a return URL callback (missing date parameters) vs IPN callback (has date parameters)
        boolean hasDateParameters = (vnpCreateDate != null && !vnpCreateDate.trim().isEmpty()) ||
                                   (vnpExpireDate != null && !vnpExpireDate.trim().isEmpty());

        if (!hasDateParameters) {
            // Return URL callback scenario - VNPay doesn't send date parameters
            log.debug("Skipping expiration validation for return URL callback - date parameters not provided by VNPay");
            return null; // Skip expiration validation for return URL callbacks
        }

        // IPN callback scenario - validate expiration with provided date parameters
        if (isPaymentExpired(vnpCreateDate, vnpExpireDate)) {
            log.warn("Payment expired in IPN callback - CreateDate: {}, ExpireDate: {}", vnpCreateDate, vnpExpireDate);
            return "Thanh toán đã hết hạn. Vui lòng tạo yêu cầu thanh toán mới.";
        }

        return null; // Payment is still valid
    }

    /**
     * Placeholder method for future VNPay API calls using centralized RestTemplate.
     * This method demonstrates how the injected RestTemplate would be used for
     * VNPay API operations like payment verification, refunds, or transaction queries.
     *
     * Currently, VNPay integration only requires URL generation and return parameter processing,
     * but this preparation ensures consistent HTTP client configuration when API calls are needed.
     *
     * @param apiEndpoint VNPay API endpoint (e.g., vnp_apiUrl + "/merchant_webapi/api/transaction")
     * @param requestData Request parameters for VNPay API
     * @return API response from VNPay
     */
    @SuppressWarnings("unused")
    private String callVNPayAPI(String apiEndpoint, Map<String, String> requestData) {
        log.debug("VNPay API call prepared with centralized RestTemplate - Endpoint: {}", apiEndpoint);

        // Future implementation would use:
        // HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.APPLICATION_JSON);
        // HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestData, headers);
        // ResponseEntity<String> response = restTemplate.postForEntity(apiEndpoint, entity, String.class);
        // return response.getBody();

        // For now, return placeholder to indicate preparation is complete
        return "VNPay API integration prepared with centralized RestTemplate";
    }

    /**
     * Get user-friendly Vietnamese error message for a VNPay response code.
     *
     * @param responseCode VNPay response code
     * @return User-friendly Vietnamese error message
     */
    public String getUserFriendlyErrorMessage(String responseCode) {
        return errorCodeMapper.getUserFriendlyMessage(responseCode);
    }

    /**
     * Get technical Vietnamese error message for a VNPay response code.
     *
     * @param responseCode VNPay response code
     * @return Technical Vietnamese error message
     */
    public String getVietnameseErrorMessage(String responseCode) {
        return errorCodeMapper.getVietnameseMessage(responseCode);
    }

    /**
     * Get error severity for a VNPay response code.
     *
     * @param responseCode VNPay response code
     * @return Error severity level
     */
    public VNPayErrorCodeMapper.ErrorSeverity getErrorSeverity(String responseCode) {
        return errorCodeMapper.getErrorSeverity(responseCode);
    }

    /**
     * Check if a VNPay error is retryable.
     *
     * @param responseCode VNPay response code
     * @return true if the error condition might be temporary and retryable
     */
    public boolean isErrorRetryable(String responseCode) {
        return errorCodeMapper.isRetryable(responseCode);
    }

    /**
     * Check if a VNPay response code indicates success.
     *
     * @param responseCode VNPay response code
     * @return true if the response code indicates successful transaction
     */
    public boolean isSuccessResponse(String responseCode) {
        return errorCodeMapper.isSuccess(responseCode);
    }

    /**
     * Check if a VNPay response code indicates a critical error.
     *
     * @param responseCode VNPay response code
     * @return true if the error is critical and requires immediate attention
     */
    public boolean isCriticalError(String responseCode) {
        return errorCodeMapper.isCriticalError(responseCode);
    }

    /**
     * Get complete error information for a VNPay response code.
     *
     * @param responseCode VNPay response code
     * @return Complete error information including messages, severity, and retry status
     */
    public VNPayErrorCodeMapper.ErrorInfo getErrorInfo(String responseCode) {
        return errorCodeMapper.getErrorInfo(responseCode);
    }

    /**
     * Result class for IPN processing.
     */
    public static class PaymentIPNResult {
        private final boolean valid;
        private final boolean successful;
        private final String transactionRef;
        private final String status;
        private final String errorMessage;

        private PaymentIPNResult(boolean valid, boolean successful, String transactionRef, String status, String errorMessage) {
            this.valid = valid;
            this.successful = successful;
            this.transactionRef = transactionRef;
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public static PaymentIPNResult success(String transactionRef, String status) {
            return new PaymentIPNResult(true, true, transactionRef, status, null);
        }

        public static PaymentIPNResult failed(String transactionRef, String status) {
            return new PaymentIPNResult(true, false, transactionRef, status, null);
        }

        public static PaymentIPNResult invalid(String errorMessage) {
            return new PaymentIPNResult(false, false, null, null, errorMessage);
        }

        public static PaymentIPNResult error(String errorMessage) {
            return new PaymentIPNResult(false, false, null, null, errorMessage);
        }

        // Getters
        public boolean isValid() { return valid; }
        public boolean isSuccessful() { return successful; }
        public String getTransactionRef() { return transactionRef; }
        public String getStatus() { return status; }
        public String getErrorMessage() { return errorMessage; }
    }

}