package de.muenchen.mcmp.storagegrid;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "storagegrid_accounts")
public class StorageGridAccount extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "config_storagegrid_id", nullable = false)
    private ConfigStorageGrid configStoragegrid;

    @Column(name = "config_storagegrid_id", insertable = false, updatable = false)
    private Long configStorageGridId;

    @NotNull
    @Column(name = "account_id", nullable = false)
    private String accountId;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @ColumnDefault("false")
    @Column(name = "use_account_identity_source")
    private Boolean useAccountIdentitySource;

    @ColumnDefault("false")
    @Column(name = "allow_platform_services")
    private Boolean allowPlatformServices;

    @ColumnDefault("false")
    @Column(name = "allow_select_object_content")
    private Boolean allowSelectObjectContent;

    @ColumnDefault("false")
    @Column(name = "allow_compliance_mode")
    private Boolean allowComplianceMode;

    @Column(name = "max_retention_days")
    private Long maxRetentionDays;

    @Column(name = "max_retention_years")
    private Long maxRetentionYears;

    @Column(name = "quota_object_bytes")
    private Long quotaObjectBytes;

    @Column(name = "data_bytes")
    private Long dataBytes;

    @Column(name = "object_count")
    private Long objectCount;

    @Column(name = "calculation_time")
    private String calculationTime;

    @ManyToMany
    @JoinTable(name = "storagegrid_accounts_has_appservices", joinColumns = {@JoinColumn(name = "storagegrid_accounts_id")}, inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @Column(name = "snow_name", length = Integer.MAX_VALUE)
    private String snowName;

    @Column(name = "snow_sys_id", length = Integer.MAX_VALUE)
    private String snowSysId;

    @Column(name = "snow_sys_class", length = Integer.MAX_VALUE)
    private String snowSysClass;
}