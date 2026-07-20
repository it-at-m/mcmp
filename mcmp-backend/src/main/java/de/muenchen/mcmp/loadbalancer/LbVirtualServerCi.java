package de.muenchen.mcmp.loadbalancer;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "lb_virtual_server_ci", schema = "cmp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"lbVirtualServer", "appservices"})
public class LbVirtualServerCi extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lb_virtual_server_id", nullable = false)
    private LbVirtualServer lbVirtualServer;

    @Column(name = "snow_name", nullable = false)
    private String snowName;

    @Column(name = "snow_sys_id", nullable = false, unique = true)
    private String snowSysId;

    @Column(name = "snow_sys_class", nullable = false)
    private String snowSysClass;

    @Column(name = "snow_last_discovered")
    private OffsetDateTime snowLastDiscovered;

    @ManyToMany
    @JoinTable(
            name = "lb_virtual_server_ci_has_appservices",
            schema = "cmp",
            joinColumns = @JoinColumn(name = "lb_virtual_server_ci_id"),
            inverseJoinColumns = @JoinColumn(name = "appservice_id")
    )
    private Set<Appservice> appservices = new LinkedHashSet<>();
}