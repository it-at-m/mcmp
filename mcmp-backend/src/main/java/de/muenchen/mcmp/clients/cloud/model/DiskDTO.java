package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.disk.Disk;
import de.muenchen.mcmp.server.Server;

import java.util.Objects;

public record DiskDTO(
        @JsonProperty("vdisk_key") Integer vdiskKey,
        @JsonProperty("unit_number") Integer unitNumber,
        @JsonProperty("disk_provisioning") String diskProvisioning,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("capacity_in_bytes") Long capacityInBytes,
        @JsonProperty("vdisk_id") String vdiskID,
        @JsonProperty("device") String device,
        @JsonProperty("virtual_disk_format") String virtualDiskFormat,
        @JsonProperty("disk_mode") String diskMode
) {
    public DiskDTO {
        Objects.requireNonNull(vdiskKey);
    }

    /**
     * Determines if this DTO contains changes that warrant writing
     * to the database, based on an existing disk entity.
     *
     * @param existing The disk entity to compare.
     * @return True if changes should be written.
     */
    public boolean hasChanges(final Disk existing) {
        return !Objects.equals(existing.getUnitNumber(), unitNumber)
                || !Objects.equals(existing.getDiskProvisioning(), diskProvisioning)
                || !Objects.equals(existing.getFileName(), fileName)
                || !Objects.equals(existing.getCapacityInBytes(), capacityInBytes)
                || !Objects.equals(existing.getVdiskId(), vdiskID)
                || !Objects.equals(existing.getDevice(), device)
                || !Objects.equals(existing.getVirtualDiskFormat(), virtualDiskFormat)
                || !Objects.equals(existing.getDiskMode(), diskMode);
    }

    /**
     * Applies the DTO's data to an existing disk entity.
     *
     * @param existing The entity to be modified.
     */
    public void applyChanges(Disk existing) {
        existing.setUnitNumber(unitNumber);
        existing.setDiskProvisioning(diskProvisioning);
        existing.setFileName(fileName);
        existing.setCapacityInBytes(capacityInBytes);
        existing.setDevice(device);
        existing.setVirtualDiskFormat(virtualDiskFormat);
        existing.setDiskMode(diskMode);
    }

    /**
     * Builds a new disk entity which can be persisted to the
     * Database.
     *
     * @param server The Server entity the disk belongs to.
     * @return A new disk entity.
     */
    public Disk build(Server server) {
        var disk = new Disk();
        disk.setVdiskKey(vdiskKey);
        disk.setServerId(server.getId());
        applyChanges(disk);
        return disk;
    }
}
