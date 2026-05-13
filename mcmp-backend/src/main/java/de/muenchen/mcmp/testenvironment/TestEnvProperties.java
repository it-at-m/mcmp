package de.muenchen.mcmp.testenvironment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "testenvironment")
@Data
public class TestEnvProperties {
    /**
     * If true, the test environment will be enabled and the application will not show unfinished features.
     * Default: false
     */
    private boolean enabled = false;
}
