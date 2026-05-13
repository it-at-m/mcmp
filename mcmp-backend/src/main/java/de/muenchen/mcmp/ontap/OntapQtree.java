package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.appservice.Appservice;
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
@Table(name = "ontap_qtree")
public class OntapQtree extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "volume_id", nullable = false)
    private OntapVolume volume;

    @Column(name = "volume_id", insertable = false, updatable = false)
    private Long volumeId;

    @Column(name = "qtree_id")
    private Long qtreeId;

    @Column(name = "name")
    private String name;

    @Column(name = "path")
    private String path;

    @Column(name = "mount_path_nfs")
    private String mountPathNfs;

    @Column(name = "security_style")
    private String securityStyle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_policy_id")
    private OntapExportPolicy exportPolicy;

    @Column(name = "export_policy_id", insertable = false, updatable = false)
    private Long exportPolicyId;

    @Column(name = "quota_index")
    private Long quotaIndex;

    @Column(name = "quota_type")
    private String quotaType;

    @Column(name = "quota_hard_limit")
    private Long quotaHardLimit;

    @Column(name = "quota_used_bytes")
    private Long quotaUsedBytes;

    @Column(name = "quota_used_percent")
    private Integer quotaUsedPercent;

    @ManyToMany
    @JoinTable(name = "ontap_qtree_has_appservices", joinColumns = {@JoinColumn(name = "ontap_qtree_id")}, inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @OneToMany(mappedBy = "ontapQtree")
    private Set<OntapQtreeServerMount> ontapQtreeServerMounts = new LinkedHashSet<>();

    @OneToMany(mappedBy = "qtree")
    private Set<OntapCifsShare> ontapCifsShares = new LinkedHashSet<>();
}