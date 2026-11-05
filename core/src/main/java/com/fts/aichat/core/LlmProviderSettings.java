package com.fts.aichat.core;

/**
 * Provider credentials and model settings shared by starter and public chat-api.
 */
public interface LlmProviderSettings {

    boolean isEnabled();

    String getSystemPrompt();

    String getGroqApiKey();

    String getGroqBaseUrl();

    String getGroqModel();

    double getTemperature();

    String getGeminiApiKey();

    String getGeminiBaseUrl();

    String getGeminiModel();

    int getMaxMemoryMessages();

    default boolean hasGroqKey() {
        String key = getGroqApiKey();
        return key != null && !key.isBlank();
    }

    default boolean hasGeminiKey() {
        String key = getGeminiApiKey();
        return key != null && !key.isBlank();
    }
}
