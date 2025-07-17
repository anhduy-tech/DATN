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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified Service quản lý AI Chat functionality với Python FastAPI integration (Streamlined)
 * Handles process lifecycle, REST API communication, và WebSocket async processing
 * Consolidates functionality from former AsyncAiChatService for streamlined architecture
 *
 * STREAMLINED ARCHITECTURE:
 * - Single endpoint approach: All chat uses /chat/recommend
 * - No intent classification: AI model decides response type
 * - GitHub AI integration: Mistral Medium 3 model
 * - Vietnamese-first with product context awareness
 * - Removed complex health monitoring and progress tracking
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {
    
    private final AiChatConfig config;

    @Qualifier("aiChatWebClient")
    private final WebClient webClient;

    private final WebSocketIntegrationService webSocketIntegrationService;

    // ================== SERVICE STATE ==================

    private Process pythonProcess;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    @PostConstruct
    public void initialize() {
        if (!config.isEnabled()) {
            log.info("AI Chat service is disabled, skipping initialization");
            return;
        }

        try {
            config.validateConfig();
            startPythonService();
            log.info("AI Chat service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize AI Chat service: {}", e.getMessage(), e);
            // Don't throw exception to prevent application startup failure
            // Service will operate in fallback mode
            isRunning.set(false);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down AI Chat service...");

        // Stop Python process
        stopPythonService();
        
        log.info("AI Chat service shutdown completed");
    }
    
    /**
     * Start Python FastAPI service
     */
    private void startPythonService() {
        if (isRunning.get()) {
            log.warn("Python service is already running");
            return;
        }
        
        try {
            log.info("Starting Python FastAPI service...");
            
            // Validate paths
            File scriptFile = new File(config.getPython().getScriptPath());
            File pythonExecutable = new File(config.getPythonExecutablePath());
            
            if (!scriptFile.exists()) {
                throw new RuntimeException("Python script not found: " + scriptFile.getAbsolutePath());
            }
            
            if (!pythonExecutable.exists()) {
                throw new RuntimeException("Python executable not found: " + pythonExecutable.getAbsolutePath());
            }
            
            // Build process command
            ProcessBuilder processBuilder = new ProcessBuilder(
                config.getPythonExecutablePath(),
                config.getPython().getScriptPath()
            );
            
            // Set working directory
            processBuilder.directory(new File(config.getPython().getVenvPath()).getParentFile());
            
            // Redirect output for debugging
            processBuilder.redirectErrorStream(true);
            
            // Start process
            pythonProcess = processBuilder.start();
            isRunning.set(true);
            
            log.info("Python FastAPI service started with PID: {}", pythonProcess.pid());
            
            // Wait for service to be ready
            waitForServiceReady();
            
        } catch (Exception e) {
            isRunning.set(false);
            log.error("Failed to start Python service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start AI Chat Python service", e);
        }
    }
    
    /**
     * Stop Python FastAPI service
     */
    private void stopPythonService() {
        if (!isRunning.get() || pythonProcess == null) {
            return;
        }
        
        try {
            log.info("Stopping Python FastAPI service...");
            
            // Graceful shutdown
            pythonProcess.destroy();
            
            // Wait for graceful shutdown
            boolean terminated = pythonProcess.waitFor(
                config.getProcess().getShutdownTimeoutSeconds(), 
                TimeUnit.SECONDS
            );
            
            if (!terminated) {
                log.warn("Python process did not terminate gracefully, forcing shutdown");
                pythonProcess.destroyForcibly();
                pythonProcess.waitFor(5, TimeUnit.SECONDS);
            }
            
            isRunning.set(false);
            log.info("Python FastAPI service stopped");
            
        } catch (Exception e) {
            log.error("Error stopping Python service: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Wait for service to be ready with proper HTTP health checking
     */
    private void waitForServiceReady() {
        log.info("Waiting for Python service to be ready...");

        int maxAttempts = config.getProcess().getStartupTimeoutSeconds();
        for (int i = 0; i < maxAttempts; i++) {
            try {
                // Check if process is still running
                if (pythonProcess == null || !pythonProcess.isAlive()) {
                    throw new RuntimeException("Python process has terminated unexpectedly");
                }

                // Perform HTTP health check
                if (checkServiceHealth()) {
                    log.info("Python service is ready and healthy after {} seconds", i + 1);
                    return;
                }

                log.debug("Python service not ready yet, waiting... (attempt {}/{})", i + 1, maxAttempts);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for service", e);
            } catch (Exception e) {
                log.debug("Health check failed on attempt {}: {}", i + 1, e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for service", ie);
                }
            }
        }

        throw new RuntimeException("Python service failed to become healthy within " + maxAttempts + " seconds");
    }

    /**
     * Check service health via HTTP endpoint
     */
    private boolean checkServiceHealth() {
        try {
            String healthResponse = webClient.get()
                .uri(config.getHealthCheckUrl())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();

            // Parse response to check if service is healthy
            if (healthResponse != null && healthResponse.contains("\"status\":\"healthy\"")) {
                log.debug("Health check passed: service is healthy");
                return true;
            } else if (healthResponse != null && healthResponse.contains("\"status\":\"degraded\"")) {
                log.warn("Health check shows degraded status, but service is responding");
                return true; // Accept degraded status as ready
            } else {
                log.debug("Health check failed: unexpected response");
                return false;
            }
        } catch (Exception e) {
            log.debug("Health check request failed: {}", e.getMessage());
            return false;
        }
    }
    

    








    // ================== CORE SERVICE METHODS ==================

    /**
     * Send chat request with full metadata and response type classification
     * Uses unified /chat/recommend endpoint for streamlined architecture
     */
    public ChatResponse sendChatRequest(ChatRequest request) {
        if (!isRunning.get()) {
            log.warn("AI Chat service is not available, returning fallback enhanced response");
            return createFallbackEnhancedResponse(request.getMessage());
        }

        try {
            log.info("Processing chat request: {}", request.getMessage());

            ChatResponse response = webClient.post()
                .uri(config.getChatRecommendUrl())
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("Unknown client error")
                        .map(body -> new AiChatException(clientResponse.statusCode(), body, AiChatException.ErrorType.CLIENT_ERROR)))
                .onStatus(status -> status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("Unknown server error")
                        .map(body -> new AiChatException(clientResponse.statusCode(), body, AiChatException.ErrorType.SERVER_ERROR)))
                .bodyToMono(ChatResponse.class)
                .onErrorMap(WebClientRequestException.class,
                    ex -> new AiChatException(config.getRequest().getTimeoutSeconds()))
                .block(Duration.ofSeconds(config.getRequest().getTimeoutSeconds()));

            log.info("Chat request completed - Type: {}, Recommendations: {}",
                response.getResponseType(),
                response.getProductRecommendations() != null ? response.getProductRecommendations().size() : 0);

            return response;

        } catch (AiChatException e) {
            log.error("Enhanced chat v2 failed: {}", e.getMessage());
            return createFallbackEnhancedResponse(request.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in enhanced chat v2: {}", e.getMessage(), e);
            return createFallbackEnhancedResponse(request.getMessage());
        }
    }





    /**
     * Create fallback enhanced response when AI service is unavailable
     */
    private ChatResponse createFallbackEnhancedResponse(String message) {
        String fallbackMessage = "Xin lỗi, dịch vụ AI chat hiện tại không khả dụng. " +
            "Vui lòng thử lại sau hoặc liên hệ với bộ phận hỗ trợ để được tư vấn về sản phẩm.";

        return ChatResponse.builder()
                .aiResponse(fallbackMessage)
                .queryProcessed(message)
                .responseType(ResponseType.CONVERSATIONAL)
                .fallbackUsed(true)
                .build();
    }

    // ================== SERVICE STATUS METHODS ==================

    /**
     * Get service status
     */
    public boolean isServiceAvailable() {
        return isRunning.get();
    }

    /**
     * Get service info
     */
    public String getServiceInfo() {
        return String.format("AI Chat Service - Running: %s, URL: %s",
            isRunning.get(), config.getService().getBaseUrl());
    }

    /**
     * Restart service if needed
     */
    public void restartIfNeeded() {
        if (isRunning.get()) {
            log.info("Restarting AI Chat service...");
            stopPythonService();
            try {
                Thread.sleep(2000); // Wait before restart
                startPythonService();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========== ASYNC METHODS (Merged from AsyncAiChatService) ==========

    /**
     * Process AI chat message asynchronously using @Async annotation
     * Sử dụng enhancedWebSocketTaskScheduler từ WebSocketServiceConfig
     *
     * @param sessionId Chat session ID
     * @param userMessage User's chat message
     * @return CompletableFuture<Void> for async processing
     */
    @Async("enhancedWebSocketTaskScheduler")
    @Retryable(
        retryFor = {SocketTimeoutException.class},
        maxAttempts = 2,
        recover = "recoverFromTimeout"
    )
    public CompletableFuture<Void> processAiChatAsync(String sessionId, ChatMessage userMessage) throws SocketTimeoutException {
        try {
            log.info("Bắt đầu xử lý AI chat async cho session: {}", sessionId);

            // Create AI chat request
            ChatRequest aiRequest = new ChatRequest();
            aiRequest.setMessage(userMessage.getContent());
            aiRequest.setUserId(userMessage.getSender());
            aiRequest.setSessionId(sessionId);

            // Get AI response using existing service method
            ChatResponse aiResponse = sendChatRequest(aiRequest);

            // Create AI response message
            ChatMessage aiMessage = new ChatMessage();
            aiMessage.setContent(aiResponse.getAiResponse());
            aiMessage.setSender(ChatSenderConstants.AI_ASSISTANT);
            aiMessage.setSessionId(sessionId);
            aiMessage.setTimestamp(Instant.now());
            aiMessage.setMessageId(UUID.randomUUID().toString());
            aiMessage.setMessageType("AI_RESPONSE");

            log.debug("Created AI response message with type: {} for session: {}",
                aiMessage.getMessageType(), sessionId);

            // Send AI response via WebSocket
            webSocketIntegrationService.sendAiChatMessage(sessionId, aiMessage);

            // Send ready status
            webSocketIntegrationService.sendAiChatStatus(sessionId, "READY", "Sẵn sàng cho tin nhắn tiếp theo");

            log.info("AI response sent successfully for session: {}", sessionId);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            // Check if it's a timeout-related exception
            if (isTimeoutException(e)) {
                log.warn("Timeout occurred for session {}, will retry: {}", sessionId, e.getMessage());
                throw new SocketTimeoutException("AI Chat timeout: " + e.getMessage());
            }

            log.error("Error processing AI chat for session {}: {}", sessionId, e.getMessage(), e);

            // Send error response
            sendErrorResponse(sessionId, "Xin lỗi, đã có lỗi xảy ra khi xử lý yêu cầu của bạn. Vui lòng thử lại sau.");

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Recovery method for timeout scenarios
     * Được gọi khi retry attempts đã hết
     */
    @Recover
    public CompletableFuture<Void> recoverFromTimeout(SocketTimeoutException ex, String sessionId, ChatMessage userMessage) {
        log.error("Timeout recovery triggered for session {}: {}", sessionId, ex.getMessage());

        // Send timeout-specific error message
        sendErrorResponse(sessionId,
            "Xin lỗi, AI đang quá tải và không thể xử lý yêu cầu trong thời gian cho phép. " +
            "Vui lòng thử lại sau vài phút.");

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process AI chat message (simplified non-streaming approach)
     * Always uses non-streaming processing for simplified architecture
     */
    @Async("enhancedWebSocketTaskScheduler")
    public CompletableFuture<Void> processAiChatStreamingAsync(String sessionId, ChatMessage userMessage, boolean useStreaming) {
        // Always use non-streaming processing for simplified architecture
        try {
            return processAiChatAsync(sessionId, userMessage);
        } catch (SocketTimeoutException e) {
            log.error("Timeout in processing for session {}: {}", sessionId, e.getMessage());
            sendErrorResponse(sessionId, "Xin lỗi, đã có lỗi timeout xảy ra. Vui lòng thử lại.");
            return CompletableFuture.completedFuture(null);
        }
    }

    // ========== HELPER METHODS FOR ASYNC PROCESSING ==========

    /**
     * Check if exception is timeout-related
     */
    private boolean isTimeoutException(Exception e) {
        return e instanceof SocketTimeoutException ||
               e instanceof java.util.concurrent.TimeoutException ||
               (e.getCause() != null && isTimeoutException((Exception) e.getCause())) ||
               e.getMessage().toLowerCase().contains("timeout");
    }

    /**
     * Send error response via WebSocket
     */
    private void sendErrorResponse(String sessionId, String errorMessage) {
        try {
            ChatMessage errorMsg = ChatMessage.createErrorMessage(sessionId, errorMessage);
            webSocketIntegrationService.sendAiChatMessage(sessionId, errorMsg);
            webSocketIntegrationService.sendAiChatStatus(sessionId, "ERROR", errorMessage);
        } catch (Exception e) {
            log.error("Failed to send error response for session {}: {}", sessionId, e.getMessage());
        }
    }

    // ================== STREAMING METHODS ==================










}
