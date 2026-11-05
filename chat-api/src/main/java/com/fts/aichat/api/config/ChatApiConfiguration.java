package com.fts.aichat.api.config;

import com.fts.aichat.api.ratelimit.SlidingWindowRateLimiter;
import com.fts.aichat.core.ChatClientFactory;
import com.fts.aichat.core.FallbackChatService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(ChatApiProperties.class)
public class ChatApiConfiguration {

    @Bean
    ChatMemory publicChatMemory(ChatApiProperties properties) {
        return ChatClientFactory.messageWindowMemory(properties);
    }

    @Bean(name = "publicGroqChatClient")
    ChatClient publicGroqChatClient(ChatApiProperties properties, ChatMemory publicChatMemory) {
        return ChatClientFactory.groqClient(properties, publicChatMemory);
    }

    @Bean(name = "publicGeminiChatClient")
    ChatClient publicGeminiChatClient(ChatApiProperties properties, ChatMemory publicChatMemory) {
        return ChatClientFactory.geminiClient(properties, publicChatMemory);
    }

    @Bean
    FallbackChatService fallbackChatService(
            ChatApiProperties properties,
            @Qualifier("publicGroqChatClient") ChatClient publicGroqChatClient,
            @Qualifier("publicGeminiChatClient") ChatClient publicGeminiChatClient) {
        return new FallbackChatService(properties, publicGroqChatClient, publicGeminiChatClient);
    }

    @Bean
    SlidingWindowRateLimiter slidingWindowRateLimiter() {
        return new SlidingWindowRateLimiter(TimeUnit.MINUTES.toMillis(1));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SiteRegistry siteRegistry) {
        Set<String> origins = siteRegistry.allAllowedOrigins();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(false);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setMaxAge(3600L);

        if (origins.isEmpty() || origins.contains("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOriginPatterns(new ArrayList<>(origins));
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/public/**", config);
        source.registerCorsConfiguration("/widget.js", config);
        source.registerCorsConfiguration("/widget.css", config);
        return source;
    }
}
