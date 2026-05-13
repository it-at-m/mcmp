package de.muenchen.mcmp.infoblox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "infoblox")
@Data
public class InfobloxProperties {

    /**
     * If true, the Infoblox query is skipped and a hostname is returned directly.
     * Default: false
     */
    private boolean skipSearch = false;
}