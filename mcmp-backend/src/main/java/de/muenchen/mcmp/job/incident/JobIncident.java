package de.muenchen.mcmp.job.incident;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.job.Job;
import de.muenchen.mcmp.types.IncidentSourceType;
import de.muenchen.mcmp.types.IncidentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "job_incident")
public class JobIncident extends AbstractEntity {

    @NotNull
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @ColumnDefault("'open'")
    @Column(name = "status")
    @ColumnTransformer(write = "?::incident_status")
    private IncidentStatus status;

    @NotNull
    @Column(name = "source_type", nullable = false)
    @ColumnTransformer(write = "?::incident_source_type")
    private IncidentSourceType sourceType;

    @Column(name = "short_description", length = Integer.MAX_VALUE)
    private String shortDescription;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    @Column(name = "caller_sys_id", length = Integer.MAX_VALUE)
    private String callerSysId;

    @Column(name = "cmdb_ci_sys_id", length = Integer.MAX_VALUE)
    private String cmdbCiSysId;

    @Column(name = "assignment_group_sys_id", length = Integer.MAX_VALUE)
    private String assignmentGroupSysId;

    @Column(name = "assignment_group_name", length = Integer.MAX_VALUE)
    private String assignmentGroupName;

    @Column(name = "change_sys_id", length = Integer.MAX_VALUE)
    private String changeSysId;

    @NotNull
    @Column(name = "incident_sys_id", nullable = false, length = Integer.MAX_VALUE)
    private String incidentSysId;

    @Column(name = "incident_number", length = Integer.MAX_VALUE)
    private String incidentNumber;

    @Column(name = "incident_link", length = Integer.MAX_VALUE)
    private String incidentLink;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message", length = Integer.MAX_VALUE)
    private String errorMessage;

    @Column(name = "close_code_label", length = Integer.MAX_VALUE)
    private String closeCodeLabel;

    @Column(name = "close_code_value", length = Integer.MAX_VALUE)
    private String closeCodeValue;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "state_label", length = Integer.MAX_VALUE)
    private String stateLabel;

    @Column(name = "state_value", length = Integer.MAX_VALUE)
    private String stateValue;

    @Column(name = "close_notes", length = Integer.MAX_VALUE)
    private String closeNotes;


}