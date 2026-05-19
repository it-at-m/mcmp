package de.muenchen.mcmp.appservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.group.Group;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.types.EnvironmentType;
import de.muenchen.mcmp.user.User;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "appservice")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@DynamicUpdate
public class Appservice extends AbstractEntity {

    @Column(name = "name", columnDefinition = "text")
    private String name;

    @Column(name = "number", nullable = false, unique = true, columnDefinition = "text")
    private String number;

    @Column(name = "sys_id", nullable = false, unique = true, columnDefinition = "text")
    private String sysId;

    @Column(name = "used_for", nullable = false, columnDefinition = "text")
    private String usedFor;

    @Column(name = "environment", nullable = false)
    @ColumnTransformer(write = "?::environment_type")
    private EnvironmentType environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "owned_by_id")
    @ToString.Exclude
    private User ownedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "service_owner_delegate_id")
    @ToString.Exclude
    private User serviceOwnerDelegate;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "change_group_id")
    @ToString.Exclude
    private Group changeGroup;

    @ManyToMany
    @JoinTable(name = "server_assignment",
            joinColumns = @JoinColumn(name = "appservice_id"),
            inverseJoinColumns = @JoinColumn(name = "server_id"))
    @ToString.Exclude
    private Set<Server> servers = new LinkedHashSet<>();

    @NotNull
    @ColumnDefault("false")
    @Column(name = "csw_enforced", nullable = false)
    private Boolean cswEnforced = false;

    @Column(name = "business_service_numbers", columnDefinition = "text")
    private String businessServiceNumbers;

    /**
     * Determines the current environment for an application service.
     * If the environment is set to 'P' (Production) and the service is used for 'Training',
     * it returns the environment as 'S' (training). Otherwise, it returns the existing environment value.
     *
     * @return the current environment of the application service, either as the configured environment or adjusted
     *         based on its usage (e.g., changed from Production to Staging for Training purposes).
     */
    public String getCurrentEnvironment() {
        if (EnvironmentType.P.equals(this.environment) && "Training".equalsIgnoreCase(this.usedFor)) {
            return "s";
        }
        return this.environment.toString().toLowerCase();
    }
}