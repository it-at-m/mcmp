package de.muenchen.mcmp.storagegrid;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.storage.StorageCategory;
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
@Table(name = "storagegrid_buckets")
public class StorageGridBucket extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "storagegrid_account_id", nullable = false)
    private StorageGridAccount storageGridAccount;

    @Column(name = "storagegrid_account_id", insertable = false, updatable = false)
    private Long storageGridAccountId;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "object_count")
    private Long objectCount;

    @Column(name = "data_bytes")
    private Long dataBytes;

    @Column(name = "quota_object_bytes")
    private Long quotaObjectBytes;

    @Column(name = "region")
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_category")
    private StorageCategory storageCategory;

    @Column(name = "snow_name", length = Integer.MAX_VALUE)
    private String snowName;

    @Column(name = "snow_sys_id", length = Integer.MAX_VALUE)
    private String snowSysId;

    @Column(name = "snow_sys_class", length = Integer.MAX_VALUE)
    private String snowSysClass;

    @ManyToMany
    @JoinTable(name = "storagegrid_buckets_has_appservices",
            joinColumns = {@JoinColumn(name = "storagegrid_bucket_id")},
            inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

}