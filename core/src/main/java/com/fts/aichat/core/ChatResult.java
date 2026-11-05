package com.fts.aichat.core;

/**
 * Provider-agnostic chat outcome (mapped to starter/public DTOs by callers).
 */
public record ChatResult(
        String reply,
        String conversationId,
        String provider,
        boolean fallbackUsed
) {
}
