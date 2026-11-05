package com.fts.aichat.api.dto;

public record PublicWidgetConfigResponse(
        String siteKey,
        String title,
        String subtitle,
        String welcome,
        String placeholder,
        String launcherLabel,
        String primaryColor
) {
}
