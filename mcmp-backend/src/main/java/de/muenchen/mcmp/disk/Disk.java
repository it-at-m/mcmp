package de.muenchen.mcmp.disk;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "disk")
public class Disk extends AbstractEntity {

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "vdisk_key", nullable = false)
    private Integer vdiskKey;

    @Column(name = "unit_number")
    private Integer unitNumber;

    @Column(name = "disk_provisioning", length = 50)
    private String diskProvisioning;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "capacity_in_bytes")
    private Long capacityInBytes;

    @Column(name = "vdisk_id", length = 100)
    private String vdiskId;

    @Column(name = "device", length = 200)
    private String device;

    @Column(name = "virtual_disk_format", length = 30)
    private String virtualDiskFormat;

    @Column(name = "disk_mode", length = 30)
    private String diskMode;

}