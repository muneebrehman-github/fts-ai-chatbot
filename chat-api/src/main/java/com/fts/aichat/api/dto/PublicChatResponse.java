package com.fts.aichat.api.dto;

public record PublicChatResponse(
        String reply,
        String conversationId,
        String provider,
        boolean fallbackUsed
) {
}
