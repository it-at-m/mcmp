package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.storage.StorageCategory;
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
@Table(name = "ontap_volume", uniqueConstraints = {@UniqueConstraint(columnNames = {"ontap_cluster_id", "volume_uuid"})})
public class OntapVolume extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "svm_id", nullable = false)
    private OntapSvm svm;

    @Column(name = "svm_id", insertable = false, updatable = false)
    private Long svmId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ontap_cluster_id", nullable = false)
    private ConfigOntapCluster cluster;

    @Column(name = "ontap_cluster_id", insertable = false, updatable = false)
    private Long clusterId;

    @NotNull
    @Column(name = "volume_uuid", nullable = false)
    private UUID volumeUuid;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "size")
    private Long size;

    @Column(name = "state")
    private String state;

    @Column(name = "type")
    private String type;

    @Column(name = "style")
    private String style;

    @Column(name = "snapshot_policy")
    private String snapshotPolicy;

    @Column(name = "nas_path")
    private String nasPath;

    @Column(name = "mount_path_nfs")
    private String mountPathNfs;

    @ColumnDefault("false")
    @Column(name = "is_flex_clone")
    private Boolean isFlexClone;

    @ColumnDefault("false")
    @Column(name = "is_split_initiated")
    private Boolean isSplitInitiated;

    @Column(name = "space_available_percent")
    private Integer spaceAvailablePercent;

    @Column(name = "space_afs_total")
    private Long spaceAfsTotal;

    @Column(name = "space_logical_used")
    private Long spaceLogicalUsed;

    @Column(name = "space_logical_available")
    private Long spaceLogicalAvailable;

    @Column(name = "space_logical_used_percent")
    private Integer spaceLogicalUsedPercent;

    @Column(name = "space_logical_used_by_afs")
    private Long spaceLogicalUsedByAfs;

    @Column(name = "space_snapshot_reserve_percent")
    private Integer spaceSnapshotReservePercent;

    @Column(name = "space_snapshot_reserve_size")
    private Long spaceSnapshotReserveSize;

    @Column(name = "space_snapshot_used")
    private Long spaceSnapshotUsed;

    @Column(name = "snaplock_append_mode_enabled")
    private Boolean snaplockAppendModeEnabled;

    @Column(name = "snaplock_autocommit_period")
    private String snaplockAutocommitPeriod;

    @Column(name = "snaplock_retention_default")
    private String snaplockRetentionDefault;

    @Column(name = "snaplock_retention_minimum")
    private String snaplockRetentionMinimum;

    @Column(name = "snaplock_retention_maximum")
    private String snaplockRetentionMaximum;

    @Column(name = "snaplock_type")
    private String snaplockType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_category")
    private StorageCategory storageCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "export_policy_id")
    private OntapExportPolicy exportPolicy;

    @ManyToMany
    @JoinTable(name = "ontap_volume_has_appservices", joinColumns = {@JoinColumn(name = "ontap_volume_id")}, inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @OneToMany(mappedBy = "volume")
    private Set<OntapCifsShare> ontapCifsShares = new LinkedHashSet<>();

    @OneToMany(mappedBy = "volume")
    private Set<OntapQtree> ontapQtrees = new LinkedHashSet<>();

    @OneToMany(mappedBy = "ontapVolume")
    private Set<OntapVolumeServerMount> ontapVolumeServerMounts = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "parent_svm_id")
    private OntapSvm parentSvm;

    @Column(name = "parent_svm_id", insertable = false, updatable = false)
    private Long parentSvmId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "parent_volume_id")
    private OntapVolume parentVolume;

    @Column(name = "parent_volume_id", insertable = false, updatable = false)
    private Long parentVolumeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "parent_snapshot_id")
    private OntapSnapshot parentSnapshot;

    @Column(name = "parent_snapshot_id", insertable = false, updatable = false)
    private Long parentSnapshotId;

    @ManyToMany(mappedBy = "ontapVolumes")
    private Set<OntapAggregate> ontapAggregates = new LinkedHashSet<>();

    @OneToMany(mappedBy = "parentVolume", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OntapVolume> cloneVolumes = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "ontap_snapshot_has_volumes", joinColumns = {@JoinColumn(name = "ontap_volume_id")}, inverseJoinColumns = {@JoinColumn(name = "ontap_snapshot_id")})
    private Set<OntapSnapshot> ontapSnapshots = new LinkedHashSet<>();

}