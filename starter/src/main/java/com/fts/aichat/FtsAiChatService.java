package com.fts.aichat;

import com.fts.aichat.core.ChatResult;
import com.fts.aichat.core.FallbackChatService;
import com.fts.aichat.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;

public class FtsAiChatService {

    private final FallbackChatService fallbackChatService;

    public FtsAiChatService(
            FtsAiChatProperties properties,
            @Qualifier("ftsAiGroqChatClient") ChatClient groqChatClient,
            @Qualifier("ftsAiGeminiChatClient") ChatClient geminiChatClient) {
        this.fallbackChatService = new FallbackChatService(properties, groqChatClient, geminiChatClient);
    }

    public ChatResponse chat(String message, String conversationId, ChatUserContext user) {
        String contextualUserMessage = """
                [session userId=%s username=%s displayName=%s authorities=%s]
                %s
                """.formatted(
                nullToDash(user.userId()),
                nullToDash(user.username()),
                nullToDash(user.displayName()),
                nullToDash(user.authorities()),
                message == null ? "" : message.trim());

        ChatResult result = fallbackChatService.chat(contextualUserMessage, conversationId);
        return new ChatResponse(result.reply(), result.conversationId(), result.provider(), result.fallbackUsed());
    }

    static boolean isRateLimited(Throwable error) {
        return FallbackChatService.isRateLimited(error);
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
