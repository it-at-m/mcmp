package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "ontap_cifs_share")
public class OntapCifsShare extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "volume_id")
    private OntapVolume volume;

    @Column(name = "volume_id", insertable = false, updatable = false)
    private Long volumeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "qtree_id")
    private OntapQtree qtree;

    @Column(name = "qtree_id", insertable = false, updatable = false)
    private Long qtreeId;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "path")
    private String path;

    @Column(name = "mount_path_cifs")
    private String mountPathCifs;

    @ManyToMany
    @JoinTable(name = "ontap_cifs_share_has_appservices", joinColumns = {@JoinColumn(name = "ontap_cifs_share_id")}, inverseJoinColumns = {@JoinColumn(name = "appservice_id")})
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @OneToMany(mappedBy = "share")
    private Set<OntapCifsShareAcl> ontapCifsShareAcls = new LinkedHashSet<>();

    /**
     * Validates that at least one of the associated volume or qtree is present for this CIFS share.
     * <p>
     * This method enforces a business rule mirroring the database-level check constraint
     * {@code CHECK (volume_id IS NOT NULL OR qtree_id IS NOT NULL)} on the {@code ontap_cifs_share} table.
     * It ensures data integrity by preventing the creation or update of a CIFS share entity where both
     * {@code volume} and {@code qtree} are null, which would violate the logical requirement that a share
     * must be tied to either a volume or a qtree (but not necessarily both).
     * </p>
     * <p>
     * Used in conjunction with Bean Validation's {@code @AssertTrue} annotation, this validation runs
     * automatically during JPA entity lifecycle events (e.g., persist, update) when validation is enabled,
     * such as in Spring Boot controllers with {@code @Valid}. If the condition fails, a
     * {@code ConstraintViolationException} is thrown with the specified message.
     * </p>
     * <p>
     * Note: This is an application-level validation and complements (but does not replace) the database
     * constraint for cases where direct SQL modifications bypass JPA.
     * </p>
     *
     * @return {@code true} if either the {@code volume} or {@code qtree} field is not null, indicating
     * valid association; {@code false} otherwise, triggering a validation error.
     */
    @SuppressWarnings("unused")  // Suppresses IntelliJ's "unused" warning as this method is used by Bean Validation
    @AssertTrue(message = "Either volume or qtree must be set")
    private boolean isVolumeOrQtreePresent() {
        return volume != null || qtree != null;
    }
}