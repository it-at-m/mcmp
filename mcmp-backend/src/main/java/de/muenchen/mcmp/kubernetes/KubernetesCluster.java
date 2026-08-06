package de.muenchen.mcmp.kubernetes;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.types.EnvironmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "kubernetes_cluster")
public class KubernetesCluster extends AbstractEntity {

    @Column(name = "name", columnDefinition = "text")
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

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<KubernetesNamespace> namespaces = new LinkedHashSet<>();

    @Column(name = "web_console_url", length = Integer.MAX_VALUE)
    private String webConsoleUrl;
}
