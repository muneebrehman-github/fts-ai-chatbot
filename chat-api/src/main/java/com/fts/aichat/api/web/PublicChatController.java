package com.fts.aichat.api.web;

import com.fts.aichat.api.dto.PublicChatRequest;
import com.fts.aichat.api.dto.PublicChatResponse;
import com.fts.aichat.api.dto.PublicWidgetConfigResponse;
import com.fts.aichat.api.service.PublicChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public")
public class PublicChatController {

    private static final Logger log = LoggerFactory.getLogger(PublicChatController.class);

    private final PublicChatService publicChatService;

    public PublicChatController(PublicChatService publicChatService) {
        this.publicChatService = publicChatService;
    }

    @PostMapping("/chat")
    public PublicChatResponse chat(
            @Valid @RequestBody PublicChatRequest request,
            @RequestHeader(value = "Origin", required = false) String origin,
            HttpServletRequest httpRequest) {
        return publicChatService.chat(
                request.siteKey(),
                request.message(),
                request.conversationId(),
                origin,
                ClientIpResolver.resolve(httpRequest));
    }

    @GetMapping("/config")
    public PublicWidgetConfigResponse config(
            @RequestParam("siteKey") String siteKey,
            @RequestHeader(value = "Origin", required = false) String origin) {
        return publicChatService.widgetConfig(siteKey, origin);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", ex.getMessage() == null ? "Chat unavailable" : ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception ex) {
        log.error("Public chat failure", ex);
        String message = ex.getMessage() == null ? "Chat failed" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", message));
    }
}
