package de.muenchen.mcmp.repository;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "repository")
public class Repository extends AbstractEntity {


    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "snow_name", length = Integer.MAX_VALUE)
    private String snowName;

    @Column(name = "snow_sys_id", length = Integer.MAX_VALUE)
    private String snowSysId;

    @Column(name = "snow_sys_class", length = Integer.MAX_VALUE)
    private String snowSysClass;

    @Column(name = "snow_last_discovered")
    private OffsetDateTime snowLastDiscovered;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_status", nullable = false)
    private RepositoryLockStatus lockStatus = RepositoryLockStatus.LOCKED;

    public boolean isLocked() {
        return lockStatus == RepositoryLockStatus.LOCKED;
    }

    @Column(name = "repository_url")
    private String repositoryUrl;

    @ManyToMany
    @JoinTable(
            name = "repository_assignment",
            joinColumns = @JoinColumn(name = "repository_id"),
            inverseJoinColumns = @JoinColumn(name = "server_id")
    )
    private Set<Server> servers = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "repository_has_appservices",
            joinColumns = @JoinColumn(name = "repository_id"),
            inverseJoinColumns = @JoinColumn(name = "appservice_id")
    )
    private Set<Appservice> appservices = new LinkedHashSet<>();
}