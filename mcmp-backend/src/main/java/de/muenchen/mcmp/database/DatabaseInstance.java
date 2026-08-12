package de.muenchen.mcmp.database;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "database_instance")
public class DatabaseInstance extends AbstractEntity {

    @Column(name = "snow_name", length = Integer.MAX_VALUE)
    private String snowName;

    @NotNull
    @Column(name = "snow_sys_id", nullable = false, length = Integer.MAX_VALUE)
    private String snowSysId;

    @Column(name = "snow_sys_class", length = Integer.MAX_VALUE)
    private String snowSysClass;

    @Column(name = "snow_last_discovered")
    private OffsetDateTime snowLastDiscovered;

    @Column(name = "snow_version", length = Integer.MAX_VALUE)
    private String snowVersion;

    @NonNull
    @ManyToMany
    @JoinTable(
            name = "database_instance_has_appservices",
            joinColumns = {@JoinColumn(name = "database_instance_id")},
            inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @NonNull
    @ManyToMany
    @JoinTable(
            name = "database_instance_has_database_pdb_instances",
            joinColumns = {@JoinColumn(name = "database_instance_id")},
            inverseJoinColumns = {@JoinColumn(name = "database_pdb_instance_id")})
    private Set<DatabasePdbInstance> databasePdbInstances = new LinkedHashSet<>();

    @NonNull
    @ManyToMany
    @JoinTable(
            name = "server_has_database_instances",
            joinColumns = {@JoinColumn(name = "database_instance_id")},
            inverseJoinColumns = {@JoinColumn(name = "server_id")})
    private Set<Server> servers = new LinkedHashSet<>();


}