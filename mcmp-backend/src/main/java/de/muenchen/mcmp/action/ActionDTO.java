package de.muenchen.mcmp.action;

import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.snowConfig.SnowConfig;
import lombok.Builder;

@Builder
public record ActionDTO(
    SnowConfig snowConfig,
    AwxConfig awxConfig,
    String identifier,
    String title,
    String description,
    String comment,
    String errorTitle,
    String errorDescription,
    String executionTitle,
    String executionDescription,
    String successTitle,
    String successDescription,
    Boolean enabled,
    Boolean quickdiscovery,
    Boolean serverInstallation,
    Boolean changeRequired,
    String changeType,
    String changeAction,
    String changeTemplate,
    String changeJustification,
    String changeImplementationPlan,
    String changeRiskImpactAnalysis,
    String changeBackoutPlan,
    Boolean awxJobEnabled,
    String awxTemplateType,
    Integer awxTemplateId,
    Integer awxInventoryId,
    String awxCredentials,
    String awxJobType,
    String awxLimit,
    String awxJobTags,
    String awxSkipTags,
    String awxExtraVars,
    String awxScmBranch,
    Integer awxVerbosity,
    Integer awxTimeout,
    Integer awxForks,
    Integer awxJobSliceCount,
    Integer awxExecutionEnvironment,
    String awxInstanceGroups,
    String awxLabels,
    Integer awxEstimatedRuntime,
    Boolean isLowPriority,
    Boolean createIncidents
) {}

