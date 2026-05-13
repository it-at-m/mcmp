package de.muenchen.mcmp.clients.netapp.ontap;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Root DTO for ONTAP cluster discovery data containing cluster metadata and SVM inventory.
 */
public record OntapDTO(
        @JsonProperty("hostname") String hostname,
        @JsonProperty("datacenter") String datacenter,
        @JsonProperty("aggregates") List<Aggregates> aggregates,
        @JsonProperty("svms") List<SVMData> svms
) {
    public record Aggregates(
            @JsonProperty("name") String name,
            @JsonProperty("uuid") String uuid,
            @JsonProperty("disk_class") String diskClass,
            @JsonProperty("mirror_enabled") Boolean mirrorEnabled
            ) {}

    /**
     * Storage Virtual Machine (SVM) - represents a virtualized ONTAP instance with isolated storage resources.
     * Each SVM can own volumes, export policies, and CIFS shares independently.
     */
    public record SVMData(
            @JsonProperty("name") String name,
            @JsonProperty("uuid") String uuid,
            @JsonProperty("volumes") List<VolumeData> volumes
    ) {}

    /**
     * FlexVol or FlexGroup volume - the primary storage container in ONTAP.
     * Volumes support both NFS and CIFS protocols and can be cloned from snapshots (FlexClone).
     */
    public record VolumeData(
            @JsonProperty("uuid") String uuid,
            @JsonProperty("name") String name,
            @JsonProperty("size") Long size,
            @JsonProperty("state") String state,
            @JsonProperty("type") String type,
            @JsonProperty("style") String style,
            @JsonProperty("space") SpaceData space,
            @JsonProperty("snapshot_policy") String snapshotPolicy,
            @JsonProperty("export_policy") ExportPolicyData exportPolicy,
            @JsonProperty("cifs_shares") List<ShareData> cifsShares,
            @JsonProperty("qtrees") List<QTreeData> qTrees,
            @JsonProperty("snapshots") List<SnapshotData> snapshots,
            @JsonProperty("aggregate_uuids") List<String> aggregateUUIDs,
            @JsonProperty("nas_path") String nasPath,
            @JsonProperty("mount_path_nfs") String mountPathNfs,
            @JsonProperty("is_flexclone") Boolean isFlexClone,
            @JsonProperty("parent_volume_name") String parentVolumeName,
            @JsonProperty("parent_volume_uuid") String parentVolumeUuid,
            @JsonProperty("parent_snapshot_name") String parentSnapshotName,
            @JsonProperty("parent_snapshot_uuid") String parentSnapshotUuid,
            @JsonProperty("parent_svm_name") String parentSvmName,
            @JsonProperty("parent_svm_uuid") String parentSvmUuid,
            @JsonProperty("is_split_initiated") Boolean isSplitInitiated,
            @JsonProperty("snaplock") SnaplockData snaplock
    ) {}

    /**
     * NFS export policy - defines access control rules for NFS clients accessing the volume.
     * Rules specify which clients can mount and with which permissions (read-only, read-write).
     */
    public record ExportPolicyData(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("rules") List<ExportRuleData> rules
    ) {}

    /**
     * Export policy rule - specifies access permissions for specific NFS clients.
     * Defines allowed protocols, read-write, and read-only access lists.
     */
    public record ExportRuleData(
            @JsonProperty("index") Long index,
            @JsonProperty("clients") List<ClientMatch> clients,
            @JsonProperty("protocols") List<String> protocols,
            @JsonProperty("rw_rule") List<String> rwRule,
            @JsonProperty("ro_rule") List<String> roRule
    ) {}

    /**
     * NFS client match specification - defines which IP addresses/hostnames are allowed by an export rule.
     * Can be a single IP, CIDR range, or hostname pattern.
     */
    public record ClientMatch(
            @JsonProperty("match") String match
    ) {}

    /**
     * CIFS (SMB) share - provides Windows file sharing access to volumes or qtrees.
     * Contains Access Control Lists (ACLs) defining user/group permissions.
     */
    public record ShareData(
            @JsonProperty("name") String name,
            @JsonProperty("path") String path,
            @JsonProperty("mount_path_cifs") String mountPathCifs,
            @JsonProperty("acls") List<ACLData> acls
    ) {}

    /**
     * CIFS Access Control Entry - defines user/group permissions on a share.
     * Permission can be: Full Control, Change, Read, etc.
     */
    public record ACLData(
            @JsonProperty("user_or_group") String userOrGroup,
            @JsonProperty("permission") String permission
    ) {}

    /**
     * QTree - a logical subdivision of a volume providing independent namespace and security boundaries.
     * Supports separate export policies, quotas, and security styles.
     */
    public record QTreeData(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("path") String path,
            @JsonProperty("mount_path_nfs") String mountPathNfs,
            @JsonProperty("security_style") String securityStyle,
            @JsonProperty("export_policy") ExportPolicyData exportPolicy,
            @JsonProperty("quota") QuotaData quota,
            @JsonProperty("cifs_shares") List<ShareData> cifsShares
            ) {}

    /**
     * QTree or Volume quota - enforces storage limits and tracks usage.
     * Supports soft and hard limits for user, group, or tree quotas.
     */
    public record QuotaData(
            @JsonProperty("index") Long index,
            @JsonProperty("type") String type,
            @JsonProperty("hard_limit") Long hardLimit,
            @JsonProperty("used_bytes") Long usedBytes,
            @JsonProperty("used_percent") Integer usedPercent
    ) {}

    /**
     * Snapshot - point-in-time read-only copy of volume contents.
     * Can be used for backup, disaster recovery, or FlexClone volume creation.
     */
    public record SnapshotData(
            @JsonProperty("uuid") String uuid,
            @JsonProperty("name") String name,
            @JsonProperty("create_time") OffsetDateTime createTime
    ) {}

    /**
     * Volume space utilization details - includes physical, logical, and snapshot space consumption.
     * Tracks available capacity and deduplication/compression savings.
     */
    public record SpaceData(
            @JsonProperty("available_percent") Integer availablePercent,
            @JsonProperty("afs_total") Long afsTotal,
            @JsonProperty("logical_space") LogicalSpaceData logicalSpace,
            @JsonProperty("snapshot") SnapshotSpaceData snapshot
    ) {}

    /**
     * Logical space metrics - space consumption from a logical (user data) perspective.
     * Shows used/available space and AFS (Application Footprint Size) savings from dedup/compression.
     */
    public record LogicalSpaceData(
            @JsonProperty("used") Long used,
            @JsonProperty("available") Long available,
            @JsonProperty("used_percent") Integer usedPercent,
            @JsonProperty("used_by_afs") Long usedByAfs
    ) {}

    /**
     * Snapshot space metrics - tracks reserved and consumed space by snapshots.
     * Reserve percent defines how much space is reserved for snapshots.
     */
    public record SnapshotSpaceData(
            @JsonProperty("reserve_percent") Integer reservePercent,
            @JsonProperty("reserve_size") Long reserveSize,
            @JsonProperty("used") Long used
    ) {}

    /**
     * SnapLock - WORM (Write Once Read Many) compliance features for volumes.
     * Enables immutable retention periods for regulatory compliance (GDPR, SOX, etc).
     */
    public record SnaplockData(
            @JsonProperty("append_mode_enabled") Boolean appendModeEnabled,
            @JsonProperty("autocommit_period") String autocommitPeriod,
            @JsonProperty("retention") RetentionData retention,
            @JsonProperty("type") String type
    ) {}

    /**
     * SnapLock retention policy - defines WORM retention durations (min, max, default).
     * Files cannot be deleted or modified until the retention period expires.
     */
    public record RetentionData(
            @JsonProperty("default") String defaultValue,
            @JsonProperty("minimum") String minimum,
            @JsonProperty("maximum") String maximum
    ) {}
}