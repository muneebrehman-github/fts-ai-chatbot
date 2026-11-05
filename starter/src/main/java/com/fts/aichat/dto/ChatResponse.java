package com.fts.aichat.dto;

public record ChatResponse(
        String reply,
        String conversationId,
        String provider,
        boolean fallbackUsed
) {
}
