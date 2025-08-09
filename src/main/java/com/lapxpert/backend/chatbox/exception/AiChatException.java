package com.lapxpert.backend.chatbox.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Unified exception cho tất cả AI Chat errors
 * Thay thế AiChatClientException, AiChatServerException, và AiChatTimeoutException
 */
public class AiChatException extends RuntimeException {

    /**
     * Enum định nghĩa các loại lỗi AI Chat
     */
    public enum ErrorType {
        CLIENT_ERROR,    // 4xx HTTP status codes
        SERVER_ERROR,    // 5xx HTTP status codes
        TIMEOUT_ERROR    // Request timeout scenarios
    }

    private final ErrorType errorType;
    private final HttpStatusCode httpStatus;
    private final String responseBody;
    private final Integer timeoutSeconds;

    /**
     * Constructor cho client errors với HTTP status
     */
    public AiChatException(HttpStatusCode httpStatus) {
        super(String.format("Lỗi yêu cầu AI chat không hợp lệ: %s", httpStatus.toString()));
        this.errorType = ErrorType.CLIENT_ERROR;
        this.httpStatus = httpStatus;
        this.responseBody = null;
        this.timeoutSeconds = null;
    }

    /**
     * Constructor cho client/server errors với HTTP status và response body
     */
    public AiChatException(HttpStatusCode httpStatus, String responseBody, ErrorType errorType) {
        super(String.format("Lỗi AI chat: %s - %s", httpStatus.toString(), responseBody));
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.timeoutSeconds = null;
    }

    /**
     * Constructor cho timeout errors
     */
    public AiChatException(int timeoutSeconds) {
        super(String.format("AI chat request timeout sau %d giây", timeoutSeconds));
        this.errorType = ErrorType.TIMEOUT_ERROR;
        this.httpStatus = null;
        this.responseBody = null;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Constructor với custom message và error type
     */
    public AiChatException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
        this.httpStatus = null;
        this.responseBody = null;
        this.timeoutSeconds = null;
    }

    /**
     * Constructor với custom message, cause và error type
     */
    public AiChatException(String message, Throwable cause, ErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = null;
        this.responseBody = null;
        this.timeoutSeconds = null;
    }

    /**
     * Constructor cho timeout với custom message
     */
    public AiChatException(String message, int timeoutSeconds) {
        super(message);
        this.errorType = ErrorType.TIMEOUT_ERROR;
        this.httpStatus = null;
        this.responseBody = null;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Constructor cho timeout với cause
     */
    public AiChatException(String message, Throwable cause, int timeoutSeconds) {
        super(message, cause);
        this.errorType = ErrorType.TIMEOUT_ERROR;
        this.httpStatus = null;
        this.responseBody = null;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Get error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Get HTTP status code (for client/server errors)
     */
    public HttpStatusCode getHttpStatus() {
        return httpStatus;
    }

    /**
     * Get response body if available (for client/server errors)
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * Get timeout value in seconds (for timeout errors)
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Get Vietnamese error message for business layer
     */
    public String getVietnameseMessage() {
        switch (errorType) {
            case CLIENT_ERROR:
                return "Yêu cầu AI chat không hợp lệ. Vui lòng kiểm tra lại thông tin và thử lại.";
            case SERVER_ERROR:
                return "Lỗi hệ thống AI chat. Vui lòng thử lại sau hoặc liên hệ bộ phận hỗ trợ kỹ thuật.";
            case TIMEOUT_ERROR:
                return String.format("Yêu cầu AI chat đã hết thời gian chờ sau %d giây. " +
                        "Vui lòng thử lại hoặc liên hệ bộ phận hỗ trợ.",
                        timeoutSeconds != null ? timeoutSeconds : 180);
            default:
                return "Lỗi AI chat không xác định. Vui lòng thử lại sau.";
        }
    }

    /**
     * Check if this is a client error
     */
    public boolean isClientError() {
        return errorType == ErrorType.CLIENT_ERROR;
    }

    /**
     * Check if this is a server error
     */
    public boolean isServerError() {
        return errorType == ErrorType.SERVER_ERROR;
    }

    /**
     * Check if this is a timeout error
     */
    public boolean isTimeoutError() {
        return errorType == ErrorType.TIMEOUT_ERROR;
    }
}
