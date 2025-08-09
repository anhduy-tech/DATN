package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO cho Intent Classification responses
 * Tương ứng với IntentClassificationResponse model trong Python FastAPI service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassificationResponse {
    
    @JsonProperty("intent")
    private String intent;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("reasoning")
    private String reasoning;
    
    @JsonProperty("method")
    private String method;
    
    @JsonProperty("processed_text")
    private String processedText;
    
    /**
     * Constructor cho basic response
     */
    public IntentClassificationResponse(String intent, Double confidence) {
        this.intent = intent;
        this.confidence = confidence;
    }
    
    /**
     * Constructor với reasoning
     */
    public IntentClassificationResponse(String intent, Double confidence, String reasoning) {
        this.intent = intent;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }
    
    /**
     * Check if intent is PRODUCT_SEARCH
     */
    public boolean isProductSearch() {
        return "PRODUCT_SEARCH".equals(intent);
    }
    
    /**
     * Check if intent is GENERAL_CHAT
     */
    public boolean isGeneralChat() {
        return "GENERAL_CHAT".equals(intent);
    }
    
    /**
     * Check if intent is HYBRID
     */
    public boolean isHybrid() {
        return "HYBRID".equals(intent);
    }
    
    /**
     * Check if confidence is high (>= 0.7)
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.7;
    }
    
    /**
     * Check if confidence is medium (0.4 - 0.7)
     */
    public boolean isMediumConfidence() {
        return confidence != null && confidence >= 0.4 && confidence < 0.7;
    }
    
    /**
     * Check if confidence is low (< 0.4)
     */
    public boolean isLowConfidence() {
        return confidence != null && confidence < 0.4;
    }
}
