package de.muenchen.mcmp.foreman;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.NonNull;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "config_foreman")
public class ConfigForeman {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Size(max = 100)
    @Column(name = "api_description", length = 100)
    private String apiDescription;

    @Size(max = 100)
    @NotNull
    @Column(name = "api_username", nullable = false, length = 100)
    private String apiUsername;

    @NotNull
    @Column(name = "api_password_encrypted", nullable = false)
    private byte[] apiPasswordEncrypted;

    @Size(max = 500)
    @NotNull
    @Column(name = "api_endpoint", nullable = false, length = 500)
    private String apiEndpoint;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @NonNull
    @OneToMany
    @JoinColumn(name = "config_foreman_id")
    private Set<ForemanHost> foremanHosts = new LinkedHashSet<>();

    @NotNull
    @Column(name = "fqdn", nullable = false, length = Integer.MAX_VALUE)
    private String fqdn;


}