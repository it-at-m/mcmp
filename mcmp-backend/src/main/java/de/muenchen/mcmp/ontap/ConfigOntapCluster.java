package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "config_ontap_cluster")
public class ConfigOntapCluster extends AbstractEntity {

    @Column(name = "api_description")
    private String apiDescription;

    @Column(name = "api_username")
    private String apiUsername;

    @Column(name = "api_password_encrypted")
    private byte[] apiPasswordEncrypted;

    @NotNull
    @Column(name = "api_endpoint", nullable = false)
    private String apiEndpoint;

    @ColumnDefault("false")
    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "datacenter")
    private String datacenter;
}