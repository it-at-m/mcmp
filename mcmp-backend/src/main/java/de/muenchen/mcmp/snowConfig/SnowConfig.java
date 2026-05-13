package de.muenchen.mcmp.snowConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "config_snow")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SnowConfig extends AbstractEntity {
    @Size(max = 100)
    @Column(name = "api_description", length = 100)
    private String apiDescription;

    @NotNull
    @Size(max = 100)
    @Column(name = "api_client_auth_url", length = 100, nullable = false)
    private String apiClientAuthUrl;

    @NotNull
    @Size(max = 100)
    @Column(name = "api_client_id", length = 100, nullable = false)
    private String apiClientId;

    @NotNull
    @Column(name = "api_client_secret_encrypted", nullable = false)
    private byte[] apiClientSecretEncrypted;

    @NotNull
    @Size(max = 500)
    @Column(name = "api_endpoint", length = 500, nullable = false)
    private String apiEndpoint;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    
    @Size(max = 500)
    @Column(name = "proxy", length = 500, nullable = false)
    private String proxy;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "use_proxy", nullable = false)
    private boolean useProxy;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
