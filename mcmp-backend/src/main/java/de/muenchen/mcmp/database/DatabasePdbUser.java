package de.muenchen.mcmp.database;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "database_pdb_user")
public class DatabasePdbUser extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "database_pdb_instance_id", nullable = false)
    private DatabasePdbInstance databasePdbInstance;

    @NotNull
    @Column(name = "user_name", nullable = false, length = Integer.MAX_VALUE)
    private String userName;

    @Column(name = "account_status", length = Integer.MAX_VALUE)
    private String accountStatus;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @Column(name = "profile", length = Integer.MAX_VALUE)
    private String profile;

    @Column(name = "tablespaces", length = Integer.MAX_VALUE)
    private String tablespaces;


}