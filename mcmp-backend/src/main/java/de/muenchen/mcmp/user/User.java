package de.muenchen.mcmp.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

@Getter
@Setter
@Entity
@Table(name = "\"user\"")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@DynamicUpdate
public class User extends AbstractEntity {
    @Size(max = 100)
    @NotNull
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Size(max = 100)
    @NotNull
    @Column(name = "sys_id", nullable = false, length = 100)
    private String sysId;

    @Size(max = 100)
    @NotNull
    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "admin", nullable = false)
    private Boolean admin = false;

    @Size(max = 50)
    @Column(name = "name", nullable = true, length = 50)
    private String name;

    @Size(max = 50)
    @Column(name = "email", nullable = true, length = 50)
    private String email;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "dark_mode", nullable = false)
    private Boolean darkMode = false;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "special_role", nullable = false)
    private Boolean specialRole = false;
}