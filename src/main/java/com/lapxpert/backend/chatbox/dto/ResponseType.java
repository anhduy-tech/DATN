package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration for different AI response types
 * Tương ứng với ResponseType enum trong Python models
 */
public enum ResponseType {
    
    CONVERSATIONAL("conversational"),
    PRODUCT_RECOMMENDATION("product_recommendation"),
    HYBRID("hybrid"),
    INTENT_CLASSIFICATION("intent_classification");
    
    private final String value;
    
    ResponseType(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    /**
     * Get ResponseType from string value
     */
    public static ResponseType fromValue(String value) {
        for (ResponseType type : ResponseType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ResponseType: " + value);
    }
    
    /**
     * Check if this is a conversational response
     */
    public boolean isConversational() {
        return this == CONVERSATIONAL;
    }
    
    /**
     * Check if this is a product recommendation response
     */
    public boolean isProductRecommendation() {
        return this == PRODUCT_RECOMMENDATION;
    }
    
    /**
     * Check if this is a hybrid response
     */
    public boolean isHybrid() {
        return this == HYBRID;
    }
    
    /**
     * Check if this is an intent classification response
     */
    public boolean isIntentClassification() {
        return this == INTENT_CLASSIFICATION;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
