package com.lapxpert.backend.shipping.service;

import com.lapxpert.backend.shipping.config.GHNConfig;
import com.lapxpert.backend.shipping.dto.GHNShippingRequest;
import com.lapxpert.backend.shipping.dto.GHNShippingResponse;
import com.lapxpert.backend.shipping.dto.ShippingRequest;
import com.lapxpert.backend.shipping.dto.ShippingFeeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GHN (GiaoHangNhanh) shipping service implementation
 * Provides shipping fee calculation using GHN API v2
 */
@Slf4j
@Service
public class GHNService extends ShippingCalculatorService {

    private final RestTemplate restTemplate;

    @Autowired
    private GHNAddressService ghnAddressService;

    public GHNService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public ShippingFeeResponse calculateShippingFee(ShippingRequest request) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] Calculating shipping fee using GHN for request: {}", requestId, request);

        // Enhanced configuration validation with detailed error reporting
        if (!GHNConfig.validateConfiguration()) {
            log.error("[{}] GHN configuration validation failed", requestId);
            return createEnhancedErrorResponse("CONFIG_INVALID",
                "GHN service configuration is invalid or incomplete",
                "Dịch vụ vận chuyển tạm thời không khả dụng", requestId);
        }

        // Enhanced request validation with specific error details
        if (!validateRequest(request)) {
            String error = getValidationError(request);
            log.error("[{}] Request validation failed: {}", requestId, error);
            return createEnhancedErrorResponse("INVALID_REQUEST", error,
                "Thông tin yêu cầu không hợp lệ: " + error, requestId);
        }

        try {
            // Fill in default values if not provided
            fillDefaultValues(request);

            // Build GHN API request with enhanced logging
            GHNShippingRequest ghnRequest = buildGHNRequest(request);
            log.info("[{}] Built GHN request: fromDistrictId={}, fromWardCode={}, toDistrictId={}, toWardCode={}, serviceId={}, serviceTypeId={}, weight={}",
                requestId, ghnRequest.getFromDistrictId(), ghnRequest.getFromWardCode(),
                ghnRequest.getToDistrictId(), ghnRequest.getToWardCode(),
                ghnRequest.getServiceId(), ghnRequest.getServiceTypeId(), ghnRequest.getWeight());

            // Create headers
            HttpHeaders headers = createHeaders();

            // Make API call with enhanced error handling
            HttpEntity<GHNShippingRequest> entity = new HttpEntity<>(ghnRequest, headers);
            ResponseEntity<GHNShippingResponse> response = restTemplate.exchange(
                GHNConfig.getShippingFeeUrl(), HttpMethod.POST, entity, GHNShippingResponse.class
            );

            // Parse response with enhanced error details
            return parseResponseWithEnhancedErrorHandling(response.getBody(), requestId);

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("[{}] GHN API client error ({}): {}", requestId, e.getStatusCode(), e.getResponseBodyAsString());
            return createEnhancedErrorResponse("API_CLIENT_ERROR",
                "GHN API client error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                "Lỗi kết nối với dịch vụ vận chuyển", requestId);
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.error("[{}] GHN API server error ({}): {}", requestId, e.getStatusCode(), e.getResponseBodyAsString());
            return createEnhancedErrorResponse("API_SERVER_ERROR",
                "GHN API server error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                "Dịch vụ vận chuyển đang gặp sự cố", requestId);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("[{}] GHN API connection timeout: {}", requestId, e.getMessage());
            return createEnhancedErrorResponse("CONNECTION_TIMEOUT",
                "Connection timeout to GHN API: " + e.getMessage(),
                "Kết nối với dịch vụ vận chuyển bị timeout", requestId);
        } catch (Exception e) {
            log.error("[{}] Unexpected error in GHN shipping calculation: {}", requestId, e.getMessage(), e);
            return createEnhancedErrorResponse("UNEXPECTED_ERROR",
                "Unexpected error: " + e.getMessage(),
                "Đã xảy ra lỗi không mong muốn", requestId);
        }
    }
    
    @Override
    public String getProviderName() {
        return "GHN";
    }
    
    @Override
    public boolean isAvailable() {
        return GHNConfig.validateConfiguration();
    }
    
    /**
     * Fill default values for GHN request
     */
    private void fillDefaultValues(ShippingRequest request) {
        if (request.getPickProvince() == null || request.getPickProvince().trim().isEmpty()) {
            // GHN uses district and ward codes instead of province names
            // We'll use the configured default values
        }
        
        if (request.getPickDistrict() == null || request.getPickDistrict().trim().isEmpty()) {
            // Will be handled in buildGHNRequest using default district ID
        }
        
        if (request.getTransport() == null || request.getTransport().trim().isEmpty()) {
            request.setTransport("road"); // Default transport method
        }
        
        if (request.getDeliverOption() == null || request.getDeliverOption().trim().isEmpty()) {
            request.setDeliverOption("standard"); // Default delivery option
        }
    }
    
    /**
     * Build GHN API request from generic shipping request
     */
    private GHNShippingRequest buildGHNRequest(ShippingRequest request) {
        // Resolve destination address using GHN Address Service
        Integer toProvinceId = ghnAddressService.findProvinceId(request.getProvince());
        Integer toDistrictId = null;
        String toWardCode = null;

        if (toProvinceId != null) {
            toDistrictId = ghnAddressService.findDistrictId(request.getDistrict(), toProvinceId);

            if (toDistrictId != null && request.getWard() != null && !request.getWard().trim().isEmpty()) {
                toWardCode = ghnAddressService.findWardCode(request.getWard(), toDistrictId);
            }
        }

        // Use fallback values if address resolution fails
        if (toDistrictId == null) {
            log.warn("Could not resolve destination district '{}' in province '{}', using fallback district ID",
                request.getDistrict(), request.getProvince());
            toDistrictId = 1; // Fallback district ID
        }

        if (toWardCode == null) {
            log.warn("Could not resolve destination ward '{}' in district '{}', using fallback ward code",
                request.getWard(), request.getDistrict());
            toWardCode = "1A0101"; // Fallback ward code
        }

        log.info("GHN address resolution: Province '{}' -> {}, District '{}' -> {}, Ward '{}' -> {}",
            request.getProvince(), toProvinceId, request.getDistrict(), toDistrictId,
            request.getWard(), toWardCode);

        // Get available services for this route to ensure we use a valid service
        Integer serviceId = getAvailableServiceForRoute(Integer.parseInt(GHNConfig.ghn_DefaultFromDistrictId), toDistrictId);
        if (serviceId == null) {
            log.warn("No available service found for route from district {} to district {}, using default service {}",
                GHNConfig.ghn_DefaultFromDistrictId, toDistrictId, GHNConfig.ghn_DefaultServiceId);
            serviceId = Integer.parseInt(GHNConfig.ghn_DefaultServiceId);
        }

        return GHNShippingRequest.builder()
            .serviceId(serviceId)
            .serviceTypeId(Integer.parseInt(GHNConfig.ghn_DefaultServiceTypeId))
            .fromDistrictId(Integer.parseInt(GHNConfig.ghn_DefaultFromDistrictId))
            .fromWardCode(GHNConfig.ghn_DefaultFromWardCode)
            .toDistrictId(toDistrictId)
            .toWardCode(toWardCode)
            .weight(request.getWeight())
            .length(20) // Default package dimensions
            .width(15)
            .height(10)
            .insuranceValue(request.getValue())
            .items(Collections.singletonList(
                GHNShippingRequest.GHNItem.builder()
                    .name("Sản phẩm")
                    .quantity(1)
                    .weight(request.getWeight())
                    .length(20)
                    .width(15)
                    .height(10)
                    .build()
            ))
            .build();
    }
    
    /**
     * Get available service for a specific route
     * This helps avoid "route not found service" errors by using valid services
     */
    private Integer getAvailableServiceForRoute(Integer fromDistrictId, Integer toDistrictId) {
        try {
            HttpHeaders headers = createHeaders();

            // Build request body for available services API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("shop_id", Integer.parseInt(GHNConfig.ghn_ShopId));
            requestBody.put("from_district", fromDistrictId);
            requestBody.put("to_district", toDistrictId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("Querying available services for route: {} -> {}", fromDistrictId, toDistrictId);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                GHNConfig.getAvailableServicesUrl(),
                HttpMethod.POST,
                entity,
                (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (response.getBody() != null && response.getBody().get("data") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> services = (List<Map<String, Object>>) response.getBody().get("data");
                if (!services.isEmpty()) {
                    // Return the first available service ID
                    Integer serviceId = (Integer) services.get(0).get("service_id");
                    log.info("Found available service {} for route {} -> {}", serviceId, fromDistrictId, toDistrictId);
                    return serviceId;
                }
            }

            log.warn("No services available for route {} -> {}", fromDistrictId, toDistrictId);
            return null;

        } catch (Exception e) {
            log.error("Error querying available services for route {} -> {}: {}", fromDistrictId, toDistrictId, e.getMessage());
            return null;
        }
    }

    /**
     * Create HTTP headers for GHN API
     * Updated to match GHN API documentation format
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", GHNConfig.ghn_ApiToken);  // lowercase as per GHN docs
        headers.set("ShopId", GHNConfig.ghn_ShopId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
    
    // Removed unused parseResponse method - using parseResponseWithEnhancedErrorHandling instead

    /**
     * Create fallback response when GHN service is unavailable
     */
    @Override
    protected ShippingFeeResponse createFallbackResponse(String reason) {
        log.warn("Creating fallback response for GHN: {}", reason);
        return ShippingFeeResponse.error("SERVICE_UNAVAILABLE", reason, getProviderName());
    }

    /**
     * Validate shipping request for GHN requirements
     */
    @Override
    protected boolean validateRequest(ShippingRequest request) {
        return request != null &&
               request.getWeight() != null && request.getWeight() > 0 &&
               request.getProvince() != null && !request.getProvince().trim().isEmpty() &&
               request.getDistrict() != null && !request.getDistrict().trim().isEmpty();
    }

    /**
     * Get validation error message
     */
    @Override
    protected String getValidationError(ShippingRequest request) {
        if (request == null) {
            return "Shipping request is null";
        }
        if (request.getWeight() == null || request.getWeight() <= 0) {
            return "Weight must be greater than 0";
        }
        if (request.getProvince() == null || request.getProvince().trim().isEmpty()) {
            return "Delivery province is required";
        }
        if (request.getDistrict() == null || request.getDistrict().trim().isEmpty()) {
            return "Delivery district is required";
        }
        return "Invalid request";
    }

    /**
     * Create enhanced error response with detailed information
     *
     * @param errorCode Technical error code for categorization
     * @param technicalMessage Technical error details for debugging
     * @param userMessage User-friendly Vietnamese message
     * @param requestId Request ID for tracing
     * @return Enhanced ShippingFeeResponse with detailed error information
     */
    private ShippingFeeResponse createEnhancedErrorResponse(String errorCode, String technicalMessage,
                                                          String userMessage, String requestId) {
        ShippingFeeResponse response = ShippingFeeResponse.error(errorCode, userMessage, getProviderName());

        // Add technical details for debugging
        response.setLogId(requestId);

        // Log detailed error for monitoring
        log.error("[{}] GHN Error - Code: {}, Technical: {}, User: {}",
            requestId, errorCode, technicalMessage, userMessage);

        return response;
    }

    /**
     * Parse GHN response with enhanced error handling and detailed logging
     *
     * @param response GHN API response
     * @param requestId Request ID for tracing
     * @return Parsed ShippingFeeResponse with enhanced error details
     */
    private ShippingFeeResponse parseResponseWithEnhancedErrorHandling(GHNShippingResponse response, String requestId) {
        try {
            if (response == null) {
                log.error("[{}] GHN response is null", requestId);
                return createEnhancedErrorResponse("NULL_RESPONSE",
                    "GHN API returned null response",
                    "Không nhận được phản hồi từ dịch vụ vận chuyển", requestId);
            }

            if (!response.isSuccess()) {
                String errorMsg = response.getMessage() != null ? response.getMessage() : "Unknown GHN error";
                log.error("[{}] GHN API returned error: Code={}, Message={}",
                    requestId, response.getCode(), errorMsg);

                // Map GHN error codes to user-friendly messages
                String userMessage = mapGHNErrorToUserMessage(response.getCode(), errorMsg);
                return createEnhancedErrorResponse("GHN_API_ERROR",
                    "GHN API error: " + errorMsg, userMessage, requestId);
            }

            BigDecimal shippingFee = response.getShippingFee();
            BigDecimal insuranceFee = response.getInsuranceFee();

            if (shippingFee == null || shippingFee.compareTo(BigDecimal.ZERO) < 0) {
                log.error("[{}] Invalid shipping fee from GHN: {}", requestId, shippingFee);
                return createEnhancedErrorResponse("INVALID_FEE",
                    "Invalid shipping fee: " + shippingFee,
                    "Phí vận chuyển không hợp lệ", requestId);
            }

            ShippingFeeResponse result = ShippingFeeResponse.success(shippingFee, insuranceFee, "GHN Standard", getProviderName());
            result.setMessage(response.getMessage());
            result.setLogId(requestId);

            log.info("[{}] Successfully calculated GHN shipping fee: {} VND (Insurance: {} VND)",
                requestId, shippingFee, insuranceFee);
            return result;

        } catch (Exception e) {
            log.error("[{}] Failed to parse GHN response: {}", requestId, e.getMessage(), e);
            return createEnhancedErrorResponse("PARSE_ERROR",
                "Failed to parse GHN response: " + e.getMessage(),
                "Lỗi xử lý phản hồi từ dịch vụ vận chuyển", requestId);
        }
    }

    /**
     * Map GHN error codes to user-friendly Vietnamese messages
     *
     * @param errorCode GHN error code
     * @param originalMessage Original GHN error message
     * @return User-friendly Vietnamese error message
     */
    private String mapGHNErrorToUserMessage(Integer errorCode, String originalMessage) {
        if (errorCode == null) {
            return "Lỗi không xác định từ dịch vụ vận chuyển";
        }

        switch (errorCode) {
            case 400:
                return "Thông tin yêu cầu không hợp lệ";
            case 401:
                return "Dịch vụ vận chuyển không được ủy quyền";
            case 403:
                return "Không có quyền truy cập dịch vụ vận chuyển";
            case 404:
                return "Không tìm thấy thông tin địa chỉ";
            case 429:
                return "Quá nhiều yêu cầu, vui lòng thử lại sau";
            case 500:
                return "Dịch vụ vận chuyển đang gặp sự cố";
            default:
                if (originalMessage != null && originalMessage.toLowerCase().contains("district")) {
                    return "Không thể xác định quận/huyện giao hàng";
                } else if (originalMessage != null && originalMessage.toLowerCase().contains("ward")) {
                    return "Không thể xác định phường/xã giao hàng";
                } else if (originalMessage != null && originalMessage.toLowerCase().contains("weight")) {
                    return "Trọng lượng không hợp lệ";
                } else {
                    return "Không thể tính phí vận chuyển: " + (originalMessage != null ? originalMessage : "Lỗi không xác định");
                }
        }
    }
}
