package de.muenchen.mcmp.ip;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.nic.Nic;
import de.muenchen.mcmp.types.IPType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ip")
public class IP extends AbstractEntity {

    @Size(max = 45)
    @NotNull
    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "ip_type", nullable = false, columnDefinition = "ip_type")
    private IPType ipType;

    @Size(max = 100)
    @Column(name = "dns_name", length = 100)
    private String dnsName;

    @Size(max = 17)
    @Column(name = "dns_mac", length = 17)
    private String dnsMac;

    @ManyToMany(fetch = FetchType.LAZY, mappedBy = "ips")
    private Set<Nic> nics = new HashSet<>();
}
