package de.muenchen.mcmp.ontap;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "ontap_cifs_share_acl")
public class OntapCifsShareAcl extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "share_id", nullable = false)
    private OntapCifsShare share;

    @Column(name = "share_id", insertable = false, updatable = false)
    private Long shareId;

    @Column(name = "user_or_group")
    private String userOrGroup;

    @Column(name = "permission")
    private String permission;
}