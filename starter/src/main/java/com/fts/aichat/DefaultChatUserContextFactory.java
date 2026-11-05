package com.fts.aichat;

import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Default mapping: username from Authentication, authorities joined, displayName = username.
 * Products with richer principals should provide their own {@link ChatUserContextFactory} bean.
 */
public class DefaultChatUserContextFactory implements ChatUserContextFactory {

    @Override
    public ChatUserContext from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authenticated session required for AI chat.");
        }
        String username = authentication.getName();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.joining(", "));

        Object principal = authentication.getPrincipal();
        String displayName = username;
        String userId = username;
        if (principal instanceof UserDetails details) {
            username = details.getUsername();
            displayName = details.getUsername();
            userId = details.getUsername();
        }

        return new ChatUserContext(userId, username, displayName, authorities);
    }
}
