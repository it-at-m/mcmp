package de.muenchen.mcmp.greenit.rightsizing;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.job.Job;
import de.muenchen.mcmp.server.Server;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "green_it_rightsizing")
public class GreenItRightsizing extends AbstractEntity {

    @NotNull
    @Column(name = "vm_name", nullable = false, length = Integer.MAX_VALUE)
    private String vmName;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @NotNull
    @Column(name = "cpu_current", nullable = false)
    private Integer cpuCurrent;

    @NotNull
    @Column(name = "cpu_new", nullable = false)
    private Integer cpuNew;

    @NotNull
    @Column(name = "ram_current", nullable = false)
    private Integer ramCurrent;

    @NotNull
    @Column(name = "ram_new", nullable = false)
    private Integer ramNew;

    @NotNull
    @Column(name = "server_uuid", nullable = false, length = Integer.MAX_VALUE)
    private String serverUuid;

    @NotNull
    @Size(max = 1)
    @Column(name = "vcenter_short_code", length = 1, columnDefinition = "citext")
    private String vcenterShortCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "server_id")
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "appservice_id")
    private Appservice appservice;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "job_id")
    private Job job;

    @Column(name = "status")
    private String status;
}