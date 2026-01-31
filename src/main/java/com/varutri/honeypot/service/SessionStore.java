package com.varutri.honeypot.service;

import com.varutri.honeypot.dto.ChatRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session storage for tracking conversations
 */
@Slf4j
@Service
public class SessionStore {

    private final ConcurrentHashMap<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * Get or create session data
     */
    public SessionData getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            log.info("Creating new session: {}", id);
            return new SessionData(id);
        });
    }

    /**
     * Add message to conversation history
     */
    public void addMessage(String sessionId, String role, String content) {
        SessionData session = getOrCreateSession(sessionId);
        session.addMessage(role, content);
        log.debug("Added message to session {}: {} - {}", sessionId, role, content);
    }

    /**
     * Get conversation history
     */
    public List<ChatRequest.ConversationMessage> getConversationHistory(String sessionId) {
        return getOrCreateSession(sessionId).getConversationHistory();
    }

    /**
     * Get turn count for session
     */
    public int getTurnCount(String sessionId) {
        return getOrCreateSession(sessionId).getTurnCount();
    }

    /**
     * Check if session should trigger final callback
     */
    public boolean shouldTriggerCallback(String sessionId, int maxTurns) {
        int turnCount = getTurnCount(sessionId);
        return turnCount >= maxTurns;
    }

    /**
     * Clear session data
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Cleared session: {}", sessionId);
    }

    /**
     * Get all messages as strings for intelligence extraction
     */
    public List<String> getAllMessages(String sessionId) {
        return getOrCreateSession(sessionId).getConversationHistory().stream()
                .map(ChatRequest.ConversationMessage::getContent)
                .toList();
    }

    @Data
    @AllArgsConstructor
    public static class SessionData {
        private String sessionId;
        private List<ChatRequest.ConversationMessage> conversationHistory;
        private int turnCount;

        public SessionData(String sessionId) {
            this.sessionId = sessionId;
            this.conversationHistory = new ArrayList<>();
            this.turnCount = 0;
        }

        public void addMessage(String role, String content) {
            conversationHistory.add(new ChatRequest.ConversationMessage(role, content));
            if ("user".equals(role)) {
                turnCount++;
            }
        }
    }
}
