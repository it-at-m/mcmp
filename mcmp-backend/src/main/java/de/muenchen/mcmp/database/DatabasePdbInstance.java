package de.muenchen.mcmp.database;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "database_pdb_instance")
public class DatabasePdbInstance extends AbstractEntity {

    @Column(name = "snow_name", length = Integer.MAX_VALUE)
    private String snowName;

    @NotNull
    @Column(name = "snow_sys_id", nullable = false, length = Integer.MAX_VALUE)
    private String snowSysId;

    @Column(name = "snow_sys_class", length = Integer.MAX_VALUE)
    private String snowSysClass;

    @Column(name = "snow_last_discovered")
    private OffsetDateTime snowLastDiscovered;

    @Column(name = "snow_pdb", length = Integer.MAX_VALUE)
    private String snowPdb;

    @NonNull
    @ManyToMany
    @JoinTable(
            name = "database_pdb_instance_has_appservices",
            joinColumns = {@JoinColumn(name = "database_pdb_instance_id")},
            inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @NonNull
    @ManyToMany(mappedBy = "databasePdbInstances")
    private Set<DatabaseInstance> databaseInstances = new LinkedHashSet<>();

    @Column(name = "pdb_name", length = Integer.MAX_VALUE)
    private String pdbName;

    @Column(name = "pdb_host_name", length = Integer.MAX_VALUE)
    private String pdbHostName;

    @Column(name = "pdb_characterset", length = Integer.MAX_VALUE)
    private String pdbCharacterset;

    @Column(name = "pdb_startup_time")
    private OffsetDateTime pdbStartupTime;

    @Column(name = "pdb_database_type", length = Integer.MAX_VALUE)
    private String pdbDatabaseType;

    @Column(name = "pdb_collected_at")
    private OffsetDateTime pdbCollectedAt;
}