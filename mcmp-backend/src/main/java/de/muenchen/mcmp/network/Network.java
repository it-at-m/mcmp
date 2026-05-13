package de.muenchen.mcmp.network;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.types.EnvironmentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "network")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Network extends AbstractEntity {
    @Size(max = 50)
    @Column(name = "broadcast", length = 50)
    private String broadcast;

    @Size(max = 50)
    @Column(name = "cidr", length = 50)
    private String cidr;

    @Column(name = "comment")
    private String comment;

    @Size(max = 50)
    @Column(name = "dns_primary", length = 50)
    private String dnsPrimary;

    @Size(max = 50)
    @Column(name = "dns_secondary", length = 50)
    private String dnsSecondary;

    @Column(name = "environment", nullable = false, columnDefinition = "environment_type")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EnvironmentType environment;

    @Size(max = 50)
    @Column(name = "gateway", length = 50)
    private String gateway;

    @Min(0)
    @Column(name = "infoblox_id")
    private Long infobloxId;

    @Size(max = 50)
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Size(max = 100)
    @Column(name = "name", length = 100)
    private String name;

    @Size(max = 50)
    @Column(name = "netmask", length = 50)
    private String netmask;

    @Min(0)
    @Column(name = "network_group_id")
    private Long networkGroupId;

    @Size(max = 100)
    @Column(name = "networktyp", length = 100)
    private String networktyp;

    @Size(max = 50)
    @Column(name = "referat", length = 50)
    private String referat;

    @Column(name = "vlan", length = 200)
    private String vlan;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "mcmp_status", nullable = false)
    private Boolean mcmpStatus = false;

    @Size(max = 25)
    @Column(name = "mcmp_network_typ", length = 25)
    private String mcmpNetworkTyp;

    @Size(max = 100)
    @Column(name = "mcmp_network_group", length = 100)
    private String mcmpNetworkGroup;

}
