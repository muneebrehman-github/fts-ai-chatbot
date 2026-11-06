package com.fts.aichat.api.service;

import com.fts.aichat.api.config.ChatApiProperties;
import com.fts.aichat.api.config.ChatApiProperties.SiteConfig;
import com.fts.aichat.api.config.SiteRegistry;
import com.fts.aichat.api.dto.PublicChatResponse;
import com.fts.aichat.api.dto.PublicWidgetConfigResponse;
import com.fts.aichat.api.ratelimit.SlidingWindowRateLimiter;
import com.fts.aichat.core.ChatResult;
import com.fts.aichat.core.FallbackChatService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicChatService {

    private final SiteRegistry siteRegistry;
    private final FallbackChatService fallbackChatService;
    private final ChatApiProperties properties;
    private final SlidingWindowRateLimiter rateLimiter;

    public PublicChatService(
            SiteRegistry siteRegistry,
            FallbackChatService fallbackChatService,
            ChatApiProperties properties,
            SlidingWindowRateLimiter rateLimiter) {
        this.siteRegistry = siteRegistry;
        this.fallbackChatService = fallbackChatService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public PublicWidgetConfigResponse widgetConfig(String siteKey, String origin) {
        SiteConfig site = requireSite(siteKey, origin);
        return toWidgetConfig(site);
    }

    public PublicChatResponse chat(
            String siteKey,
            String message,
            String conversationId,
            String origin,
            String clientIp) {
        SiteConfig site = requireSite(siteKey, origin);
        enforceRateLimits(site, clientIp);

        String publicConversationId = StringUtils.hasText(conversationId)
                ? conversationId.trim()
                : UUID.randomUUID().toString();
        String scopedConversationId = site.getSiteKey() + ":" + publicConversationId;

        String systemPrompt = StringUtils.hasText(site.getSystemPrompt())
                ? site.getSystemPrompt()
                : properties.getSystemPrompt();

        ChatResult result = fallbackChatService.chat(message, scopedConversationId, systemPrompt);

        return new PublicChatResponse(
                result.reply(),
                publicConversationId,
                result.provider(),
                result.fallbackUsed());
    }

    private PublicWidgetConfigResponse toWidgetConfig(SiteConfig site) {
        return new PublicWidgetConfigResponse(
                site.getSiteKey(),
                site.getTitle(),
                site.getSubtitle(),
                site.getWelcome(),
                site.getPlaceholder(),
                site.getLauncherLabel(),
                site.getPrimaryColor());
    }

    private SiteConfig requireSite(String siteKey, String origin) {
        SiteConfig site = siteRegistry.find(siteKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid siteKey"));
        Instant now = Instant.now();
        if (!site.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site is disabled");
        }
        if (site.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site key expired");
        }
        if (!siteRegistry.isOriginAllowed(site, origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Origin not allowed for this siteKey");
        }
        return site;
    }

    private void enforceRateLimits(SiteConfig site, String clientIp) {
        String siteBucket = "site:" + site.getSiteKey();
        if (!rateLimiter.tryAcquire(siteBucket, site.getRateLimitPerMinute())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Site rate limit exceeded");
        }
        String ip = StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
        String ipBucket = "ip:" + site.getSiteKey() + ":" + ip;
        if (!rateLimiter.tryAcquire(ipBucket, site.getRateLimitPerIpPerMinute())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "IP rate limit exceeded");
        }
    }
}
