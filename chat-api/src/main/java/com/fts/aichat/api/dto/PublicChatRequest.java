package com.fts.aichat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicChatRequest(
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 120) String conversationId,
        @NotBlank @Size(max = 120) String siteKey
) {
}
