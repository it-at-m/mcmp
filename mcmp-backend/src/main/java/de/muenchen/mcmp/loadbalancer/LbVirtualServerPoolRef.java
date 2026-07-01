package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Join entity linking a {@link LbVirtualServer} to the {@link LbPool}(s) it routes to.
 * Replaces the former lb_virtual_server.pool_refs JSONB map (keyed by pool name) with an
 * indexed FK relation; hosts/paths remain JSONB since they are plain string lists, not references.
 */
@Entity
@Table(
        name = "lb_virtual_server_pool_ref",
        schema = "cmp",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lb_virtual_server_id", "lb_pool_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"virtualServer"})
public class LbVirtualServerPoolRef extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lb_virtual_server_id", nullable = false)
    private LbVirtualServer virtualServer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lb_pool_id", nullable = false)
    private LbPool pool;

    @Column(name = "is_default")
    private Boolean isDefault;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hosts", columnDefinition = "jsonb")
    private List<String> hosts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paths", columnDefinition = "jsonb")
    private List<String> paths;
}
