package de.muenchen.mcmp.database;

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
@Table(name = "database_pdb_tablespace")
public class DatabasePdbTablespace extends AbstractEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "database_pdb_instance_id", nullable = false)
    private DatabasePdbInstance databasePdbInstance;

    @NotNull
    @Column(name = "tablespace_name", nullable = false, length = Integer.MAX_VALUE)
    private String tablespaceName;

    @Column(name = "tablespace_type", length = Integer.MAX_VALUE)
    private String tablespaceType;

    @Column(name = "data_max_in_b")
    private Long dataMaxInB;

    @Column(name = "data_used_in_b")
    private Long dataUsedInB;


}