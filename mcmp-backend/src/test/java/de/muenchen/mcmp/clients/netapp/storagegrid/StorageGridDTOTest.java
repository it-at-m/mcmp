package de.muenchen.mcmp.clients.netapp.storagegrid;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for the Spring Boot 4 / Jackson 3 upgrade: several {@code AccountWithUsage}
 * fields were primitives ({@code boolean}/{@code long}) that crashed the JSON parser when
 * StorageGRID omitted them (e.g. accounts with no usage data yet). They are now boxed and left
 * nullable, matching the already-nullable {@code StorageGridAccount}/{@code StorageGridBucket}
 * entity columns.
 */
class StorageGridDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void accountWithUsage_allNullableFieldsNull_doesNotThrow() {
        String json = """
                {
                  "id": "acct-1",
                  "name": "account1",
                  "useAccountIdentitySource": null,
                  "allowPlatformServices": null,
                  "allowSelectObjectContent": null,
                  "allowComplianceMode": null,
                  "maxRetentionYears": null,
                  "quotaObjectBytes": null,
                  "dataBytes": null,
                  "objectCount": null
                }
                """;

        StorageGridDTO.AccountWithUsage account = MAPPER.readValue(json, StorageGridDTO.AccountWithUsage.class);

        assertNull(account.useAccountIdentitySource());
        assertNull(account.allowPlatformServices());
        assertNull(account.allowSelectObjectContent());
        assertNull(account.allowComplianceMode());
        assertNull(account.maxRetentionYears());
        assertNull(account.quotaObjectBytes());
        assertNull(account.dataBytes());
        assertNull(account.objectCount());
    }

    @Test
    void bucketUsage_nullDataBytesAndObjectCount_doesNotThrow() {
        String json = """
                {"name": "bucket1", "dataBytes": null, "objectCount": null}
                """;

        StorageGridDTO.AccountWithUsage.BucketUsage bucket =
                MAPPER.readValue(json, StorageGridDTO.AccountWithUsage.BucketUsage.class);

        assertNull(bucket.dataBytes());
        assertNull(bucket.objectCount());
    }
}
