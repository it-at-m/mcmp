package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(
        name = "lb_virtual_server",
        schema = "cmp",
        uniqueConstraints = @UniqueConstraint(columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class LbVirtualServer extends AbstractEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "listen", nullable = false)
    private String listen;

    @Column(name = "forward", nullable = false)
    private String forward;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "persistence", nullable = false)
    private String persistence;

    @Column(name = "waf_enabled", nullable = false)
    private boolean wafEnabled;

    @Column(name = "waf_status")
    private String wafStatus;

    @Column(name = "redirect80", nullable = false)
    private boolean redirect80;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "addresses", columnDefinition = "jsonb")
    private List<String> addresses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "irules", columnDefinition = "jsonb")
    private Map<String, String> irules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pool_refs", columnDefinition = "jsonb")
    private Map<String, LbPoolRef> poolRefs;

    @ManyToMany
    @JoinTable(
            name = "lb_virtual_server_has_appservices",
            schema = "cmp",
            joinColumns = {@JoinColumn(name = "lb_virtual_server_id")},
            inverseJoinColumns = {@JoinColumn(name = "appservice_id")}
    )
    private Set<Appservice> appservices = new LinkedHashSet<>();
}
