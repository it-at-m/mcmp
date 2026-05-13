package de.muenchen.mcmp.backup;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "backup")
public class Backup extends AbstractEntity {
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "backup_type", nullable = false)
    private String backupType;

    @Column(name = "backup_server", length = 100, nullable = false)
    private String backupServer;

    @Column(name = "client_server", length = 100, nullable = false)
    private String clientServer;

    @Column(name = "save_set_name", length = 100, nullable = false)
    private String saveSetName;

    @Column(name = "save_time_string", length = 50, nullable = false)
    private String saveTimeString;

    @Column(name = "save_time", nullable = false)
    private OffsetDateTime saveTime;

    @Column(name = "ssid", length = 50, nullable = false)
    private String ssid;

    @Column(name = "clone_id", length = 50, nullable = false)
    private String cloneId;

    @Column(name = "pool", length = 100, nullable = false)
    private String pool;

    @Size(max = 30)
    @Column(name = "ssretent_string", length = 30)
    private String ssretentString;

    @Column(name = "ssretent")
    private OffsetDateTime ssretent;

    @Column(name = "totalsize")
    private Long totalsize;

    @Size(max = 10)
    @Column(name = "runtime", length = 10)
    private String runtime;

}

