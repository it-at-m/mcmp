package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ontap_snapshot")
public class OntapSnapshot extends AbstractEntity {

    @NotNull
    @Column(name = "snapshot_uuid", nullable = false)
    private UUID snapshotUuid;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "create_time")
    private OffsetDateTime createTime;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ontap_cluster_id", nullable = false)
    private ConfigOntapCluster ontapCluster;

    @Column(name = "ontap_cluster_id", insertable = false, updatable = false)
    private Long ontapClusterId;
}