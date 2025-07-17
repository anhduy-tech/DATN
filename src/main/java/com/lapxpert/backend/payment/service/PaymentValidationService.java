package com.lapxpert.backend.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Payment validation service for business logic layer
 * Provides shared validation logic for payment operations in services
 * Consolidates validation logic previously in PaymentValidationUtils
 */
@Slf4j
@Service
public class PaymentValidationService {

    /**
     * Validate payment amount
     * @param amount Payment amount
     * @throws IllegalArgumentException if amount is invalid
     */
    public void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }
    }

    /**
     * Validate order information
     * @param orderInfo Order information
     * @throws IllegalArgumentException if order info is invalid
     */
    public void validateOrderInfo(String orderInfo) {
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
    public void validateOrderId(String orderId) {
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
    public void validateUrl(String url, String paramName) {
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
    public boolean isValidIPAddress(String ipAddress) {
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
}
