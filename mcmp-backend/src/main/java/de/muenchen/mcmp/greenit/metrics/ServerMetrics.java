package de.muenchen.mcmp.greenit.metrics;

import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "server_metrics", schema = "cmp", uniqueConstraints = @UniqueConstraint(columnNames = {"server_id", "created_at"}))
public class ServerMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "cpu_util")
    private Float cpuUtil;

    @Column(name = "mem_used_percent")
    private Float memUsedPercent;

    public ServerMetrics(Long serverId, OffsetDateTime createdAt, Float cpuUtil, Float memUsedPercent) {
        this.serverId = serverId;
        this.createdAt = createdAt;
        this.cpuUtil = cpuUtil;
        this.memUsedPercent = memUsedPercent;
    }
}