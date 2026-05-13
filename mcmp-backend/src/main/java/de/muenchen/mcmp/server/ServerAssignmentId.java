package de.muenchen.mcmp.server;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class ServerAssignmentId implements Serializable {
    private static final long serialVersionUID = -5109561308652313870L;
    @NotNull
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @NotNull
    @Column(name = "appservice_id", nullable = false)
    private Long appserviceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ServerAssignmentId entity = (ServerAssignmentId) o;
        return Objects.equals(this.appserviceId, entity.appserviceId) &&
               Objects.equals(this.serverId, entity.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appserviceId, serverId);
    }

}