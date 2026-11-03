package com.fts.aichat;

import org.springframework.security.core.Authentication;

/**
 * Maps the host application's {@link Authentication} into a {@link ChatUserContext}.
 * Products may override the default bean to expose user ids / display names from their principal type.
 */
@FunctionalInterface
public interface ChatUserContextFactory {

    ChatUserContext from(Authentication authentication);
}
