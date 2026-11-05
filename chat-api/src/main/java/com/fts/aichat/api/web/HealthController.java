package com.fts.aichat.api.web;

import com.fts.aichat.api.config.ChatApiProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ChatApiProperties properties;

    public HealthController(ChatApiProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "chatEnabled", properties.isEnabled(),
                "groqConfigured", properties.hasGroqKey(),
                "geminiConfigured", properties.hasGeminiKey());
    }
}
