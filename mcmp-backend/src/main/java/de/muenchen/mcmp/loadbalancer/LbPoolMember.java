package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.List;

@Entity
@Table(name = "lb_pool_member", schema = "cmp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class LbPoolMember extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private LbPool pool;

    @Column(name = "ip", nullable = false)
    private String ip;

    @Column(name = "port", nullable = false)
    private int port;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private Server server;

    @Column(name = "monitor_condition")
    private String monitorCondition;

    @OneToMany(mappedBy = "poolMember", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<LbPoolMonitor> monitors;
}
