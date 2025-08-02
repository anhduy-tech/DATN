package com.lapxpert.backend.websocket.service;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ActiveSessionService {
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public void addSession(String sessionId) {
        activeSessions.add(sessionId);
    }

    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

    public Set<String> getActiveSessions() {
        return activeSessions;
    }
}
