package de.muenchen.mcmp.snapshot;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "snapshot")
public class Snapshot extends AbstractEntity {
    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "snapshot_id", nullable = false)
    private Integer snapshotId;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "create_time")
    private java.time.OffsetDateTime createTime;

    @Column(name = "quiesced", nullable = false)
    private boolean quiesced;

    @Column(name = "state", length = 20)
    private String state;

    @Column(name = "replay_supported", nullable = false)
    private boolean replaySupported;

    @Column(name = "retention_period")
    private java.time.OffsetDateTime retentionPeriod;
}
