package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.mountPoint.MountPoint;
import de.muenchen.mcmp.server.Server;

import java.util.Objects;

public record MountPointDTO(
        @JsonProperty("disk_path") String diskPath,
        @JsonProperty("capacity_in_bytes") Long capacityInBytes,
        @JsonProperty("free_space_in_bytes") Long freeSpaceInBytes,
        @JsonProperty("filesystem_type") String filesystemType,
        @JsonProperty("source") String source
) {
    public MountPointDTO {
        Objects.requireNonNull(diskPath);
    }

    /**
     * Determines if this DTO contains changes that warrant writing
     * to the database, based on an existing mountpoint entity.
     *
     * @param existing The mountpoint entity to compare.
     * @return True if changes should be written.
     */
    public boolean hasChanges(MountPoint existing) {
        return !Objects.equals(existing.getCapacityInBytes(), capacityInBytes)
                || Objects.equals(existing.getFreeSpaceInBytes(), freeSpaceInBytes)
                || Objects.equals(existing.getFilesystemType(), filesystemType)
                || Objects.equals(existing.getSource(), source);
    }

    /**
     * Applies the DTO's data to an existing mountpoint entity.
     *
     * @param existing The entity to be modified.
     */
    public void applyChanges(MountPoint existing) {
        existing.setCapacityInBytes(capacityInBytes);
        existing.setFreeSpaceInBytes(freeSpaceInBytes);
        existing.setFilesystemType(filesystemType);
        existing.setSource(source);
    }

    /**
     * Builds a new mounpoint entity which can be persisted to the
     * Database.
     *
     * @param server The Server entity the mountpoint belongs to.
     * @return A new mountpoint entity.
     */
    public MountPoint build(Server server) {
        var mountPoint = new MountPoint();
        mountPoint.setServerId(server.getId());
        mountPoint.setDiskPath(diskPath);
        applyChanges(mountPoint);
        return mountPoint;
    }
}
