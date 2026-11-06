package com.fts.aichat.api.config;

import com.fts.aichat.core.LlmProviderSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fts.ai")
public class ChatApiProperties implements LlmProviderSettings {

    private final Chat chat = new Chat();
    private final PublicApi publicApi = new PublicApi();

    public Chat getChat() {
        return chat;
    }

    public PublicApi getPublic() {
        return publicApi;
    }

    @Override
    public boolean isEnabled() {
        return chat.isEnabled();
    }

    @Override
    public String getSystemPrompt() {
        return chat.getSystemPrompt();
    }

    @Override
    public String getGroqApiKey() {
        return chat.getGroqApiKey();
    }

    @Override
    public String getGroqBaseUrl() {
        return chat.getGroqBaseUrl();
    }

    @Override
    public String getGroqModel() {
        return chat.getGroqModel();
    }

    @Override
    public double getTemperature() {
        return chat.getTemperature();
    }

    @Override
    public String getGeminiApiKey() {
        return chat.getGeminiApiKey();
    }

    @Override
    public String getGeminiBaseUrl() {
        return chat.getGeminiBaseUrl();
    }

    @Override
    public String getGeminiModel() {
        return chat.getGeminiModel();
    }

    @Override
    public int getMaxMemoryMessages() {
        return chat.getMaxMemoryMessages();
    }

    public static class Chat {
        private boolean enabled = true;
        private String systemPrompt = """
                You are a helpful website assistant.
                Be concise, friendly, and accurate.
                If you do not know something, say so clearly.
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
    }

    public static class PublicApi {
        /**
         * Map key is a stable site id; each entry must define siteKey (or key is used as siteKey).
         */
        private Map<String, SiteConfig> sites = new LinkedHashMap<>();

        public Map<String, SiteConfig> getSites() {
            return sites;
        }

        public void setSites(Map<String, SiteConfig> sites) {
            this.sites = sites != null ? sites : new LinkedHashMap<>();
        }
    }

    public static class SiteConfig {
        private String siteKey = "";
        /** Soft kill switch; false rejects all public requests for this site. */
        private boolean enabled = true;
        /**
         * Optional ISO-8601 instant (e.g. {@code 2027-12-31T23:59:59Z}).
         * Null / omitted = never expires.
         */
        private Instant expiresAt;
        private List<String> allowedOrigins = new ArrayList<>();
        private String systemPrompt = "";
        private String title = "AI Assistant";
        private String subtitle = "Ask us anything";
        private String welcome = "Hi — how can I help you today?";
        private String placeholder = "Type a message…";
        private String launcherLabel = "AI assistant";
        private String primaryColor = "";
        private int rateLimitPerMinute = 60;
        private int rateLimitPerIpPerMinute = 30;

        public String getSiteKey() {
            return siteKey;
        }

        public void setSiteKey(String siteKey) {
            this.siteKey = siteKey;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        /** True when the site is enabled and not past {@link #expiresAt}. */
        public boolean isActive(Instant now) {
            if (!enabled) {
                return false;
            }
            Instant at = now != null ? now : Instant.now();
            return expiresAt == null || !at.isAfter(expiresAt);
        }

        public boolean isExpired(Instant now) {
            Instant at = now != null ? now : Instant.now();
            return expiresAt != null && at.isAfter(expiresAt);
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins != null ? allowedOrigins : new ArrayList<>();
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }

        public String getWelcome() {
            return welcome;
        }

        public void setWelcome(String welcome) {
            this.welcome = welcome;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }

        public String getLauncherLabel() {
            return launcherLabel;
        }

        public void setLauncherLabel(String launcherLabel) {
            this.launcherLabel = launcherLabel;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }

        public int getRateLimitPerIpPerMinute() {
            return rateLimitPerIpPerMinute;
        }

        public void setRateLimitPerIpPerMinute(int rateLimitPerIpPerMinute) {
            this.rateLimitPerIpPerMinute = rateLimitPerIpPerMinute;
        }
    }
}
