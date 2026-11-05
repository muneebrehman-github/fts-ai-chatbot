package com.fts.aichat.api.config;

import com.fts.aichat.api.config.ChatApiProperties.SiteConfig;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SiteRegistry {

    private final ChatApiProperties properties;
    private Map<String, SiteConfig> bySiteKey = Map.of();

    public SiteRegistry(ChatApiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void index() {
        Map<String, SiteConfig> indexed = new LinkedHashMap<>();
        properties.getPublic().getSites().forEach((id, config) -> {
            String key = StringUtils.hasText(config.getSiteKey()) ? config.getSiteKey().trim() : id;
            config.setSiteKey(key);
            indexed.put(key, config);
        });
        this.bySiteKey = Map.copyOf(indexed);
    }

    public Optional<SiteConfig> find(String siteKey) {
        if (!StringUtils.hasText(siteKey)) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySiteKey.get(siteKey.trim()));
    }

    public SiteConfig require(String siteKey) {
        return find(siteKey).orElseThrow(() -> new IllegalArgumentException("Unknown siteKey"));
    }

    public Collection<SiteConfig> all() {
        return bySiteKey.values();
    }

    public Set<String> allAllowedOrigins() {
        return bySiteKey.values().stream()
                .flatMap(site -> site.getAllowedOrigins().stream())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    public boolean isOriginAllowed(SiteConfig site, String origin) {
        if (site.getAllowedOrigins() == null || site.getAllowedOrigins().isEmpty()) {
            // Empty allowlist = open (useful for local demos). Prefer explicit origins in production.
            return true;
        }
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        String normalized = origin.trim();
        return site.getAllowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(allowed -> allowed.equalsIgnoreCase(normalized)
                        || "*".equals(allowed)
                        || matchesWildcard(allowed, normalized));
    }

    private static boolean matchesWildcard(String pattern, String origin) {
        if (!pattern.contains("*")) {
            return false;
        }
        String regex = pattern
                .toLowerCase(Locale.ROOT)
                .replace(".", "\\.")
                .replace("*", ".*");
        return origin.toLowerCase(Locale.ROOT).matches(regex);
    }
}
