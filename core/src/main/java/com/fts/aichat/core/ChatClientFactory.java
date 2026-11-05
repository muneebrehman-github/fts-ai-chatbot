package com.fts.aichat.core;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StringUtils;

/**
 * Builds OpenAI-compatible ChatClients for Groq and Gemini.
 */
public final class ChatClientFactory {

    private ChatClientFactory() {
    }

    public static ChatMemory messageWindowMemory(LlmProviderSettings settings) {
        return MessageWindowChatMemory.builder()
                .maxMessages(Math.max(8, settings.getMaxMemoryMessages()))
                .build();
    }

    public static ChatClient groqClient(LlmProviderSettings settings, ChatMemory memory, Object... tools) {
        return buildClient(
                settings.getGroqBaseUrl(),
                settings.getGroqApiKey(),
                settings.getGroqModel(),
                settings,
                memory,
                tools);
    }

    public static ChatClient geminiClient(LlmProviderSettings settings, ChatMemory memory, Object... tools) {
        return buildClient(
                settings.getGeminiBaseUrl(),
                settings.getGeminiApiKey(),
                settings.getGeminiModel(),
                settings,
                memory,
                tools);
    }

    public static ChatClient groqClient(
            LlmProviderSettings settings,
            ChatMemory memory,
            String systemPromptOverride,
            Object... tools) {
        return buildClient(
                settings.getGroqBaseUrl(),
                settings.getGroqApiKey(),
                settings.getGroqModel(),
                settings,
                memory,
                systemPromptOverride,
                tools);
    }

    public static ChatClient geminiClient(
            LlmProviderSettings settings,
            ChatMemory memory,
            String systemPromptOverride,
            Object... tools) {
        return buildClient(
                settings.getGeminiBaseUrl(),
                settings.getGeminiApiKey(),
                settings.getGeminiModel(),
                settings,
                memory,
                systemPromptOverride,
                tools);
    }

    private static ChatClient buildClient(
            String baseUrl,
            String apiKey,
            String model,
            LlmProviderSettings settings,
            ChatMemory memory,
            Object... tools) {
        return buildClient(baseUrl, apiKey, model, settings, memory, settings.getSystemPrompt(), tools);
    }

    private static ChatClient buildClient(
            String baseUrl,
            String apiKey,
            String model,
            LlmProviderSettings settings,
            ChatMemory memory,
            String systemPrompt,
            Object... tools) {
        String key = StringUtils.hasText(apiKey) ? apiKey : "not-configured";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(key)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(settings.getTemperature())
                        .build())
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt == null ? "" : systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build());

        if (tools != null && tools.length > 0) {
            builder.defaultTools(tools);
        }
        return builder.build();
    }

    public static Object[] toToolArray(List<?> toolBeans) {
        if (toolBeans == null || toolBeans.isEmpty()) {
            return new Object[0];
        }
        return toolBeans.toArray();
    }
}
