package de.muenchen.mcmp.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "appservices")
public record AppservicesProperties(
        String linux,
        String windows
) {}
