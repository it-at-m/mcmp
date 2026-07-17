package de.muenchen.mcmp.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class UserFavoriteStorageId implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "storage_type", nullable = false)
    private String storageType;

    @NotNull
    @Column(name = "storage_uuid", nullable = false)
    private String storageUuid;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        UserFavoriteStorageId that = (UserFavoriteStorageId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(storageType, that.storageType)
                && Objects.equals(storageUuid, that.storageUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, storageType, storageUuid);
    }
}
