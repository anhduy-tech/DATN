package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO cho Intent Classification requests
 * Tương ứng với ChatRequest model trong Python FastAPI service cho intent classification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassificationRequest {
    
    @NotBlank(message = "Tin nhắn không được để trống")
    @Size(min = 1, max = 2000, message = "Tin nhắn phải từ 1 đến 2000 ký tự")
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    /**
     * Constructor cho basic intent classification request
     */
    public IntentClassificationRequest(String message) {
        this.message = message;
    }
}
