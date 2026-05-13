package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ontap_svm")
public class OntapSvm extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ontap_cluster_id", nullable = false)
    private ConfigOntapCluster cluster;

    @Column(name = "ontap_cluster_id", insertable = false, updatable = false)
    private Long clusterId;

    @NotNull
    @Column(name = "swm_uuid", nullable = false)
    private UUID swmUuid;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;
}