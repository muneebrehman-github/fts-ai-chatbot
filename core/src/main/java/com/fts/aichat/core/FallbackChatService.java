package com.fts.aichat.core;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared Groq-primary chat with Gemini fallback on HTTP 429.
 */
public class FallbackChatService {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatService.class);

    private final LlmProviderSettings settings;
    private final ChatClient groqChatClient;
    private final ChatClient geminiChatClient;

    public FallbackChatService(
            LlmProviderSettings settings,
            ChatClient groqChatClient,
            ChatClient geminiChatClient) {
        this.settings = settings;
        this.groqChatClient = groqChatClient;
        this.geminiChatClient = geminiChatClient;
    }

    public ChatResult chat(String userMessage, String conversationId) {
        return chat(userMessage, conversationId, null);
    }

    /**
     * @param systemPromptOverride when non-blank, replaces the client's default system prompt for this call
     */
    public ChatResult chat(String userMessage, String conversationId, String systemPromptOverride) {
        if (!settings.isEnabled()) {
            throw new IllegalStateException("AI chat is disabled.");
        }
        if (!settings.hasGroqKey()) {
            throw new IllegalStateException("GROQ / groq-api-key is not configured.");
        }

        String cid = StringUtils.hasText(conversationId) ? conversationId.trim() : UUID.randomUUID().toString();
        String message = userMessage == null ? "" : userMessage.trim();

        try {
            String reply = call(groqChatClient, message, cid, systemPromptOverride);
            return new ChatResult(reply, cid, "groq", false);
        } catch (Exception primaryFailure) {
            if (!isRateLimited(primaryFailure)) {
                throw primaryFailure instanceof RuntimeException re
                        ? re
                        : new IllegalStateException("Groq chat failed", primaryFailure);
            }
            if (!settings.hasGeminiKey()) {
                throw new IllegalStateException(
                        "Groq rate-limited (HTTP 429) and Gemini API key is not configured.",
                        primaryFailure);
            }
            log.warn("Groq HTTP 429 for conversation {} — falling back to Gemini ({})",
                    cid, settings.getGeminiModel());
            String reply = call(geminiChatClient, message, cid, systemPromptOverride);
            return new ChatResult(reply, cid, "gemini", true);
        }
    }

    private static String call(
            ChatClient client,
            String userMessage,
            String conversationId,
            String systemPromptOverride) {
        ChatClient.ChatClientRequestSpec spec = client.prompt();
        if (StringUtils.hasText(systemPromptOverride)) {
            spec = spec.system(systemPromptOverride.trim());
        }
        String content = spec
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return content == null || content.isBlank() ? "(empty model response)" : content.trim();
    }

    public static boolean isRateLimited(Throwable error) {
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
}
