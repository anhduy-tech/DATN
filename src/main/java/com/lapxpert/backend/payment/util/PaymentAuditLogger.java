package com.lapxpert.backend.payment.util;

import com.lapxpert.backend.common.util.IpAddressUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized payment audit logging utility.
 * Provides consistent audit logging for all payment operations across controllers and services.
 * Ensures single source of truth for payment audit data structure and Vietnamese business terminology.
 */
@Slf4j
@Component
public class PaymentAuditLogger {

    /**
     * Create audit log entry for payment operations
     * @param gateway Payment gateway name (e.g., "VNPay", "MoMo")
     * @param operation Operation name (e.g., "CREATE_PAYMENT", "PROCESS_CALLBACK")
     * @param orderId Order ID
     * @param amount Payment amount
     * @param clientIp Client IP address
     * @param additionalInfo Additional information for audit
     */
    public void createAuditLog(String gateway, String operation, String orderId, Long amount, 
                              String clientIp, Map<String, Object> additionalInfo) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("gateway", gateway);
            auditData.put("operation", operation);
            auditData.put("orderId", orderId);
            auditData.put("amount", amount);
            auditData.put("clientIp", IpAddressUtils.sanitizeIpForLogging(clientIp));
            auditData.put("timestamp", LocalDateTime.now());
            
            if (additionalInfo != null) {
                auditData.putAll(additionalInfo);
            }
            
            log.info("Payment audit log - Gateway: {}, Operation: {}, OrderId: {}, Amount: {}, IP: {}", 
                    gateway, operation, orderId, amount, 
                    IpAddressUtils.sanitizeIpForLogging(clientIp));
            
            // TODO: Implement proper audit logging to database or audit service
            // This could be enhanced to store in audit_log table or send to audit service
            
        } catch (Exception e) {
            log.error("Failed to create audit log for gateway: {}, operation: {}, orderId: {}", 
                     gateway, operation, orderId, e);
        }
    }

    /**
     * Create audit log entry for MoMo payment operations with MoMo-specific fields
     * @param operation Operation name
     * @param orderId Order ID
     * @param amount Payment amount
     * @param clientIp Client IP address
     * @param additionalInfo Additional information for audit
     * @param sdkEnabled Whether MoMo SDK is enabled
     * @param environment MoMo environment (sandbox/production)
     */
    public void createMoMoAuditLog(String operation, String orderId, Long amount,
                                  String clientIp, Map<String, Object> additionalInfo,
                                  boolean sdkEnabled, String environment) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("gateway", "MoMo");
            auditData.put("operation", operation);
            auditData.put("orderId", orderId);
            auditData.put("amount", amount);
            auditData.put("clientIp", IpAddressUtils.sanitizeIpForLogging(clientIp));
            auditData.put("timestamp", LocalDateTime.now());
            auditData.put("sdkEnabled", sdkEnabled);
            auditData.put("environment", environment);

            if (additionalInfo != null) {
                auditData.putAll(additionalInfo);
            }

            log.info("MoMo audit log - Operation: {}, OrderId: {}, Amount: {}, IP: {}",
                    operation, orderId, amount, IpAddressUtils.sanitizeIpForLogging(clientIp));

            // TODO: Implement proper audit logging to database or audit service
            // This could be enhanced to store in audit_log table or send to audit service

        } catch (Exception e) {
            log.error("Failed to create MoMo audit log for operation: {}, orderId: {}",
                     operation, orderId, e);
        }
    }
}
