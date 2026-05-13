package de.muenchen.mcmp.storage;

import de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO;
import de.muenchen.mcmp.ontap.OntapCifsShareAclListDto;
import de.muenchen.mcmp.ontap.OntapExportPolicyListDto;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UnifiedStorageItemDto {
    private String uuid;
    private String name;
    private StorageType type;
    private Long size;
    private Long used;
    private String protocol;
    private List<AppserviceNameAndSysIdDTO> appservices;

    // NFS Specifics
    private String nfs_mount_path;
    private OntapExportPolicyListDto nfs_export_policy;
    private String nfs_security_style;

    // CIFS Specifics
    private String cifs_share_name;
    private String cifs_mount_path;
    private List<OntapCifsShareAclListDto> cifs_share_acl_list;

    // S3 Specifics
    private String s3_endpoint; // Placeholder, might not be needed or available
    private String s3_bucket_name;
    private Long s3_object_count;

    // Common/Volume Specifics
    private Boolean mirrorEnabled;
    private String diskClass;

    // FlexClone specific fields
    private Boolean isFlexClone;
    private String parentVolumeName;
    private UUID parentVolumeUUID;
    private StorageType parentVolumeType;
    private String parentSnapshotName;

    // Worm specific fields
    private Boolean isWorm;
    private String minRetention;
    private String maxRetention;
    private String defaultRetention;
    private String autocommitPeriod;
    private Boolean appendMode;

    private Integer spaceAvailablePercent;
    private Long spaceAfsTotal;
    private Long spaceLogicalUsed; // Already partially used as 'used'
    private Long spaceLogicalAvailable;
    private Integer spaceLogicalUsedPercent;
    private Long spaceLogicalUsedByAfs;
    private Integer spaceSnapshotReservePercent;
    private Long spaceSnapshotReserveSize;
    private Long spaceSnapshotUsed;
    private String snapshotPolicy;
}
