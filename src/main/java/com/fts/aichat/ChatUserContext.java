package com.fts.aichat;

/**
 * Product-agnostic snapshot of the authenticated user, injected into the model prompt.
 */
public record ChatUserContext(
        String userId,
        String username,
        String displayName,
        String authorities
) {
}
