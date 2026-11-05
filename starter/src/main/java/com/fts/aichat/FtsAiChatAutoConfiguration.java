package com.fts.aichat;

import com.fts.aichat.core.ChatClientFactory;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
        return ChatClientFactory.messageWindowMemory(properties);
    }

    @Bean(name = "ftsAiGroqChatClient")
    ChatClient ftsAiGroqChatClient(
            FtsAiChatProperties properties,
            ChatMemory ftsAiChatMemory,
            ObjectProvider<FtsAiChatTools> tools) {
        Object[] toolBeans = ChatClientFactory.toToolArray(tools.orderedStream().toList());
        return ChatClientFactory.groqClient(properties, ftsAiChatMemory, toolBeans);
    }

    @Bean(name = "ftsAiGeminiChatClient")
    ChatClient ftsAiGeminiChatClient(
            FtsAiChatProperties properties,
            ChatMemory ftsAiChatMemory,
            ObjectProvider<FtsAiChatTools> tools) {
        Object[] toolBeans = ChatClientFactory.toToolArray(tools.orderedStream().toList());
        return ChatClientFactory.geminiClient(properties, ftsAiChatMemory, toolBeans);
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
}
