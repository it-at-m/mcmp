package de.muenchen.mcmp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "infoblox")
public class InfobloxProperties {

    private Map<String, ServerConfig> servers;

    @Data
    public static class ServerConfig {
        private String baseUrl;
        private String username;
        private String password;
        private boolean enabled;
        private String request;
    }
}
