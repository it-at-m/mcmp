package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    @Column(name = "domains", columnDefinition = "jsonb")
    private List<String> domains;

    @OneToMany(mappedBy = "virtualServer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LbVirtualServerPoolRef> poolRefs = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "lb_virtual_server_has_appservices",
            schema = "cmp",
            joinColumns = {@JoinColumn(name = "lb_virtual_server_id")},
            inverseJoinColumns = {@JoinColumn(name = "appservice_id")}
    )
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "lb_virtual_server_has_irules",
            schema = "cmp",
            joinColumns = {@JoinColumn(name = "lb_virtual_server_id")},
            inverseJoinColumns = {@JoinColumn(name = "lb_irule_id")}
    )
    private Set<LbIrule> irules = new LinkedHashSet<>();

    @Column(name = "snow_name", length = Integer.MAX_VALUE)
    private String snowName;

    @Column(name = "snow_sys_id", length = Integer.MAX_VALUE)
    private String snowSysId;

    @Column(name = "snow_sys_class", length = Integer.MAX_VALUE)
    private String snowSysClass;

    @Column(name = "snow_last_discovered")
    private OffsetDateTime snowLastDiscovered;
}
