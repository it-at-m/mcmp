package de.muenchen.mcmp.storage;

import de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
// Only NFS volumes, NFS Qtrees and CIFS shares are mountable
public class UnifiedStorageMountItemDto {

    private String mountPoint;
    private String filesystem;
    private List<String> options;
    private Long serverId;

    private String uuid;
    private String name;
    private StorageType type;
    private Long size;
    private Long used;
    private String protocol;
    private List<AppserviceNameAndSysIdDTO> appservices;

    private String nfs_mount_path;
    private String cifs_share_name;
    private String cifs_mount_path;
}
