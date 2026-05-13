package de.muenchen.mcmp.mountPoint;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "mount_point")
public class MountPoint extends AbstractEntity {
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "disk_path", length = 255, nullable = false)
    private String diskPath;

    @Column(name = "capacity_in_bytes")
    private Long capacityInBytes;

    @Column(name = "free_space_in_bytes")
    private Long freeSpaceInBytes;

    @Column(name = "filesystem_type", length = 20)
    private String filesystemType;

    @Column(name = "source", length = 10)
    private String source;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "hidden", nullable = false)
    private Boolean hidden = false;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "editable", nullable = false)
    private Boolean editable = false;

    @Size(max = 50)
    @Column(name = "foreman_uuid", length = 50)
    private String foremanUuid;

    @Column(name = "foreman_capacity_in_bytes")
    private Long foremanCapacityInBytes;

    @Size(max = 255)
    @Column(name = "foreman_partition")
    private String foremanPartition;

    @Size(max = 50)
    @Column(name = "foreman_parttype", length = 50)
    private String foremanParttype;

    @Size(max = 50)
    @Column(name = "foreman_partuuid", length = 50)
    private String foremanPartuuid;

}

