package de.muenchen.mcmp.nic;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.portgroup.PortGroup;
import de.muenchen.mcmp.ip.IP;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "nic")
public class Nic extends AbstractEntity {
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_group_id")
    private PortGroup portGroup;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "ip_assignment",
               joinColumns = { @JoinColumn(name = "nic_id") },
               inverseJoinColumns = { @JoinColumn(name = "ip_id") })
    private Set<IP> ips = new HashSet<>();

    @Column(name = "vnic_key", nullable = false)
    private Integer vnicKey;

    @Column(name = "unit_number")
    private Integer unitNumber;

    @Column(name = "device", length = 200)
    private String device;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    @Column(name = "network", length = 200)
    private String network;

    @Column(name = "connected", nullable = false)
    private Boolean connected;

    @Column(name = "port_group_summary", length = 200)
    private String portGroupSummary;

    @Column(name = "port_group_key", length = 200)
    private String portGroupKey;

    @Column(name = "distributed_port_key", length = 200)
    private String distributedPortKey;

    @Column(name = "address_type", length = 20)
    private String addressType;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "tools_ip_address", length = 300)
    private String toolsIpAddress;

    @Column(name = "tools_network_name", length = 200)
    private String toolsNetworkName;

    @Column(name = "tools_connected", nullable = false)
    private Boolean toolsConnected;

    public void assignIp(IP ip) {
        ips.add(ip);
        ip.getNics().add(this);
    }

    public void unassignIp(IP ip) {
        ips.remove(ip);
        ip.getNics().remove(this);
    }
}
