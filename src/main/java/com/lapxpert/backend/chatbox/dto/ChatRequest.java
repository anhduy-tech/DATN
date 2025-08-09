package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * DTO cho AI chat requests
 * Tương ứng với ChatRequest model trong Python FastAPI service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    @NotBlank(message = "Tin nhắn không được để trống")
    @Size(min = 1, max = 2000, message = "Tin nhắn phải từ 1 đến 2000 ký tự")
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    @Min(value = 1, message = "Số lượng gợi ý tối thiểu là 1")
    @Max(value = 10, message = "Số lượng gợi ý tối đa là 10")
    @JsonProperty("top_k")
    private Integer topK = 5;
    
    @JsonProperty("include_details")
    private Boolean includeDetails = true;
    
    /**
     * Constructor cho basic chat request
     */
    public ChatRequest(String message) {
        this.message = message;
        this.topK = 5;
        this.includeDetails = true;
    }
    
    /**
     * Constructor với user context
     */
    public ChatRequest(String message, String userId, String sessionId) {
        this.message = message;
        this.userId = userId;
        this.sessionId = sessionId;
        this.topK = 5;
        this.includeDetails = true;
    }
}
