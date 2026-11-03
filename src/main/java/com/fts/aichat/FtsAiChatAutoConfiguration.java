package com.fts.aichat;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "fts.ai.chat", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FtsAiChatProperties.class)
public class FtsAiChatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ChatUserContextFactory chatUserContextFactory() {
        return new DefaultChatUserContextFactory();
    }

    @Bean(name = "ftsAiChatMemory")
    @ConditionalOnMissingBean(name = "ftsAiChatMemory")
    ChatMemory ftsAiChatMemory(FtsAiChatProperties properties) {
        return MessageWindowChatMemory.builder()
                .maxMessages(Math.max(8, properties.getMaxMemoryMessages()))
                .build();
    }

    @Bean(name = "ftsAiGroqChatClient")
    ChatClient ftsAiGroqChatClient(
            FtsAiChatProperties properties,
            ChatMemory ftsAiChatMemory,
            ObjectProvider<FtsAiChatTools> tools) {
        String apiKey = StringUtils.hasText(properties.getGroqApiKey())
                ? properties.getGroqApiKey()
                : "not-configured";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.getGroqBaseUrl())
                .apiKey(apiKey)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getGroqModel())
                        .temperature(properties.getTemperature())
                        .build())
                .build();
        return buildClient(model, properties, ftsAiChatMemory, tools);
    }

    @Bean(name = "ftsAiGeminiChatClient")
    ChatClient ftsAiGeminiChatClient(
            FtsAiChatProperties properties,
            ChatMemory ftsAiChatMemory,
            ObjectProvider<FtsAiChatTools> tools) {
        String apiKey = StringUtils.hasText(properties.getGeminiApiKey())
                ? properties.getGeminiApiKey()
                : "not-configured";
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.getGeminiBaseUrl())
                .apiKey(apiKey)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getGeminiModel())
                        .temperature(properties.getTemperature())
                        .build())
                .build();
        return buildClient(model, properties, ftsAiChatMemory, tools);
    }

    @Bean
    @ConditionalOnMissingBean
    FtsAiChatService ftsAiChatService(
            FtsAiChatProperties properties,
            @Qualifier("ftsAiGroqChatClient") ChatClient ftsAiGroqChatClient,
            @Qualifier("ftsAiGeminiChatClient") ChatClient ftsAiGeminiChatClient) {
        return new FtsAiChatService(properties, ftsAiGroqChatClient, ftsAiGeminiChatClient);
    }

    @Bean
    @ConditionalOnMissingBean
    FtsAiChatController ftsAiChatController(
            FtsAiChatService ftsAiChatService,
            ChatUserContextFactory chatUserContextFactory) {
        return new FtsAiChatController(ftsAiChatService, chatUserContextFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fts.ai.chat", name = "widget-enabled", havingValue = "true", matchIfMissing = true)
    WebMvcConfigurer ftsAiChatWidgetResources() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Packaged under META-INF/resources — Spring Boot serves them at classpath root as well.
                registry.addResourceHandler("/js/portal-chat-widget.js")
                        .addResourceLocations(
                                "classpath:/META-INF/resources/static/js/",
                                "classpath:/static/js/");
                registry.addResourceHandler("/css/portal-chat-widget.css")
                        .addResourceLocations(
                                "classpath:/META-INF/resources/static/css/",
                                "classpath:/static/css/");
            }
        };
    }

    private static ChatClient buildClient(
            OpenAiChatModel model,
            FtsAiChatProperties properties,
            ChatMemory memory,
            ObjectProvider<FtsAiChatTools> tools) {
        ChatClient.Builder builder = ChatClient.builder(model)
                .defaultSystem(properties.getSystemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build());

        List<FtsAiChatTools> toolBeans = tools.orderedStream().toList();
        if (!toolBeans.isEmpty()) {
            builder.defaultTools(toolBeans.toArray());
        }
        return builder.build();
    }
}
