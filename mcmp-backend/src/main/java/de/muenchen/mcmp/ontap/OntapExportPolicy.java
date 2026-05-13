package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "ontap_export_policy")
public class OntapExportPolicy extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ontap_cluster_id", nullable = false)
    private ConfigOntapCluster cluster;

    @Column(name = "ontap_cluster_id", insertable = false, updatable = false)
    private Long clusterId;

    @NotNull
    @Column(name = "export_policy_id", nullable = false)
    private Long exportPolicyId;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "policy")
    private Set<OntapExportPolicyRule> ontapExportPolicyRules = new LinkedHashSet<>();
}