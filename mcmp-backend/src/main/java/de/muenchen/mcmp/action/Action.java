package de.muenchen.mcmp.action;

import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.snowConfig.SnowConfig;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@Table(name = "action")
public class Action extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snow_id")
    @ToString.Exclude
    private SnowConfig snowConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awx_id")
    @ToString.Exclude
    private AwxConfig awxConfig;

    @Column(name = "identifier", nullable = false, unique = true, columnDefinition = "text")
    private String identifier;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "execution_title", columnDefinition = "text")
    private String executionTitle;

    @Column(name = "execution_description", columnDefinition = "text")
    private String executionDescription;

    @Column(name = "success_title", columnDefinition = "text")
    private String successTitle;

    @Column(name = "success_description", columnDefinition = "text")
    private String successDescription;

    @Column(name = "error_title", columnDefinition = "text")
    private String errorTitle;

    @Column(name = "error_description", columnDefinition = "text")
    private String errorDescription;

    @ColumnDefault("false")
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

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

    @ColumnDefault("true")
    @Column(name = "awx_job_enabled", nullable = false)
    private Boolean awxJobEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "awx_template_type", nullable = false)
    @ColumnTransformer(write = "?::awx_template_type")
    private AwxTemplateType awxTemplateType;

    @Column(name = "awx_template_id")
    private Integer awxTemplateId;

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

    @Column(name = "change_action", columnDefinition = "text")
    private String changeAction;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_low_priority", nullable = false)
    private Boolean isLowPriority = false;

    @ColumnDefault("true")
    @Column(name = "create_incidents")
    private Boolean createIncidents = true;

}