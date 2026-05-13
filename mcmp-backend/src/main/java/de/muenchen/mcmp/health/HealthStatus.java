package de.muenchen.mcmp.health;

import de.muenchen.mcmp.common.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "health_status")
public class HealthStatus extends AbstractEntity {

    @NotNull
    @Column(name = "identifier", nullable = false, length = Integer.MAX_VALUE)
    private String identifier;

    @NotNull
    @Column(name = "display_name", nullable = false, length = Integer.MAX_VALUE)
    private String displayName;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @NotNull
    @ColumnDefault("60")
    @Column(name = "expected_interval_minutes", nullable = false)
    private Integer expectedIntervalMinutes = 60;

    @Column(name = "quiet_period_start")
    private LocalTime quietPeriodStart;

    @Column(name = "quiet_period_end")
    private LocalTime quietPeriodEnd;

    @NotNull
    @ColumnDefault("3")
    @Column(name = "failure_threshold_yellow")
    private Integer failureThresholdYellow = 3;

    @NotNull
    @ColumnDefault("10")
    @Column(name = "failure_threshold_red")
    private Integer failureThresholdRed = 10;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;


}