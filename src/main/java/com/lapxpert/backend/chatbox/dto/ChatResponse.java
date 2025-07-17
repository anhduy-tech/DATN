package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Enhanced DTO cho GitHub AI chat responses với metadata và type classification
 * Tương ứng với EnhancedChatResponse model trong Python FastAPI service
 * Uses GitHub AI (Mistral Medium 3) for Vietnamese language processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    
    // Core response data (backward compatible)
    @JsonProperty("ai_response")
    private String aiResponse;
    
    @JsonProperty("query_processed")
    private String queryProcessed;

    // Enhanced fields
    @JsonProperty("response_type")
    private ResponseType responseType;
    
    @JsonProperty("processing_metadata")
    private ProcessingMetadata processingMetadata;
    
    // Optional product recommendations (for product_recommendation and hybrid types)
    @JsonProperty("product_recommendations")
    private List<ProductRecommendation> productRecommendations;
    
    // Optional intent classification data
    @JsonProperty("intent_data")
    private Map<String, Object> intentData;
    
    // Confidence and quality metrics
    @JsonProperty("confidence_score")
    private Double confidenceScore;
    
    @JsonProperty("quality_score")
    private Double qualityScore;
    
    // Response context
    @JsonProperty("context_used")
    private List<String> contextUsed;
    
    @JsonProperty("fallback_used")
    @Builder.Default
    private Boolean fallbackUsed = false;
    
    /**
     * Constructor cho basic enhanced response
     */
    public ChatResponse(String aiResponse, String queryProcessed,
                              ResponseType responseType) {
        this.aiResponse = aiResponse;
        this.queryProcessed = queryProcessed;
        this.responseType = responseType;
        this.fallbackUsed = false;
    }
    

    
    /**
     * Get number of product recommendations
     */
    public int getRecommendationCount() {
        return productRecommendations != null ? productRecommendations.size() : 0;
    }
    
    /**
     * Check if has product recommendations
     */
    public boolean hasRecommendations() {
        return productRecommendations != null && !productRecommendations.isEmpty();
    }
    
    /**
     * Check if response is conversational type
     */
    public boolean isConversational() {
        return responseType != null && responseType.isConversational();
    }
    
    /**
     * Check if response is product recommendation type
     */
    public boolean isProductRecommendation() {
        return responseType != null && responseType.isProductRecommendation();
    }
    
    /**
     * Check if response is hybrid type
     */
    public boolean isHybrid() {
        return responseType != null && responseType.isHybrid();
    }
    
    /**
     * Check if response has high confidence (>= 0.8)
     */
    public boolean isHighConfidence() {
        return confidenceScore != null && confidenceScore >= 0.8;
    }
}
