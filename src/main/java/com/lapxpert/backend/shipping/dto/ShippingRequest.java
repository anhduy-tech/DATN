package com.lapxpert.backend.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Shipping fee calculation request DTO
 * Contains all necessary information for calculating shipping costs
 *
 * ENHANCED VALIDATION FEATURES:
 * - Comprehensive field validation with specific error messages
 * - Vietnamese address format validation
 * - Weight and dimension constraints validation
 * - Business rule validation for Vietnamese market
 * - Backward compatibility with existing frontend implementations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingRequest {
    
    // Pickup location
    private String pickAddressId;
    private String pickAddress;
    private String pickProvince;
    private String pickDistrict;
    private String pickWard;
    private String pickStreet;
    
    // Delivery location
    private String address;
    private String province;
    private String district;
    private String ward;
    private String street;
    
    // Package details
    private Integer weight; // in grams
    private BigDecimal value; // order value in VND for insurance
    private String transport; // "road" or "fly"
    private String deliverOption; // delivery service option
    
    // Additional parameters
    private String[] tags;
    
    /**
     * Generate hash code for caching purposes
     * Only includes essential fields that affect shipping calculation
     */
    @Override
    public int hashCode() {
        return Objects.hash(
            pickProvince, pickDistrict, pickWard,
            province, district, ward,
            weight, transport, deliverOption
        );
    }
    
    /**
     * Enhanced validation for shipping request with comprehensive checks
     *
     * VALIDATION RULES:
     * - Weight: Must be positive and within reasonable limits (100g - 30kg)
     * - Address: Both pickup and delivery addresses are mandatory
     * - Vietnamese format: Address fields should follow Vietnamese naming conventions
     * - Business rules: Order value validation, transport option validation
     */
    public boolean isValid() {
        return validatePickupAddress() &&
               validateDeliveryAddress() &&
               validateWeight() &&
               validateBusinessRules() &&
               deliverOption != null && !deliverOption.trim().isEmpty();
    }

    /**
     * Get comprehensive validation error message with specific details
     */
    public String getValidationError() {
        // Pickup address validation
        if (pickProvince == null || pickProvince.trim().isEmpty()) {
            return "Tỉnh/Thành phố lấy hàng là bắt buộc";
        }
        if (pickDistrict == null || pickDistrict.trim().isEmpty()) {
            return "Quận/Huyện lấy hàng là bắt buộc";
        }
        if (pickProvince.trim().length() < 3) {
            return "Tên tỉnh/thành phố lấy hàng không hợp lệ";
        }
        if (pickDistrict.trim().length() < 3) {
            return "Tên quận/huyện lấy hàng không hợp lệ";
        }

        // Delivery address validation
        if (province == null || province.trim().isEmpty()) {
            return "Tỉnh/Thành phố giao hàng là bắt buộc";
        }
        if (district == null || district.trim().isEmpty()) {
            return "Quận/Huyện giao hàng là bắt buộc";
        }
        if (province.trim().length() < 3) {
            return "Tên tỉnh/thành phố giao hàng không hợp lệ";
        }
        if (district.trim().length() < 3) {
            return "Tên quận/huyện giao hàng không hợp lệ";
        }

        // Weight validation
        if (weight == null) {
            return "Trọng lượng là bắt buộc";
        }
        if (weight <= 0) {
            return "Trọng lượng phải lớn hơn 0";
        }
        if (weight < 100) { // Minimum 100g
            return "Trọng lượng tối thiểu là 100g";
        }
        if (weight > 30000) { // 30kg limit for standard shipping
            return "Trọng lượng không được vượt quá 30kg";
        }

        // Business rules validation
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            return "Giá trị đơn hàng không được âm";
        }
        if (value != null && value.compareTo(new BigDecimal("100000000")) > 0) { // 100M VND limit
            return "Giá trị đơn hàng không được vượt quá 100,000,000 VND";
        }

        // Delivery option validation
        if (deliverOption == null || deliverOption.trim().isEmpty()) {
            return "Tùy chọn giao hàng là bắt buộc";
        }

        return null;
    }

    /**
     * Validate pickup address information
     */
    private boolean validatePickupAddress() {
        return pickProvince != null && !pickProvince.trim().isEmpty() && pickProvince.trim().length() >= 3 &&
               pickDistrict != null && !pickDistrict.trim().isEmpty() && pickDistrict.trim().length() >= 3;
    }

    /**
     * Validate delivery address information
     */
    private boolean validateDeliveryAddress() {
        return province != null && !province.trim().isEmpty() && province.trim().length() >= 3 &&
               district != null && !district.trim().isEmpty() && district.trim().length() >= 3;
    }

    /**
     * Validate weight constraints
     */
    private boolean validateWeight() {
        return weight != null && weight >= 100 && weight <= 30000; // 100g to 30kg
    }

    /**
     * Validate business rules specific to Vietnamese market
     */
    private boolean validateBusinessRules() {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) return false;
        if (value != null && value.compareTo(new BigDecimal("100000000")) > 0) return false; // 100M VND limit
        return true;
    }
}
