package de.muenchen.mcmp.storage;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.ontap.*;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import de.muenchen.mcmp.storagegrid.StorageGridBucket;
import de.muenchen.mcmp.storagegrid.StorageGridBucketRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedStorageService {

    private final OntapVolumeRepository ontapVolumeRepository;
    private final OntapQtreeRepository ontapQtreeRepository;
    private final StorageGridBucketRepository storageGridBucketRepository;
    private final OntapVolumeServerMountRepository ontapVolumeServerMountRepository;
    private final OntapQtreeServerMountRepository ontapQtreeServerMountRepository;

    @Transactional(readOnly = true)
    public UnifiedStorageItemDto getUnifiedStorageItem(String uuid, StorageType type) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String username = userRoles.getUsername();
        boolean isAdmin = userRoles.hasAdminRole();
        boolean isReadonly = userRoles.hasReadonlyRole();

        if (type == StorageType.NFS || type == StorageType.CIFS) {
            OntapVolume volume = ontapVolumeRepository.findByVolumeUuidWithPermissions(UUID.fromString(uuid), username, isAdmin, isReadonly)
                    .orElseThrow(() -> new EntityNotFoundException("Volume not found or access denied"));

            String protocol = determineProtocolFromSvm(volume.getSvm());
            if (protocol == null) {
                protocol = type == StorageType.NFS ? "NFS" : "CIFS"; // Fallback to type-based protocol
            }

            UnifiedStorageItemDto.UnifiedStorageItemDtoBuilder builder = UnifiedStorageItemDto.builder()
                    .uuid(volume.getVolumeUuid().toString())
                    .name(volume.getName())
                    .type(type) // Use requested type or infer? Both valid. Let's use requested.
                    .protocol(protocol)
                    .appservices(volume.getAppservices().stream()
                            .map(a -> new de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO(a.getId(), a.getName(), a.getSysId()))
                            .toList())
                    .size(volume.getSize())
                    .used(volume.getSpaceLogicalUsed())
                    // Extended Volume Properties
                    .mirrorEnabled(getMirrorEnabled(volume))
                    .diskClass(getDiskClass(volume))
                    .isFlexClone(Boolean.TRUE.equals(volume.getIsFlexClone()))

                    .isWorm(isWorm(volume.getSnaplockType()))
                    .minRetention(volume.getSnaplockRetentionMinimum())
                    .maxRetention(volume.getSnaplockRetentionMaximum())
                    .defaultRetention(volume.getSnaplockRetentionDefault())
                    .autocommitPeriod(volume.getSnaplockAutocommitPeriod())
                    .appendMode(Boolean.TRUE.equals(volume.getSnaplockAppendModeEnabled()))
                    .spaceAvailablePercent(volume.getSpaceAvailablePercent())
                    .spaceAfsTotal(volume.getSpaceAfsTotal())
                    .spaceLogicalUsed(volume.getSpaceLogicalUsed())
                    .spaceLogicalAvailable(volume.getSpaceLogicalAvailable())
                    .spaceLogicalUsedPercent(volume.getSpaceLogicalUsedPercent())
                    .spaceLogicalUsedByAfs(volume.getSpaceLogicalUsedByAfs())
                    .spaceSnapshotReservePercent(volume.getSpaceSnapshotReservePercent())
                    .spaceSnapshotReserveSize(volume.getSpaceSnapshotReserveSize())
                    .spaceSnapshotUsed(volume.getSpaceSnapshotUsed())
                    .snapshotPolicy(volume.getSnapshotPolicy());

            if ("NFS".equals(protocol)) {
                builder.nfs_mount_path(volume.getMountPathNfs())
                       .nfs_export_policy(mapExportPolicyToDto(volume.getExportPolicy()));
            } else if ("CIFS".equals(protocol)) {
                 if (volume.getOntapCifsShares() != null && !volume.getOntapCifsShares().isEmpty()) {
                        OntapCifsShare share = volume.getOntapCifsShares().iterator().next();
                        builder.cifs_share_name(share.getName())
                               .cifs_mount_path(share.getMountPathCifs())
                                .cifs_share_acl_list(mapCifsShareAclToDto(share.getOntapCifsShareAcls()));
                 }
            }
            if (volume.getParentVolume() != null) {
                if (volume.getParentVolume().getMountPathNfs() != null && !volume.getParentVolume().getMountPathNfs().isEmpty()) {
                    builder.parentVolumeType(StorageType.NFS);
                } else if (volume.getParentVolume().getOntapCifsShares() != null && !volume.getParentVolume().getOntapCifsShares().isEmpty()) {
                    builder.parentVolumeType(StorageType.CIFS);
                } else {
                    builder.parentVolumeType(null); // Unknown or not applicable
                }
                builder.parentVolumeName(volume.getParentVolume().getName())
                       .parentVolumeUUID(volume.getParentVolume().getVolumeUuid())
                       .parentSnapshotName(volume.getParentSnapshot().getName());
            }
            return builder.build();

        } else if (type == StorageType.QTREE) {
             OntapQtree qtree = ontapQtreeRepository.findByIdWithPermissions(Long.parseLong(uuid), username, isAdmin, isReadonly)
                    .orElseThrow(() -> new EntityNotFoundException("Qtree not found or access denied"));

             String protocol = determineProtocolFromSvm(qtree.getVolume().getSvm());
             if (protocol == null) {
                 protocol = "NFS"; // Fallback
             }

             return UnifiedStorageItemDto.builder()
                            .uuid(String.valueOf(qtree.getId()))
                            .name(qtree.getName())
                            .type(StorageType.QTREE)
                            .protocol(protocol)
                            .appservices(qtree.getAppservices().stream()
                                    .map(a -> new de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO(a.getId(), a.getName(), a.getSysId()))
                                    .toList())
                            .size(qtree.getQuotaHardLimit()) // Can be null
                            .used(qtree.getQuotaUsedBytes()) // Can be null
                            .diskClass(getDiskClass(qtree.getVolume()))
                            .mirrorEnabled(getMirrorEnabled(qtree.getVolume()))
                            .diskClass(getDiskClass(qtree.getVolume()))
                            .isWorm(isWorm(qtree.getVolume().getSnaplockType()))
                            .minRetention(qtree.getVolume().getSnaplockRetentionMinimum())
                            .maxRetention(qtree.getVolume().getSnaplockRetentionMaximum())
                            .defaultRetention(qtree.getVolume().getSnaplockRetentionDefault())
                            .autocommitPeriod(qtree.getVolume().getSnaplockAutocommitPeriod())
                            .appendMode(Boolean.TRUE.equals(qtree.getVolume().getSnaplockAppendModeEnabled()))
                            .nfs_mount_path(qtree.getMountPathNfs())
                            .nfs_export_policy(mapExportPolicyToDto(qtree.getExportPolicy()))
                            .nfs_security_style(qtree.getSecurityStyle())
                            .build();
        } else if (type == StorageType.S3) {
             StorageGridBucket bucket = storageGridBucketRepository.findByIdWithPermissions(Long.parseLong(uuid), username, isAdmin, isReadonly)
                    .orElseThrow(() -> new EntityNotFoundException("Bucket not found or access denied"));

             Long quota = null;
             if (bucket.getStorageGridAccount() != null) {
                 quota = bucket.getStorageGridAccount().getQuotaObjectBytes();
             }

             return UnifiedStorageItemDto.builder()
                            .uuid(String.valueOf(bucket.getId()))
                            .name(bucket.getName())
                            .type(StorageType.S3)
                            .protocol("S3")
                            .appservices(bucket.getStorageGridAccount() != null ?
                                    bucket.getStorageGridAccount().getAppservices().stream()
                                            .map(a -> new de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO(a.getId(), a.getName(), a.getSysId()))
                                            .toList() :
                                    List.of())
                            .size(quota) // Using Account Quota as size
                            .used(bucket.getDataBytes())
                            .s3_object_count(bucket.getObjectCount())
                            .s3_bucket_name(bucket.getName())
                            .build();
        }

        throw new IllegalArgumentException("Unknown storage type: " + type);
    }

    @Transactional(readOnly = true)
    public List<UnifiedStorageSnapshotListDto> getUnifiedStorageSnapshots(String uuid, StorageType type) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String username = userRoles.getUsername();
        boolean isAdmin = userRoles.hasAdminRole();
        boolean isReadonly = userRoles.hasReadonlyRole();

        if (type == StorageType.NFS || type == StorageType.CIFS) {
            OntapVolume volume = ontapVolumeRepository.findByVolumeUuidWithPermissions(UUID.fromString(uuid), username, isAdmin, isReadonly)
                    .orElseThrow(() -> new EntityNotFoundException("Volume not found or access denied"));

            return mapSnapshotsToDto(volume.getOntapSnapshots());

        } else if (type == StorageType.QTREE) {
            OntapQtree qtree = ontapQtreeRepository.findByIdWithPermissions(Long.parseLong(uuid), username, isAdmin, isReadonly)
                    .orElseThrow(() -> new EntityNotFoundException("Qtree not found or access denied"));

            return mapSnapshotsToDto(qtree.getVolume().getOntapSnapshots());

        } else if (type == StorageType.S3) {
            storageGridBucketRepository.findByIdWithPermissions(Long.parseLong(uuid), username, isAdmin, isReadonly)
                    .orElseThrow(() -> new EntityNotFoundException("Bucket not found or access denied"));

            return Collections.emptyList();
        }

        throw new IllegalArgumentException("Unknown storage type: " + type);
    }

    private List<UnifiedStorageSnapshotListDto> mapSnapshotsToDto(Set<OntapSnapshot> snapshots) {
        if (snapshots == null) {
            return Collections.emptyList();
        }
        return snapshots.stream()
                .map(snapshot -> UnifiedStorageSnapshotListDto.builder()
                        .uuid(snapshot.getSnapshotUuid())
                        .name(snapshot.getName())
                        .createTime(snapshot.getCreateTime() != null ? snapshot.getCreateTime().toString() : null)
                        .build())
                .sorted(Comparator.comparing(UnifiedStorageSnapshotListDto::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UnifiedStorageItemListDto> getUnifiedStorage(String search, Pageable pageable) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String username = userRoles.getUsername();
        boolean isAdmin = userRoles.hasAdminRole();
        boolean isReadonly = userRoles.hasReadonlyRole();

        String searchTerm = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;

        List<UnifiedStorageItemListDto> allItems = new ArrayList<>();

        // 1. Fetch NFS Volumes
        List<Object[]> nfsItems = ontapVolumeRepository.findNfsVolumeListItems(searchTerm, username, isAdmin, isReadonly);
        List<UUID> nfsVolumeUuids = nfsItems.stream().map(row -> (UUID) row[0]).toList();
        Map<String, String> nfsAppserviceNames = loadVolumeAppserviceNames(nfsVolumeUuids);

        for (Object[] row : nfsItems) {
            String uuidStr = row[0].toString();
            String svmName = (String) row[2];
            String protocol = determineProtocolFromSvmName(svmName);
            if (protocol == null) {
                protocol = "NFS";
            }
            allItems.add(UnifiedStorageItemListDto.builder()
                    .uuid(uuidStr)
                    .name((String) row[1])
                    .type(StorageType.NFS)
                    .protocol(protocol)
                    .appserviceNames(nfsAppserviceNames.get(uuidStr))
                    .build());
        }

        // 2. Fetch CIFS Volumes
        List<Object[]> cifsItems = ontapVolumeRepository.findCifsVolumeListItems(searchTerm, username, isAdmin, isReadonly);
        List<UUID> cifsVolumeUuids = cifsItems.stream().map(row -> (UUID) row[0]).toList();
        Map<String, String> cifsAppserviceNames = loadVolumeAppserviceNames(cifsVolumeUuids);

        for (Object[] row : cifsItems) {
            String uuidStr = row[0].toString();
            String svmName = (String) row[2];
            String protocol = determineProtocolFromSvmName(svmName);
            if (protocol == null) {
                protocol = "CIFS";
            }
            allItems.add(UnifiedStorageItemListDto.builder()
                    .uuid(uuidStr)
                    .name((String) row[1])
                    .type(StorageType.CIFS)
                    .protocol(protocol)
                    .appserviceNames(cifsAppserviceNames.get(uuidStr))
                    .build());
        }

        // 3. Fetch NFS Qtrees
        List<Object[]> qtreeItems = ontapQtreeRepository.findNfsQtreeListItems(searchTerm, username, isAdmin, isReadonly);
        List<Long> qtreeIds = qtreeItems.stream().map(row -> (Long) row[0]).toList();
        Map<String, String> qtreeAppserviceNames = loadQtreeAppserviceNames(qtreeIds);

        for (Object[] row : qtreeItems) {
            String idStr = row[0].toString();
            String svmName = (String) row[2];
            String protocol = determineProtocolFromSvmName(svmName);
            if (protocol == null) {
                protocol = "NFS";
            }
            allItems.add(UnifiedStorageItemListDto.builder()
                    .uuid(idStr)
                    .name((String) row[1])
                    .type(StorageType.QTREE)
                    .protocol(protocol)
                    .appserviceNames(qtreeAppserviceNames.get(idStr))
                    .build());
        }

        // 4. Fetch S3 Buckets
        List<Object[]> bucketItems = storageGridBucketRepository.findBucketListItems(searchTerm, username, isAdmin, isReadonly);
        List<Long> bucketIds = bucketItems.stream().map(row -> (Long) row[0]).toList();
        Map<String, String> bucketAppserviceNames = loadBucketAppserviceNames(bucketIds);

        for (Object[] row : bucketItems) {
            String idStr = row[0].toString();
            allItems.add(UnifiedStorageItemListDto.builder()
                    .uuid(idStr)
                    .name((String) row[1])
                    .type(StorageType.S3)
                    .protocol("S3")
                    .appserviceNames(bucketAppserviceNames.get(idStr))
                    .build());
        }

        // 5. Global Sorting
        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            sort.stream().forEach(order -> {
                Comparator<UnifiedStorageItemListDto> comparator = null;

                // Determine comparator based on property
                if ("name".equalsIgnoreCase(order.getProperty())) {
                    comparator = Comparator.comparing(UnifiedStorageItemListDto::getName, String.CASE_INSENSITIVE_ORDER);
                } else if ("type".equalsIgnoreCase(order.getProperty())) {
                    comparator = Comparator.comparing(dto -> dto.getType().toString());
                } else if ("protocol".equalsIgnoreCase(order.getProperty())) {
                    comparator = Comparator.comparing(UnifiedStorageItemListDto::getProtocol);
                }

                if (comparator != null) {
                    if (order.isDescending()) {
                        comparator = comparator.reversed();
                    }
                    // Apply sort (Note: this is simple single-property sort logic per iteration,
                    // for multi-property we would chain valid comparators.
                    // Assuming mostly single column sort from UI.)
                    allItems.sort(comparator);
                }
            });
        } else {
            // Default sort by name ascending if no sort specified
            allItems.sort(Comparator.comparing(UnifiedStorageItemListDto::getName, String.CASE_INSENSITIVE_ORDER));
        }

        // 6. Pagination in Memory
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allItems.size());

        List<UnifiedStorageItemListDto> pagedItems;
        if (start > allItems.size()) {
            pagedItems = Collections.emptyList();
        } else {
            pagedItems = allItems.subList(start, end);
        }

        return new PageImpl<>(pagedItems, pageable, allItems.size());
    }

    @Transactional(readOnly = true)
    public List<UnifiedStorageMountItemDto> getUnifiedStorageMountsByServerId(Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String username = userRoles.getUsername();
        boolean isAdmin = userRoles.hasAdminRole();
        boolean isReadonly = userRoles.hasReadonlyRole();

        List<UnifiedStorageMountItemDto> result = new ArrayList<>();

        // 1. Fetch Volume Mounts
        List<OntapVolumeServerMount> volumeMounts = ontapVolumeServerMountRepository.findAllByServerIdWithPermissions(serverId, username, isAdmin, isReadonly);
        for (OntapVolumeServerMount mount : volumeMounts) {
            OntapVolume volume = mount.getOntapVolume();
            String protocol = determineProtocolFromSvm(volume.getSvm());
            if (protocol == null) {
                protocol = "NFS"; // Default fallback
            }

            UnifiedStorageMountItemDto dto = UnifiedStorageMountItemDto.builder()
                    .mountPoint(mount.getMountPoint())
                    .filesystem(mount.getFilesystem())
                    .options(mount.getOptions())
                    .serverId(mount.getServerId())
                    .uuid(volume.getVolumeUuid().toString())
                    .name(volume.getName())
                    .type(StorageType.NFS)
                    .size(volume.getSize())
                    .used(volume.getSpaceLogicalUsed())
                    .protocol(protocol)
                    .appservices(volume.getAppservices().stream()
                            .map(a -> new de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO(a.getId(), a.getName(), a.getSysId()))
                            .toList())
                    .nfs_mount_path(volume.getMountPathNfs())
                    .build();

            if ("CIFS".equals(protocol)) {
                dto.setType(StorageType.CIFS);
                if (volume.getOntapCifsShares() != null && !volume.getOntapCifsShares().isEmpty()) {
                    OntapCifsShare share = volume.getOntapCifsShares().iterator().next();
                    dto.setCifs_share_name(share.getName());
                    dto.setCifs_mount_path(share.getMountPathCifs());
                }
            }

            result.add(dto);
        }

        // 2. Fetch Qtree Mounts
        List<OntapQtreeServerMount> qtreeMounts = ontapQtreeServerMountRepository.findAllByServerIdWithPermissions(serverId, username, isAdmin, isReadonly);
        for (OntapQtreeServerMount mount : qtreeMounts) {
            OntapQtree qtree = mount.getOntapQtree();
            String protocol = determineProtocolFromSvm(qtree.getVolume().getSvm());
            if (protocol == null) {
                protocol = "NFS"; // Default fallback
            }

            UnifiedStorageMountItemDto dto = UnifiedStorageMountItemDto.builder()
                    .mountPoint(mount.getMountPoint())
                    .filesystem(mount.getFilesystem())
                    .options(mount.getOptions())
                    .serverId(mount.getServerId())
                    .uuid(String.valueOf(qtree.getId()))
                    .name(qtree.getName())
                    .type(StorageType.QTREE)
                    .size(qtree.getQuotaHardLimit())
                    .used(qtree.getQuotaUsedBytes())
                    .protocol(protocol)
                    .appservices(qtree.getAppservices().stream()
                            .map(a -> new de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO(a.getId(), a.getName(), a.getSysId()))
                            .toList())
                    .nfs_mount_path(qtree.getMountPathNfs())
                    .build();

            result.add(dto);
        }

        return result;
    }

    private List<OntapCifsShareAclListDto> mapCifsShareAclToDto(Set<OntapCifsShareAcl> acls) {
        if (acls == null) {
            return Collections.emptyList();
        }
        return acls.stream()
                .map(acl -> OntapCifsShareAclListDto.builder()
                        .shareAclId(acl.getId())
                        .userOrGroup(acl.getUserOrGroup())
                        .permission(acl.getPermission())
                        .build())
                .collect(Collectors.toList());
    }

    private OntapExportPolicyListDto mapExportPolicyToDto(OntapExportPolicy policy) {
        if (policy == null) {
            return null;
        }
        return OntapExportPolicyListDto.builder()
                .exportPolicyId(policy.getExportPolicyId())
                .name(policy.getName())
                .ontapExportPolicyRules(policy.getOntapExportPolicyRules().stream()
                        .filter(rule -> rule.getClients() == null || rule.getClients().stream()
                                .noneMatch(c -> c.toLowerCase().contains("barkeeper")))
                        .map(rule -> OntapExportPolicyRuleListDto.builder()
                                .policyId(policy.getExportPolicyId())
                                .clients(rule.getClients() == null ? null : rule.getClients().stream()
                                        .map(this::formatClientName)
                                        .collect(Collectors.toList()))
                                .rwRules(rule.getRwRules())
                                .build())
                        .collect(Collectors.toSet()))
                .build();
    }

    private String formatClientName(String client) {
        if (client == null) {
            return null;
        }
        return client.replaceFirst("(\\d{3})n", "$1");
    }

    private boolean isWorm(String snaplockType) {
        return snaplockType != null && !"non_snaplock".equals(snaplockType);
    }

    private Boolean getMirrorEnabled(OntapVolume volume) {
        if (volume.getOntapAggregates() == null || volume.getOntapAggregates().isEmpty()) {
            return null;
        }
        // Assuming all aggregates for a volume have similar properties or picking first
        return volume.getOntapAggregates().iterator().next().getMirrorEnabled();
    }

    private String getDiskClass(OntapVolume volume) {
        if (volume.getOntapAggregates() == null || volume.getOntapAggregates().isEmpty()) {
            return null;
        }
        return volume.getOntapAggregates().iterator().next().getDiskClass();
    }

    private String determineProtocolFromSvm(OntapSvm svm) {
        if (svm == null || svm.getName() == null) {
            return null;
        }
        return determineProtocolFromSvmName(svm.getName());
    }

    private String determineProtocolFromSvmName(String svmName) {
        if (svmName == null) {
            return null;
        }
        String svmNameLower = svmName.toLowerCase();
        if (svmNameLower.endsWith("dcn")) {
            return "NFS";
        } else if (svmNameLower.endsWith("dcc")) {
            return "CIFS";
        }
        return null;
    }

    private Map<String, String> loadVolumeAppserviceNames(List<UUID> uuids) {
        Map<String, String> result = new HashMap<>();
        if (uuids.isEmpty()) {
            return result;
        }
        List<OntapVolume> volumes = ontapVolumeRepository.findByVolumeUuidsWithAppservices(uuids);
        for (OntapVolume vol : volumes) {
            String appNames = vol.getAppservices().stream()
                    .map(Appservice::getName)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            result.put(vol.getVolumeUuid().toString(), appNames.isEmpty() ? null : appNames);
        }
        return result;
    }

    private Map<String, String> loadQtreeAppserviceNames(List<Long> ids) {
        Map<String, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<OntapQtree> qtrees = ontapQtreeRepository.findByIdsWithAppservices(ids);
        for (OntapQtree qtree : qtrees) {
            String appNames = qtree.getAppservices().stream()
                    .map(Appservice::getName)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            result.put(String.valueOf(qtree.getId()), appNames.isEmpty() ? null : appNames);
        }
        return result;
    }

    private Map<String, String> loadBucketAppserviceNames(List<Long> ids) {
        Map<String, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<StorageGridBucket> buckets = storageGridBucketRepository.findByIdsWithAppservices(ids);
        for (StorageGridBucket bucket : buckets) {
            String appNames = "";
            if (bucket.getStorageGridAccount() != null && bucket.getStorageGridAccount().getAppservices() != null) {
                appNames = bucket.getStorageGridAccount().getAppservices().stream()
                        .map(Appservice::getName)
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(", "));
            }
            result.put(String.valueOf(bucket.getId()), appNames.isEmpty() ? null : appNames);
        }
        return result;
    }

    public boolean canUserEditStorage(String uuid, StorageType storageType) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String username = userRoles.getUsername();

        try {
            if (storageType == StorageType.NFS || storageType == StorageType.CIFS) {
                return Boolean.TRUE.equals(
                        ontapVolumeRepository.canUserEditVolume(UUID.fromString(uuid), username));
            }

            if (storageType == StorageType.QTREE) {
                return Boolean.TRUE.equals(
                        ontapQtreeRepository.canUserEditQtree(Long.parseLong(uuid), username));
            }

            if (storageType == StorageType.S3) {
                return Boolean.TRUE.equals(
                        storageGridBucketRepository.canUserEditBucket(Long.parseLong(uuid), username));
            }

            return false;
        } catch (IllegalArgumentException ex) {
            log.debug("Invalid storage identifier for edit check: type={}, uuid={}", storageType, uuid, ex);
            return false;
        }
    }
}
