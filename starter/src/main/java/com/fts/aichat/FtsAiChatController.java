package com.fts.aichat;

import com.fts.aichat.dto.ChatRequest;
import com.fts.aichat.dto.ChatResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class FtsAiChatController {

    private static final Logger log = LoggerFactory.getLogger(FtsAiChatController.class);

    private final FtsAiChatService chatService;
    private final ChatUserContextFactory userContextFactory;

    public FtsAiChatController(FtsAiChatService chatService, ChatUserContextFactory userContextFactory) {
        this.chatService = chatService;
        this.userContextFactory = userContextFactory;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        ChatUserContext user = userContextFactory.from(authentication);
        log.debug("Chat request user={} conversationId={}", user.username(), request.conversationId());
        return chatService.chat(request.message(), request.conversationId(), user);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage() == null ? "Access denied" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage() == null ? "Chat unavailable" : ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception ex) {
        log.error("Chat endpoint failure", ex);
        String message = ex.getMessage() == null ? "Chat failed" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", message));
    }
}
