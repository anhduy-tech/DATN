package com.lapxpert.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lapxpert.backend.chatbox.constants.ChatSenderConstants;
import com.lapxpert.backend.chatbox.dto.ProductRecommendation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO cho WebSocket chat messages
 * Sử dụng cho real-time AI chat functionality
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    /**
     * Unique message identifier
     */
    @JsonProperty("message_id")
    private String messageId;
    
    /**
     * Chat session identifier
     */
    @JsonProperty("session_id")
    private String sessionId;
    
    /**
     * Message sender (user ID hoặc "AI Assistant")
     */
    @JsonProperty("sender")
    private String sender;
    
    /**
     * Message content
     */
    @JsonProperty("content")
    private String content;
    
    /**
     * Message timestamp
     */
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    /**
     * Message type (USER_MESSAGE, AI_RESPONSE, JOIN, LEAVE, ERROR)
     */
    @JsonProperty("message_type")
    private String messageType = "USER_MESSAGE";
    
    /**
     * Target user cho private messages (optional)
     */
    @JsonProperty("target_user")
    private String targetUser;
    
    /**
     * Whether message is private
     */
    @JsonProperty("is_private")
    private Boolean isPrivate = false;
    
    /**
     * Product recommendations từ AI (chỉ cho AI responses)
     */
    @JsonProperty("product_recommendations")
    private List<ProductRecommendation> productRecommendations;
    
    /**
     * Additional metadata
     */
    @JsonProperty("metadata")
    private Object metadata;
    
    /**
     * Constructor cho user messages
     */
    public ChatMessage(String sessionId, String sender, String content) {
        this.sessionId = sessionId;
        this.sender = sender;
        this.content = content;
        this.timestamp = Instant.now();
        this.messageType = "USER_MESSAGE";
    }
    
    /**
     * Constructor cho AI responses với recommendations
     */
    public ChatMessage(String sessionId, String sender, String content, List<ProductRecommendation> recommendations) {
        this.sessionId = sessionId;
        this.sender = sender;
        this.content = content;
        this.productRecommendations = recommendations;
        this.timestamp = Instant.now();
        this.messageType = "AI_RESPONSE";
    }
    
    /**
     * Create join message
     */
    public static ChatMessage createJoinMessage(String sessionId, String userId) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSender(userId);
        message.setContent(userId + " đã tham gia cuộc trò chuyện");
        message.setTimestamp(Instant.now());
        message.setMessageType("JOIN");
        return message;
    }
    
    /**
     * Create leave message
     */
    public static ChatMessage createLeaveMessage(String sessionId, String userId) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSender(userId);
        message.setContent(userId + " đã rời khỏi cuộc trò chuyện");
        message.setTimestamp(Instant.now());
        message.setMessageType("LEAVE");
        return message;
    }
    
    /**
     * Create error message
     */
    public static ChatMessage createErrorMessage(String sessionId, String errorContent) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSender(ChatSenderConstants.SYSTEM);
        message.setContent(errorContent);
        message.setTimestamp(Instant.now());
        message.setMessageType("ERROR");
        return message;
    }
    
    /**
     * Check if message is from AI
     */
    public boolean isFromAI() {
        return "AI Assistant".equals(sender) || "AI_RESPONSE".equals(messageType);
    }
    
    /**
     * Check if message has product recommendations
     */
    public boolean hasRecommendations() {
        return productRecommendations != null && !productRecommendations.isEmpty();
    }
    
    /**
     * Get recommendation count
     */
    public int getRecommendationCount() {
        return productRecommendations != null ? productRecommendations.size() : 0;
    }
}
