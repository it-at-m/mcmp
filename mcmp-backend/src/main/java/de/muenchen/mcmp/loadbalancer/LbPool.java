package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "monitors", columnDefinition = "jsonb")
    private List<LbMonitor> monitors;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LbPoolMember> members;
}
