package com.fts.aichat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fts.ai.chat")
public class FtsAiChatProperties {

    /**
     * Master switch for the chat API auto-configuration.
     */
    private boolean enabled = true;

    /**
     * When true, serves /js/portal-chat-widget.js and /css/portal-chat-widget.css from this jar.
     */
    private boolean widgetEnabled = true;

    private String systemPrompt = """
            You are an embedded AI assistant for an FTS product portal.
            Be concise, accurate, and action-oriented. Prefer bullet steps for how-tos.
            Use registered tools when they improve factual answers.
            If a tool reports AccessDenied, explain the missing permission clearly.
            Never invent operational facts — use tools or say you do not know.
            """;

    private String groqApiKey = "";
    private String groqBaseUrl = "https://api.groq.com/openai";
    private String groqModel = "openai/gpt-oss-120b";
    private double temperature = 0.3;

    private String geminiApiKey = "";
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
    private String geminiModel = "gemini-2.5-flash";

    private int maxMemoryMessages = 40;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWidgetEnabled() {
        return widgetEnabled;
    }

    public void setWidgetEnabled(boolean widgetEnabled) {
        this.widgetEnabled = widgetEnabled;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public String getGroqBaseUrl() {
        return groqBaseUrl;
    }

    public void setGroqBaseUrl(String groqBaseUrl) {
        this.groqBaseUrl = groqBaseUrl;
    }

    public String getGroqModel() {
        return groqModel;
    }

    public void setGroqModel(String groqModel) {
        this.groqModel = groqModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getGeminiBaseUrl() {
        return geminiBaseUrl;
    }

    public void setGeminiBaseUrl(String geminiBaseUrl) {
        this.geminiBaseUrl = geminiBaseUrl;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public int getMaxMemoryMessages() {
        return maxMemoryMessages;
    }

    public void setMaxMemoryMessages(int maxMemoryMessages) {
        this.maxMemoryMessages = maxMemoryMessages;
    }

    public boolean hasGroqKey() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    public boolean hasGeminiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
