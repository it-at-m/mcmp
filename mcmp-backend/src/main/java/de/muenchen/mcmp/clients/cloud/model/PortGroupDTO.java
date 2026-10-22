package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.portgroup.PortGroup;
import de.muenchen.mcmp.cloud.Cloud;

import java.util.Objects;

public record PortGroupDTO(
        @JsonProperty("port_group_key") String portGroupKey,
        @JsonProperty("name") String name,
        @JsonProperty("vlan") String vlan
) {
    /**
     * Determines if this DTO contains changes that warrant writing
     * to the database, based on an existing PortGroup entity.
     *
     * @param existing The entity to compare.
     * @return True if changes should be written.
     */
    public boolean hasChanges(PortGroup existing) {
        return !Objects.equals(existing.getName(), name)
            || !Objects.equals(existing.getVlan(), vlan);
    }

    /**
     * Applies the DTO's data to an existing PortGroup entity.
     *
     * @param existing The entity to be modified.
     */
    public void applyChanges(PortGroup existing) {
        existing.setName(name);
        existing.setVlan(vlan);
    }

    /**
     * Builds a new PortGroup entity which can be persisted to
     * the database.
     *
     * @param server The Cloud entity the PortGroup belongs to.
     * @return A new PortGroup entity.
     */
    public PortGroup build(Cloud cloud) {
        var pg = new PortGroup();
        pg.setCloudId(cloud.getId());
        pg.setPortGroupKey(portGroupKey);
        applyChanges(pg);
        return pg;
    }
}
