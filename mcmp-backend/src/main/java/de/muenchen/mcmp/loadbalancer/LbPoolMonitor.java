package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A single health monitor, owned by either a pool (default monitor) or a pool member
 * (member-level monitor override) - exactly one of the two FKs is set.
 */
@Entity
@Table(name = "lb_pool_monitor", schema = "cmp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"pool", "poolMember"})
public class LbPoolMonitor extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lb_pool_id")
    private LbPool pool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lb_pool_member_id")
    private LbPoolMember poolMember;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "interval_seconds")
    private Integer interval;

    @Column(name = "port")
    private String port;

    @Column(name = "method")
    private String method;

    @Column(name = "path")
    private String path;

    @Column(name = "host")
    private String host;

    @Column(name = "http_version")
    private String httpVersion;

    @Column(name = "expect")
    private String expect;
}
