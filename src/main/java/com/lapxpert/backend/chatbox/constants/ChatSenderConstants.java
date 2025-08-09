package com.lapxpert.backend.chatbox.constants;

/**
 * Constants for standardized chat message sender values
 * Provides single source of truth for sender identification across LapXpert AI Chat system
 * Eliminates inconsistencies between 'AI_ASSISTANT' and 'AI Assistant' values
 */
public final class ChatSenderConstants {
    
    /**
     * AI Assistant sender value - standardized across all AI responses
     * Used for all AI-generated messages including chat responses, welcome messages, and streaming responses
     */
    public static final String AI_ASSISTANT = "AI Assistant";
    
    /**
     * System sender value - used for system-generated messages
     * Used for error messages, status updates, and other system notifications
     */
    public static final String SYSTEM = "System";
    
    /**
     * Customer/User sender value - used for user messages
     * Vietnamese language support for customer identification
     */
    public static final String CUSTOMER = "Khách hàng";
    
    /**
     * Private constructor to prevent instantiation
     * This is a utility class with only static constants
     */
    private ChatSenderConstants() {
        throw new UnsupportedOperationException("ChatSenderConstants is a utility class and cannot be instantiated");
    }
}
