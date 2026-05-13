package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ontap_aggregate")
public class OntapAggregate extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ontap_cluster_id", nullable = false)
    private ConfigOntapCluster ontapCluster;

    @NotNull
    @Column(name = "aggregate_uuid", nullable = false)
    private UUID aggregateUuid;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @NotNull
    @Column(name = "disk_class", nullable = false, length = Integer.MAX_VALUE)
    private String diskClass;

    @ManyToMany
    @JoinTable(name = "ontap_aggregate_has_volumes", joinColumns = {@JoinColumn(name = "aggregate_id")}, inverseJoinColumns = {@JoinColumn(name = "volume_id")})
    private Set<OntapVolume> ontapVolumes = new LinkedHashSet<>();

    @NotNull
    @ColumnDefault("false")
    @Column(name = "mirror_enabled", nullable = false)
    private Boolean mirrorEnabled = false;
}