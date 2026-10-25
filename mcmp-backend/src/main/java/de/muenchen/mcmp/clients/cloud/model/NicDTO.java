package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.nic.Nic;
import de.muenchen.mcmp.portgroup.PortGroup;
import de.muenchen.mcmp.server.Server;

import java.util.Objects;

public record NicDTO(
        @JsonProperty("vnic_key") Integer vNicKey,
        @JsonProperty("unit_number") Integer unitNumber,
        @JsonProperty("device") String device,
        @JsonProperty("mac_address") String macAddress,
        @JsonProperty("network") String network,
        @JsonProperty("connected") Boolean connected,
        @JsonProperty("port_group_summary") String portGroupSummary,
        @JsonProperty("port_group_key") String portGroupKey,
        @JsonProperty("distributed_port_group_key") String distributedPortGroupKey,
        @JsonProperty("address_type") String addressType,
        @JsonProperty("card_type") String cardType,
        @JsonProperty("tools_ip_address") String toolsIPAddress,
        @JsonProperty("tools_network_name") String toolsNetworkName,
        @JsonProperty("tools_connected") Boolean toolsConnected
) {
    public NicDTO {
        Objects.requireNonNull(vNicKey);
        if (connected == null) connected = false;
        if (toolsConnected == null) toolsConnected = false;
    }

    /**
     * Determines if this DTO contains changes that warrant writing
     * to the database, based on an existing nic entity.
     *
     * @param existing The nic entity to compare.
     * @param portGroup Optionally the portgroup entity the nic should be mapped to.
     * @return True if changes should be written.
     */
    public boolean hasChanges(Nic existing, PortGroup portGroup) {
        return !Objects.equals(existing.getUnitNumber(), unitNumber)
                || !Objects.equals(existing.getDevice(), device)
                || !Objects.equals(existing.getMacAddress(), macAddress)
                || !Objects.equals(existing.getNetwork(), network)
                || !Objects.equals(existing.getConnected(), connected)
                || !Objects.equals(existing.getPortGroup() == null
                                       ? null : existing.getPortGroup().getId(),
                                   portGroup == null
                                       ? null : portGroup.getId())
                || !Objects.equals(existing.getPortGroupSummary(), portGroupSummary)
                || !Objects.equals(existing.getPortGroupKey(), portGroupKey)
                || !Objects.equals(existing.getDistributedPortKey(), distributedPortGroupKey)
                || !Objects.equals(existing.getAddressType(), addressType)
                || !Objects.equals(existing.getCardType(), cardType)
                || !Objects.equals(existing.getToolsIpAddress(), toolsIPAddress)
                || !Objects.equals(existing.getToolsNetworkName(), toolsNetworkName)
                || !Objects.equals(existing.getToolsConnected(), toolsConnected);
    }

    /**
     * Applies the DTO's data to an existing nic entity.
     *
     * @param existing The entity to be modified.
     * @param portGroup Optionally the portgroup entity the nic belongs to.
     */
    public void applyChanges(Nic existing, PortGroup portGroup) {
        existing.setUnitNumber(unitNumber);
        existing.setDevice(device);
        existing.setMacAddress(macAddress);
        existing.setNetwork(network);
        existing.setConnected(connected);
        existing.setPortGroup(portGroup);
        existing.setPortGroupSummary(portGroupSummary);
        existing.setPortGroupKey(portGroupKey);
        existing.setDistributedPortKey(distributedPortGroupKey);
        existing.setAddressType(addressType);
        existing.setCardType(cardType);
        existing.setToolsIpAddress(toolsIPAddress);
        existing.setToolsNetworkName(toolsNetworkName);
        existing.setToolsConnected(toolsConnected);
    }

    /**
     * Builds a new nic entity which can be persisted to the database.
     *
     * @param server The server entity the nic belongs to.
     * @param portGroup Optionally the portgroup entity the nic belongs to.
     * @return A new snapshot entity.
     */
    public Nic build(Server server, PortGroup portGroup) {
        var nic = new Nic();
        nic.setVnicKey(vNicKey);
        nic.setServerId(server.getId());
        applyChanges(nic, portGroup);
        return nic;
    }
}
