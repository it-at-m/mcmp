package de.muenchen.mcmp.portgroup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "port_group")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PortGroup extends AbstractEntity {
    @Column(name = "cloud_id", nullable = false)
    private Long cloudId;

    @Column(name = "port_group_key", length = 100, nullable = false)
    private String portGroupKey;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "vlan", length = 200)
    private String vlan;
}
