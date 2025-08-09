package com.lapxpert.backend.chatbox;

import com.lapxpert.backend.chatbox.constants.ChatSenderConstants;
import com.lapxpert.backend.chatbox.dto.ChatRequest;
import com.lapxpert.backend.chatbox.dto.ChatResponse;
import com.lapxpert.backend.chatbox.dto.ResponseType;
import com.lapxpert.backend.chatbox.exception.AiChatException;
import com.lapxpert.backend.common.dto.ChatMessage;
import com.lapxpert.backend.common.service.WebSocketIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified Controller cho AI Chat functionality (Consolidated Architecture)
 * Combines both REST and WebSocket functionality in a single controller
 * Provides core chat endpoint for product recommendations and conversational AI
 * Handles real-time AI chat messages với unified non-streaming approach
 * Sử dụng Vietnamese topic naming conventions: /topic/ai-chat/{sessionId}
 *
 * UNIFIED FEATURES:
 * - REST endpoint: POST /api/ai-chat/chat for direct chat requests
 * - WebSocket endpoints: /app/ai-chat/{sessionId}/send, /join, /leave for real-time chat
 * - Non-streaming processing: Uses simplified request-response patterns
 * - Unified AiChatService integration with @Async processing
 * - Vietnamese error messages and status updates for both paradigms
 * - Consolidated error handling supporting both ResponseEntity and WebSocket status patterns
 */
