package de.muenchen.mcmp.job.node;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.job.Job;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import net.minidev.json.annotate.JsonIgnore;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "job_nodes")
public class JobNode extends AbstractEntity {

    @NotNull
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @NotNull
    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "node_alias", length = Integer.MAX_VALUE)
    private String nodeAlias;

    @Column(name = "node_identifier", length = Integer.MAX_VALUE)
    private String nodeIdentifier;

    @NotNull
    @Column(name = "parent_job_id", nullable = false)
    private Long parentJobId;

    @Column(name = "parent_job_link", length = Integer.MAX_VALUE)
    private String parentJobLink;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_link", length = Integer.MAX_VALUE)
    private String templateLink;

    @Column(name = "template_name", length = Integer.MAX_VALUE)
    private String templateName;

    @Column(name = "template_type", length = Integer.MAX_VALUE)
    private String templateType;

    @Column(name = "job_awx_id")
    private Long jobAwxId;

    @Column(name = "job_awx_link", length = Integer.MAX_VALUE)
    private String jobAwxLink;

    @Column(name = "job_name", length = Integer.MAX_VALUE)
    private String jobName;

    @Column(name = "job_type", length = Integer.MAX_VALUE)
    private String jobType;

    @Column(name = "job_status", length = Integer.MAX_VALUE)
    private String jobStatus;

    @Column(name = "job_failed")
    private Boolean jobFailed;

    @Column(name = "job_return_completed")
    private Boolean jobReturnCompleted;

    @Column(name = "job_return_message", length = Integer.MAX_VALUE)
    private String jobReturnMessage;

    @Column(name = "job_return_data", length = Integer.MAX_VALUE)
    private String jobReturnData;

    @Column(name = "job_org", length = Integer.MAX_VALUE)
    private String jobOrg;

    @Column(name = "job_started")
    private OffsetDateTime jobStarted;

    @Column(name = "job_finished")
    private OffsetDateTime jobFinished;

    @Column(name = "job_duration", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration jobDuration;

    @Column(name = "job_depth")
    private Integer jobDepth;

    @Column(name = "job_error_message", length = Integer.MAX_VALUE)
    private String jobErrorMessage;

    @Column(name = "job_extra_vars", length = Integer.MAX_VALUE)
    private String jobExtraVars;

    @ColumnDefault("false")
    @Column(name = "job_is_root_cause")
    private Boolean jobIsRootCause = false;

    @Column(name = "job_artifacts", length = Integer.MAX_VALUE)
    private String jobArtifacts;
}