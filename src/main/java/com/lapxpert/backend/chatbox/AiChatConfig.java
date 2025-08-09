package com.lapxpert.backend.chatbox;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Streamlined Configuration cho AI Chat Service
 * Quản lý essential settings cho Python FastAPI service integration
 * Focuses on core service connection, timeouts, and Python script execution
 */
@Configuration
@Data
@Slf4j
public class AiChatConfig {

    // Service enable/disable
    private boolean enabled = true;

    // Python script configuration
    private Python python = new Python();

    // Service configuration
    private Service service = new Service();

    // Process management configuration
    private Process process = new Process();

    // Request configuration
    private Request request = new Request();

    @Data
    public static class Python {
        // Python script path (updated to main.py)
        private String scriptPath = "src/main/java/com/lapxpert/backend/chatbox/python/main.py";
        // Virtual environment path (corrected to actual .venv location in project root)
        private String venvPath = ".venv";
    }
    
    @Data
    public static class Service {
        private String host = "localhost";
        private int port = 8001;
        private String baseUrl = "http://localhost:8001";
    }

    @Data
    public static class Process {
        private int startupTimeoutSeconds = 60;
        private int shutdownTimeoutSeconds = 30;
    }
    
    @Data
    public static class Request {
        private int timeoutSeconds = 180;
        private int connectTimeoutSeconds = 30;
        private int socketTimeoutSeconds = 180;
        private int retryAttempts = 3;
    }

    /**
     * Get full URL for specific endpoint
     */
    public String getEndpointUrl(String endpoint) {
        return service.baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
    }

    /**
     * Get chat recommend URL
     */
    public String getChatRecommendUrl() {
        return getEndpointUrl("/chat/recommend");
    }

    /**
     * Get conversational AI URL
     */
    public String getConversationalUrl() {
        return getEndpointUrl("/chat/conversational");
    }



    /**
     * Get health check URL
     */
    public String getHealthCheckUrl() {
        return getEndpointUrl("/health");
    }
    
    /**
     * Get Python executable path
     * Tries virtual environment first, falls back to system python3
     */
    public String getPythonExecutablePath() {
        // Try venv python first if venv path is configured
        if (python.venvPath != null && !python.venvPath.trim().isEmpty()) {
            String venvPython = python.venvPath + "/bin/python";
            if (new java.io.File(venvPython).exists()) {
                return venvPython;
            }
        }

        // Fallback to system python3
        return "python3";
    }

    /**
     * Validate configuration
     */
    public void validateConfig() {
        if (!enabled) {
            log.info("AI Chat service is disabled");
            return;
        }

        if (python.scriptPath == null || python.scriptPath.trim().isEmpty()) {
            throw new IllegalArgumentException("AI Chat Python script path cannot be empty");
        }

        // Verify script file exists
        if (!new java.io.File(python.scriptPath).exists()) {
            throw new IllegalArgumentException("AI Chat Python script file does not exist: " + python.scriptPath);
        }

        if (service.port <= 0 || service.port > 65535) {
            throw new IllegalArgumentException("AI Chat service port must be between 1 and 65535");
        }

        log.info("AI Chat configuration validated successfully");
    }
}
