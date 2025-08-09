package com.lapxpert.backend.sanpham.dto;

import com.lapxpert.backend.sanpham.event.PriceChangeEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WebSocket message for real-time price update notifications.
 * Sent to frontend clients when product prices change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceUpdateMessage {
    
    /**
     * Product variant ID
     */
    private Long variantId;
    
    /**
     * Product variant SKU
     */
    private String sku;
    
    /**
     * Product name for display
     */
    private String productName;
    
    /**
     * Old original price (giaBan) - for price change notifications
     */
    private BigDecimal oldPrice;

    /**
     * New original price (giaBan) - for price change notifications
     */
    private BigDecimal newPrice;
    
    /**
     * Vietnamese warning message
     */
    private String message;
    
    /**
     * Timestamp of the change
     */
    private Instant timestamp;
    
    /**
     * Type of price change (INCREASE, DECREASE, UPDATE)
     */
    private String changeType;
    
    /**
     * User who made the change
     */
    private String nguoiThucHien;
    
    /**
     * Create price update message from price change event
     * Uses original prices (giaBan) for notifications, not effective prices
     */
    public static PriceUpdateMessage fromEvent(PriceChangeEvent event) {
        // Use original prices (giaBan) for price change notifications
        BigDecimal oldOriginalPrice = event.getOldPrice();
        BigDecimal newOriginalPrice = event.getNewPrice();

        String changeType = determineChangeType(oldOriginalPrice, newOriginalPrice);
        String message = createVietnameseMessage(event.getProductName(), oldOriginalPrice, newOriginalPrice, changeType);

        return PriceUpdateMessage.builder()
                .variantId(event.getVariantId())
                .sku(event.getSku())
                .productName(event.getProductName())
                .oldPrice(oldOriginalPrice)
                .newPrice(newOriginalPrice)
                .message(message)
                .timestamp(event.getTimestamp())
                .changeType(changeType)
                .nguoiThucHien(event.getNguoiThucHien())
                .build();
    }
    
    /**
     * Determine the type of price change
     */
    private static String determineChangeType(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null) {
            return "UPDATE";
        }
        
        int comparison = newPrice.compareTo(oldPrice);
        if (comparison > 0) {
            return "INCREASE";
        } else if (comparison < 0) {
            return "DECREASE";
        } else {
            return "UPDATE";
        }
    }
    
    /**
     * Create Vietnamese warning message
     */
    private static String createVietnameseMessage(String productName, BigDecimal oldPrice, BigDecimal newPrice, String changeType) {
        if (oldPrice == null || newPrice == null) {
            return String.format("Giá sản phẩm %s đã được cập nhật", productName);
        }
        
        switch (changeType) {
            case "INCREASE":
                return String.format("⚠️ Giá sản phẩm %s đã tăng từ %,.0f₫ lên %,.0f₫", 
                    productName, oldPrice, newPrice);
            case "DECREASE":
                return String.format("🎉 Giá sản phẩm %s đã giảm từ %,.0f₫ xuống %,.0f₫", 
                    productName, oldPrice, newPrice);
            default:
                return String.format("Giá sản phẩm %s đã thay đổi từ %,.0f₫ thành %,.0f₫", 
                    productName, oldPrice, newPrice);
        }
    }
}
