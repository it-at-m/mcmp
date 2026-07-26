package de.muenchen.mcmp.kubernetes;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.types.EnvironmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "kubernetes_namespace")
public class KubernetesNamespace extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cluster_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private KubernetesCluster cluster;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "sys_id", nullable = false, unique = true, columnDefinition = "text")
    private String sysId;

    @Column(name = "sys_class", columnDefinition = "text")
    private String sysClass;

    @Column(name = "last_discovered")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastDiscovered;

    @Column(name = "k8s_uid", unique = true, columnDefinition = "text")
    private String k8sUid;

    @Column(name = "environment")
    @ColumnTransformer(write = "?::environment_type")
    private EnvironmentType environment;

    @ManyToMany
    @JoinTable(name = "kubernetes_namespace_has_appservices",
            joinColumns = @JoinColumn(name = "kubernetes_namespace_id"),
            inverseJoinColumns = @JoinColumn(name = "appservice_id"))
    private Set<Appservice> appservices = new LinkedHashSet<>();
}