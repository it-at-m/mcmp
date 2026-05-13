package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "ontap_qtree_server_mount")
public class OntapQtreeServerMount extends AbstractEntity implements OntapServerMount {

    @Column(name = "mount_point", length = Integer.MAX_VALUE)
    private String mountPoint;

    @Column(name = "filesystem", length = Integer.MAX_VALUE)
    private String filesystem;

    @Column(name = "options")
    private List<String> options;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ontap_qtree_id", nullable = false)
    private OntapQtree ontapQtree;

    @Column(name = "ontap_qtree_id", insertable = false, updatable = false)
    private Long ontapQtreeId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(name = "server_id", insertable = false, updatable = false)
    private Long serverId;

    @Override
    public AbstractEntity getOntapEntity() {
        return ontapQtree;
    }
}