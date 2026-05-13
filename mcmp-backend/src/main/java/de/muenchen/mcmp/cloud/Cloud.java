package de.muenchen.mcmp.cloud;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.types.CloudType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;


@Getter
@Setter
@Entity
@Table(name = "cloud")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Cloud extends AbstractEntity {

    @Size(max = 100)
    @Column(name = "name", length = 100)
    private String name;

    @Size(max = 100)
    @NotNull
    @Column(name = "fqdn", nullable = false, length = 100)
    private String fqdn;

    @Size(max = 100)
    @Column(name = "server_gui", length = 100)
    private String serverGui;

    @Enumerated(EnumType.STRING)
    @Column(name = "cloud_type")
    @ColumnTransformer(write = "?::cloud_type")
    private CloudType cloudType;

    @Size(max = 100)
    @Column(name = "api_description", length = 100)
    private String apiDescription;

    @Size(max = 100)
    @Column(name = "api_username", length = 100)
    private String apiUsername;

    @Column(name = "api_password_encrypted")
    private byte[] apiPasswordEncrypted;

    @Size(max = 500)
    @Column(name = "api_endpoint", length = 500)
    private String apiEndpoint;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "config_infoblox_id")
    private Long configInfobloxId;

    @Column(name = "config_baas_id")
    private Long configBaasId;

    @Size(max = 1)
    @Column(name = "vcenter_short_code", length = 1, columnDefinition = "citext")
    private String vcenterShortCode;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "green_it_enabled", nullable = false)
    private boolean greenItEnabled = false;
}