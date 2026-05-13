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
            boolean useAccountIdentitySource,
            boolean allowPlatformServices,
            boolean allowSelectObjectContent,
            List<Object> allowedGridFederationConnections,
            boolean allowComplianceMode,
            Long maxRetentionDays,
            long maxRetentionYears,
            long quotaObjectBytes,
            long dataBytes,
            long objectCount,
            String calculationTime,
            List<BucketUsage> buckets
    ) {

        public record BucketUsage(
                String name,
                long dataBytes,
                long objectCount,
                String region,
                Long quotaObjectBytes
        ) {}

    }

}