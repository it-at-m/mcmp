package de.muenchen.mcmp.clients.netapp.storagegrid;

import java.util.List;
import java.util.Map;

public record StorageGridDTO(String hostname, List<AccountWithUsage> accounts) {

    public record AccountWithUsage(
            String id,
            String name,
            String description,
            List<String> capabilities,
            Map<String, Object> synchronizeRules,
            Boolean useAccountIdentitySource,
            Boolean allowPlatformServices,
            Boolean allowSelectObjectContent,
            List<Object> allowedGridFederationConnections,
            Boolean allowComplianceMode,
            Long maxRetentionDays,
            Long maxRetentionYears,
            Long quotaObjectBytes,
            Long dataBytes,
            Long objectCount,
            String calculationTime,
            List<BucketUsage> buckets
    ) {

        public record BucketUsage(
                String name,
                Long dataBytes,
                Long objectCount,
                String region,
                Long quotaObjectBytes
        ) {}

    }

}