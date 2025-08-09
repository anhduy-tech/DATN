package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO cho processing metadata
 * Tương ứng với ProcessingMetadata model trong Python FastAPI service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingMetadata {
    
    // Timing information
    @JsonProperty("processing_time_ms")
    private Double processingTimeMs;
    
    @JsonProperty("embedding_time_ms")
    private Double embeddingTimeMs;
    
    @JsonProperty("search_time_ms")
    private Double searchTimeMs;
    
    @JsonProperty("ai_generation_time_ms")
    private Double aiGenerationTimeMs;
    
    // Performance metrics
    @JsonProperty("tokens_processed")
    private Integer tokensProcessed;
    
    @JsonProperty("similarity_threshold")
    private Double similarityThreshold;
    
    @JsonProperty("results_found")
    private Integer resultsFound;
    
    // System information
    @JsonProperty("model_version")
    private String modelVersion;
    
    @JsonProperty("embedding_model")
    private String embeddingModel;
    
    @JsonProperty("ollama_model")
    private String ollamaModel;
    
    // Request context
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    // Additional metadata
    @JsonProperty("extra_data")
    private Map<String, Object> extraData;
    
    /**
     * Constructor cho basic timing metadata
     */
    public ProcessingMetadata(Double processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Get total processing time in seconds
     */
    public Double getProcessingTimeSeconds() {
        return processingTimeMs != null ? processingTimeMs / 1000.0 : null;
    }
    
    /**
     * Check if processing was fast (< 1 second)
     */
    public boolean isFastProcessing() {
        return processingTimeMs != null && processingTimeMs < 1000.0;
    }
    
    /**
     * Check if processing was slow (> 5 seconds)
     */
    public boolean isSlowProcessing() {
        return processingTimeMs != null && processingTimeMs > 5000.0;
    }
}
