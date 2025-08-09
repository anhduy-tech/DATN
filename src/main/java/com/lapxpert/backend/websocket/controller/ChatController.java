package com.lapxpert.backend.websocket.controller;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/public")
    public void sendPublicMessage(@RequestBody ChatMessage message) {
        message.setPrivate(false); // Đánh dấu là công khai
        messagingTemplate.convertAndSend("/topic/chatbox/" + message.getSessionId(), message);
    }

    @PostMapping("/private")
    public void sendPrivateMessage(@RequestBody ChatMessage message) {
        message.setPrivate(true); // Đánh dấu là riêng tư
        messagingTemplate.convertAndSend("/user/" + message.getTargetUser() + "/queue/chat", message);
    }

    @PostMapping("/join")
    public void sendJoinNotification(@RequestBody JoinMessage message) {
        messagingTemplate.convertAndSend("/topic/join/" + message.getSessionId(), message);
    }
}

class ChatMessage {
    private String sessionId;
    private String sender;
    private String content;
    private String targetUser;
    private long timestamp;
    private boolean isPrivate;

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTargetUser() { return targetUser; }
    public void setTargetUser(String targetUser) { this.targetUser = targetUser; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
}

class JoinMessage {
    private String sessionId;
    private String username;
    private long timestamp;

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}