@RestController
@RequestMapping("/api/ai-chat")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final AiChatService aiChatService;
    private final WebSocketIntegrationService webSocketIntegrationService;
    
    // ================== REST ENDPOINTS ==================

    /**
     * Send chat message và get AI recommendations (REST endpoint)
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        try {
            log.info("Received chat request from user: {}", request.getUserId());

            ChatResponse response = aiChatService.sendChatRequest(request);

            log.info("Chat response generated with {} recommendations",
                response.getRecommendationCount());

            return ResponseEntity.ok(response);

        } catch (AiChatException e) {
            log.error("AI Chat error processing request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ChatResponse.builder()
                    .aiResponse(e.getVietnameseMessage())
                    .queryProcessed(request.getMessage())
                    .responseType(ResponseType.CONVERSATIONAL)
                    .fallbackUsed(true)
                    .build());
        } catch (Exception e) {
            log.error("Unexpected error processing chat request: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ChatResponse.builder()
                    .aiResponse("Xin lỗi, đã có lỗi không xác định xảy ra khi xử lý yêu cầu của bạn.")
                    .queryProcessed(request.getMessage())
                    .responseType(ResponseType.CONVERSATIONAL)
                    .fallbackUsed(true)
                    .build());
        }
    }

    // ================== WEBSOCKET ENDPOINTS ==================

    /**
     * Handle incoming chat messages from WebSocket clients
     * Vietnamese mapping: /app/ai-chat/{sessionId}/send
     * Now uses unified AiChatService with @Async processing
     */
    @MessageMapping("/ai-chat/{sessionId}/send")
    public void handleChatMessage(
            @DestinationVariable String sessionId,
            @Payload ChatMessage chatMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            log.info("Received AI chat message for session {}: {}", sessionId, chatMessage.getContent());

            // Validate session and message
            if (sessionId == null || sessionId.trim().isEmpty()) {
                log.warn("Invalid session ID received");
                return;
            }

            if (chatMessage.getContent() == null || chatMessage.getContent().trim().isEmpty()) {
                log.warn("Empty message content received for session {}", sessionId);
                return;
            }

            // Set message metadata
            chatMessage.setSessionId(sessionId);
            chatMessage.setTimestamp(Instant.now());

            // Preserve frontend message ID if present, otherwise generate new one
            boolean isNewId = false;
            if (chatMessage.getMessageId() == null || chatMessage.getMessageId().trim().isEmpty()) {
                chatMessage.setMessageId(UUID.randomUUID().toString());
                isNewId = true;
            }

            log.debug("Processing message with ID: {} (preserved: {}) for session: {}",
                chatMessage.getMessageId(), !isNewId, sessionId);

            // Broadcast user message to session subscribers
            webSocketIntegrationService.sendAiChatMessage(sessionId, chatMessage);

            // Send typing status
            webSocketIntegrationService.sendAiChatStatus(sessionId, "PROCESSING", "AI đang xử lý tin nhắn...");

            // Process message with unified AiChatService (replaces manual thread creation)
            // Default to non-streaming for backward compatibility
            aiChatService.processAiChatStreamingAsync(sessionId, chatMessage, false);

        } catch (Exception e) {
            handleWebSocketError(sessionId, e, "xử lý tin nhắn");
        }
    }

    /**
     * Handle chat session join
     * Vietnamese mapping: /app/ai-chat/{sessionId}/join
     */
    @MessageMapping("/ai-chat/{sessionId}/join")
    public void handleSessionJoin(
            @DestinationVariable String sessionId,
            @Payload ChatMessage joinMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            log.info("User joined AI chat session: {}", sessionId);

            // Set join message metadata
            joinMessage.setSessionId(sessionId);
            joinMessage.setTimestamp(Instant.now());
            joinMessage.setMessageId(UUID.randomUUID().toString());
            joinMessage.setMessageType("JOIN");

            // Send welcome message
            ChatMessage welcomeMessage = new ChatMessage();
            welcomeMessage.setSessionId(sessionId);
            welcomeMessage.setSender(ChatSenderConstants.AI_ASSISTANT);
            welcomeMessage.setContent("Xin chào! Tôi là trợ lý AI của LapXpert. Tôi có thể giúp bạn tìm kiếm và tư vấn sản phẩm laptop. Bạn cần hỗ trợ gì?");
            welcomeMessage.setTimestamp(Instant.now());
            welcomeMessage.setMessageId(UUID.randomUUID().toString());
            welcomeMessage.setMessageType("AI_RESPONSE");

            // Broadcast welcome message
            webSocketIntegrationService.sendAiChatMessage(sessionId, welcomeMessage);

            // Send ready status
            webSocketIntegrationService.sendAiChatStatus(sessionId, "READY", "Sẵn sàng trò chuyện");

        } catch (Exception e) {
            handleWebSocketError(sessionId, e, "tham gia phiên chat");
        }
    }

    /**
     * Handle chat session leave
     * Vietnamese mapping: /app/ai-chat/{sessionId}/leave
     */
    @MessageMapping("/ai-chat/{sessionId}/leave")
    public void handleSessionLeave(
            @DestinationVariable String sessionId,
            @Payload ChatMessage leaveMessage,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            log.info("User left AI chat session: {}", sessionId);

            // Set leave message metadata
            leaveMessage.setSessionId(sessionId);
            leaveMessage.setTimestamp(Instant.now());
            leaveMessage.setMessageId(UUID.randomUUID().toString());
            leaveMessage.setMessageType("LEAVE");

            // Send goodbye status
            webSocketIntegrationService.sendAiChatStatus(sessionId, "DISCONNECTED", "Đã ngắt kết nối");

        } catch (Exception e) {
            handleWebSocketError(sessionId, e, "rời khỏi phiên chat");
        }
    }

    // ================== UNIFIED ERROR HANDLING ==================

    /**
     * Handle AiChatException for REST endpoints
     * Returns standardized error response with Vietnamese messages
     */
    @ExceptionHandler(AiChatException.class)
    public ResponseEntity<ChatResponse> handleAiChatException(AiChatException e) {
        log.error("AI Chat error: {}", e.getMessage(), e);

        return ResponseEntity.internalServerError()
            .body(ChatResponse.builder()
                .aiResponse(e.getVietnameseMessage())
                .queryProcessed("") // No query available in exception context
                .responseType(ResponseType.CONVERSATIONAL)
                .fallbackUsed(true)
                .build());
    }

    /**
     * Handle validation errors for REST endpoints
     * Returns standardized error response with Vietnamese messages
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(IllegalArgumentException e) {
        log.warn("Validation error in AI Chat: {}", e.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "VALIDATION_ERROR");
        errorResponse.put("message", "Dữ liệu đầu vào không hợp lệ: " + e.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", 400);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle general exceptions for REST endpoints
     * Returns standardized error response with Vietnamese messages
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unexpected error in AI Chat controller", e);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "INTERNAL_ERROR");
        errorResponse.put("message", "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ bộ phận hỗ trợ kỹ thuật.");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", 500);

        return ResponseEntity.internalServerError().body(errorResponse);
    }

    /**
     * Helper method to handle WebSocket errors consistently
     * Sends Vietnamese error messages via WebSocket status updates
     */
    private void handleWebSocketError(String sessionId, Exception e, String operation) {
        log.error("WebSocket error during {} for session {}: {}", operation, sessionId, e.getMessage(), e);

        String vietnameseMessage;
        if (e instanceof AiChatException) {
            vietnameseMessage = ((AiChatException) e).getVietnameseMessage();
        } else if (e instanceof IllegalArgumentException) {
            vietnameseMessage = "Dữ liệu không hợp lệ: " + e.getMessage();
        } else {
            vietnameseMessage = "Đã có lỗi xảy ra khi " + operation + ". Vui lòng thử lại.";
        }

        // Send error status via WebSocket
        try {
            webSocketIntegrationService.sendAiChatStatus(sessionId, "ERROR", vietnameseMessage);
        } catch (Exception statusException) {
            log.error("Failed to send error status for session {}: {}", sessionId, statusException.getMessage());
        }
    }
}
