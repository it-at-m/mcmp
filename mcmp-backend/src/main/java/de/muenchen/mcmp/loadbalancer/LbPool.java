package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(
        name = "lb_pool",
        schema = "cmp",
        uniqueConstraints = @UniqueConstraint(columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class LbPool extends AbstractEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "lb_method", nullable = false)
    private String lbMethod;

    @Column(name = "monitor_condition")
    private String monitorCondition;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LbPoolMonitor> monitors;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LbPoolMember> members;
}
