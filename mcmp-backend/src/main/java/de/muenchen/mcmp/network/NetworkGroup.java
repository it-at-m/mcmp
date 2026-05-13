package de.muenchen.mcmp.network;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.types.EnvironmentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "network_group")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NetworkGroup extends AbstractEntity {
    @Size(max = 100)
    @Column(name = "name", length = 100)
    private String name;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "application", nullable = false)
    private boolean application;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "database", nullable = false)
    private boolean database;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "storage", nullable = false)
    private boolean storage;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "restrict", nullable = false)
    private boolean restrict;

    @ManyToMany
    @JoinTable(
            name = "appservice_network_group_assignment",
            joinColumns = @JoinColumn(name = "network_group_id"),
            inverseJoinColumns = @JoinColumn(name = "appservice_id")
    )
    @OrderBy("name ASC")
    @JsonIgnoreProperties({"servers", "changeGroup", "serviceOwnerDelegate", "ownedBy"})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @Column(name = "environment", columnDefinition = "environment_type")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EnvironmentType environment;
}