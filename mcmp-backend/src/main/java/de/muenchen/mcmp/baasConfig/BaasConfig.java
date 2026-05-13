package de.muenchen.mcmp.baasConfig;

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
@Table(name = "config_baas")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BaasConfig extends AbstractEntity {
    @Size(max = 100)
    @Column(name = "api_description", length = 100)
    private String apiDescription;

    @NotNull
    @Size(max = 500)
    @Column(name = "api_endpoint", length = 500, nullable = false)
    private String apiEndpoint;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
