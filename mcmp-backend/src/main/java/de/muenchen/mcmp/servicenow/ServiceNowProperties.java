package de.muenchen.mcmp.servicenow;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "servicenow")
@Data
public class ServiceNowProperties {

    /**
     * If true, the ServiceNow query is skipped.
     * Default: false
     */
    private boolean skipSearch = false;
}