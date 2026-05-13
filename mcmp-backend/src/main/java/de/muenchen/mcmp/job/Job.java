package de.muenchen.mcmp.job;

import de.muenchen.mcmp.action.AwxTemplateType;
import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.converter.*;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.snowConfig.SnowConfig;
import de.muenchen.mcmp.types.*;
import de.muenchen.mcmp.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "job")
public class Job extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snow_id", nullable = true)
    @ToString.Exclude
    private SnowConfig snowConfig;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "awx_id", nullable = true)
    @ToString.Exclude
    private AwxConfig awxConfig;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    @ToString.Exclude
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appservice_id")
    @ToString.Exclude
    private Appservice appService;

    @Convert(converter = JobStatusConverter.class)
    @Column(name = "status", nullable = false)
    @ColumnTransformer(write = "?::job_status")
    private JobStatus status = JobStatus.NEW;

    @Column(name = "action_identifier", nullable = false, unique = true, columnDefinition = "text")
    private String actionIdentifier;

    @Column(name = "action_title", columnDefinition = "text")
    private String actionTitle;

    @Column(name = "action_description", columnDefinition = "text")
    private String actionDescription;

    @Column(name = "action_execution_title", columnDefinition = "text")
    private String actionExecutionTitle;

    @Column(name = "action_execution_description", columnDefinition = "text")
    private String actionExecutionDescription;

    @Column(name = "action_success_title", columnDefinition = "text")
    private String actionSuccessTitle;

    @Column(name = "action_success_description", columnDefinition = "text")
    private String actionSuccessDescription;

    @Column(name = "action_error_title", columnDefinition = "text")
    private String actionErrorTitle;

    @Column(name = "action_error_description", columnDefinition = "text")
    private String actionErrorDescription;

    @ColumnDefault("false")
    @Column(name = "quickdiscovery", nullable = false)
    private Boolean quickdiscovery = false;

    @ColumnDefault("false")
    @Column(name = "server_installation", nullable = false)
    private Boolean serverInstallation = false;

    @ColumnDefault("true")
    @Column(name = "change_required", nullable = false)
    private Boolean changeRequired = true;

    @Column(name = "change_type", columnDefinition = "text")
    private String changeType;

    @Column(name = "change_template", columnDefinition = "text")
    private String changeTemplate;

    @Column(name = "change_justification", columnDefinition = "text")
    private String changeJustification;

    @Column(name = "change_implementation_plan", columnDefinition = "text")
    private String changeImplementationPlan;

    @Column(name = "change_risk_impact_analysis", columnDefinition = "text")
    private String changeRiskImpactAnalysis;

    @Column(name = "change_backout_plan", columnDefinition = "text")
    private String changeBackoutPlan;

    @Column(name = "change_start_date")
    private Instant changeStartDate;

    @Column(name = "change_end_date")
    private Instant changeEndDate;

    @Convert(converter = ChangeStatusConverter.class)
    @Column(name = "change_status", nullable = false)
    @ColumnTransformer(write = "?::change_status")
    private ChangeStatus changeStatus = ChangeStatus.NEW;

    @ColumnDefault("true")
    @Column(name = "awx_job_enabled", nullable = false)
    private Boolean awxJobEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "awx_template_type", nullable = false)
    @ColumnTransformer(write = "?::awx_template_type")
    private AwxTemplateType awxTemplateType;

    @Column(name = "awx_template_id")
    private Long awxTemplateId;

    @Column(name = "awx_inventory_id")
    private Integer awxInventoryId;

    @Column(name = "awx_credentials", columnDefinition = "text")
    private String awxCredentials;

    @Column(name = "awx_job_type", columnDefinition = "text")
    private String awxJobType;

    @Column(name = "awx_limit", columnDefinition = "text")
    private String awxLimit;

    @Column(name = "awx_job_tags", columnDefinition = "text")
    private String awxJobTags;

    @Column(name = "awx_skip_tags", columnDefinition = "text")
    private String awxSkipTags;

    @Column(name = "awx_extra_vars", columnDefinition = "text")
    private String awxExtraVars;

    @Column(name = "awx_scm_branch", columnDefinition = "text")
    private String awxScmBranch;

    @Column(name = "awx_verbosity")
    private Integer awxVerbosity;

    @Column(name = "awx_timeout")
    private Integer awxTimeout;

    @Column(name = "awx_forks")
    private Integer awxForks;

    @Column(name = "awx_job_slice_count")
    private Integer awxJobSliceCount;

    @Column(name = "awx_execution_environment")
    private Integer awxExecutionEnvironment;

    @Column(name = "awx_instance_groups", columnDefinition = "text")
    private String awxInstanceGroups;

    @Column(name = "awx_labels", columnDefinition = "text")
    private String awxLabels;

    @Column(name = "awx_estimated_runtime")
    private Integer awxEstimatedRuntime;

    @Column(name = "awx_variables", columnDefinition = "text")
    private String awxVariables;

    @Column(name = "awx_artifacts", columnDefinition = "text")
    private String awxArtifacts;

    @Convert(converter = AwxStatusConverter.class)
    @Column(name = "awx_status", nullable = false)
    @ColumnTransformer(write = "?::awx_status")
    private AwxStatus awxStatus = AwxStatus.NEW;

    @Column(name = "awx_next_status_check")
    private Instant awxNextStatusCheck;

    @Convert(converter = QuickdiscoveryStatusConverter.class)
    @Column(name = "quickdiscovery_status", nullable = false)
    @ColumnTransformer(write = "?::quickdiscovery_status")
    private QuickdiscoveryStatus quickdiscoveryStatus = QuickdiscoveryStatus.NEW;

    @Convert(converter = TaggingStatusConverter.class)
    @Column(name = "tagging_status", nullable = false)
    @ColumnTransformer(write = "?::tagging_status")
    private TaggingStatus taggingStatus = TaggingStatus.NEW;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "change_number", columnDefinition = "text")
    private String changeNumber;

    @Column(name = "change_sys_id", columnDefinition = "text")
    private String changeSysId;

    @Column(name = "change_link", columnDefinition = "text")
    private String changeLink;

    @Column(name = "awx_job_id")
    private Long awxJobId;

    @Column(name = "awx_job_link", columnDefinition = "text")
    private String awxJobLink;

    @Column(name = "awx_error", columnDefinition = "text")
    private String awxError;

    @Column(name = "hostname", columnDefinition = "text")
    private String hostname;

    @Column(name = "ip", columnDefinition = "text")
    private String ip;

    @ColumnDefault("false")
    @Column(name = "notification", nullable = false)
    private Boolean notification = false;

    @Column(name = "quickdiscovery_error", columnDefinition = "text")
    private String quickdiscoveryError;

    @Column(name = "quickdiscovery_ci_sysid", columnDefinition = "text")
    private String quickdiscoveryCiSysid;

    @Column(name = "quickdiscovery_ci_name", columnDefinition = "text")
    private String quickdiscoveryCiName;

    @Column(name = "change_error", columnDefinition = "text")
    private String changeError;

    @Column(name = "change_action", columnDefinition = "text")
    private String changeAction;

    @Column(name = "tagging_error", columnDefinition = "text")
    private String taggingError;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "quickdiscovery_error_counter", nullable = false)
    private Integer quickdiscoveryErrorCounter = 0;

    @Column(name = "awx_start_date")
    private OffsetDateTime awxStartDate;

    @Column(name = "awx_end_date")
    private OffsetDateTime awxEndDate;

    @Column(name = "job_end_date")
    private OffsetDateTime jobEndDate;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "non_postgres", nullable = false)
    private Boolean nonPostgres = false;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "non_oss", nullable = false)
    private Boolean nonOss = false;

    @Column(name = "non_postgres_justification", columnDefinition = "text")
    private String nonPostgresJustification;

    @Convert(converter = EmailStatusConverter.class)
    @Column(name = "non_postgres_email_status", nullable = false)
    @ColumnTransformer(write = "?::email_status")
    private EmailStatus nonPostgresEmailStatus = EmailStatus.NEW;

    @Column(name = "awx_duration", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration awxDuration;

    @Column(name = "job_duration", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration jobDuration;

    @Convert(converter = DbTypeConverter.class)
    @Column(name = "target_database_type", columnDefinition = "db_type")
    @ColumnTransformer(write = "?::db_type")
    private DbType targetDatabaseType;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_low_priority", nullable = false)
    private Boolean isLowPriority = false;

    @Column(name = "awx_job_name", length = Integer.MAX_VALUE)
    private String awxJobName;

    @Column(name = "awx_job_failed")
    private Boolean awxJobFailed;

    @Column(name = "awx_job_return_completed")
    private Boolean awxJobReturnCompleted;

    @Column(name = "awx_job_return_message", length = Integer.MAX_VALUE)
    private String awxJobReturnMessage;

    @Column(name = "awx_job_return_data", length = Integer.MAX_VALUE)
    private String awxJobReturnData;

    @Column(name = "awx_job_org", length = Integer.MAX_VALUE)
    private String awxJobOrg;

    @Column(name = "awx_job_error_message", length = Integer.MAX_VALUE)
    private String awxJobErrorMessage;

    @Column(name = "awx_template_link", length = Integer.MAX_VALUE)
    private String awxTemplateLink;

    @Column(name = "awx_template_name", length = Integer.MAX_VALUE)
    private String awxTemplateName;

    @Column(name = "awx_job_status", length = Integer.MAX_VALUE)
    private String awxJobStatus;

    @Column(name = "awx_job_artifacts", length = Integer.MAX_VALUE)
    private String awxJobArtifacts;

    @ColumnDefault("true")
    @Column(name = "create_incidents")
    private Boolean createIncidents = true;
}
