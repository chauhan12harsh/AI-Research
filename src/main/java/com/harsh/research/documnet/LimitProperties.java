package com.harsh.research.documnet;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.limits")
public record LimitProperties(
    long maxFileBytes,
    long workspaceBytes,
    int maxPdfPages
) {

}
