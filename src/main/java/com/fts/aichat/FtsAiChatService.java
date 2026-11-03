package com.fts.aichat;

import com.fts.aichat.dto.ChatResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

public class FtsAiChatService {

    private static final Logger log = LoggerFactory.getLogger(FtsAiChatService.class);

    private final FtsAiChatProperties properties;
    private final ChatClient groqChatClient;
    private final ChatClient geminiChatClient;

    public FtsAiChatService(
            FtsAiChatProperties properties,
            @Qualifier("ftsAiGroqChatClient") ChatClient groqChatClient,
            @Qualifier("ftsAiGeminiChatClient") ChatClient geminiChatClient) {
        this.properties = properties;
        this.groqChatClient = groqChatClient;
        this.geminiChatClient = geminiChatClient;
    }

    public ChatResponse chat(String message, String conversationId, ChatUserContext user) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI chat is disabled (fts.ai.chat.enabled=false).");
        }
        if (!properties.hasGroqKey()) {
            throw new IllegalStateException("GROQ / fts.ai.chat.groq-api-key is not configured.");
        }

        String cid = StringUtils.hasText(conversationId) ? conversationId.trim() : UUID.randomUUID().toString();
        String contextualUserMessage = """
                [session userId=%s username=%s displayName=%s authorities=%s]
                %s
                """.formatted(
                nullToDash(user.userId()),
                nullToDash(user.username()),
                nullToDash(user.displayName()),
                nullToDash(user.authorities()),
                message.trim());

        try {
            String reply = call(groqChatClient, contextualUserMessage, cid);
            return new ChatResponse(reply, cid, "groq", false);
        } catch (Exception primaryFailure) {
            if (!isRateLimited(primaryFailure)) {
                throw primaryFailure instanceof RuntimeException re
                        ? re
                        : new IllegalStateException("Groq chat failed", primaryFailure);
            }
            if (!properties.hasGeminiKey()) {
                throw new IllegalStateException(
                        "Groq rate-limited (HTTP 429) and Gemini API key is not configured.",
                        primaryFailure);
            }
            log.warn("Groq HTTP 429 for conversation {} — falling back to Gemini ({})",
                    cid, properties.getGeminiModel());
            String reply = call(geminiChatClient, contextualUserMessage, cid);
            return new ChatResponse(reply, cid, "gemini", true);
        }
    }

    private static String call(ChatClient client, String userMessage, String conversationId) {
        String content = client.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return content == null || content.isBlank() ? "(empty model response)" : content.trim();
    }

    static boolean isRateLimited(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof RestClientResponseException rest && rest.getStatusCode().value() == 429) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
