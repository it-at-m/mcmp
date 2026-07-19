package de.muenchen.mcmp.storage;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Storage is unified across several backing entity types (Ontap volumes, qtrees, StorageGrid
 * buckets) with no shared numeric id space, so favorites are keyed by (userId, storageType,
 * storageUuid) rather than a FK to a single storage table.
 */
@Getter
@Setter
@Entity
@Table(name = "user_favorite_storage")
public class UserFavoriteStorage {
    @EmbeddedId
    private UserFavoriteStorageId id;
}
