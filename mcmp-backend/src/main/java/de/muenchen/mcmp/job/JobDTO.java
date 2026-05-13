package de.muenchen.mcmp.job;

import de.muenchen.mcmp.action.AwxTemplateType;
import de.muenchen.mcmp.awxConfig.AwxConfig;
import de.muenchen.mcmp.snowConfig.SnowConfig;
import de.muenchen.mcmp.types.*;
import de.muenchen.mcmp.user.User;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

public class JobDTO {
    public Long id;
    public Date createdAt;
    public SnowConfig snowConfig;
    public AwxConfig awxConfig;
    public User user;
    public String serverName;

    public JobStatus status;
    public String actionIdentifier;
    public String actionTitle;
    public String actionDescription;
    public String actionExecutionTitle;
    public String actionExecutionDescription;
    public String actionSuccessTitle;
    public String actionSuccessDescription;
    public String actionErrorTitle;
    public String actionErrorDescription;

    public Boolean quickdiscovery;
    public Boolean serverInstallation;
    public Boolean changeRequired;

    public String changeType;
    public String changeAction;
    public String changeTemplate;
    public Instant changeStartDate;
    public Instant changeEndDate;
    public ChangeStatus changeStatus;
    public String changeJustification;
    public String changeImplementationPlan;
    public String changeRiskImpactAnalysis;
    public String changeBackoutPlan;
    public Boolean awxJobEnabled;
    public AwxTemplateType awxTemplateType;
    public Long awxTemplateId;
    public Integer awxInventoryId;
    public String awxCredentials;
    public String awxJobType;
    public String awxLimit;
    public String awxJobTags;
    public String awxSkipTags;
    public String awxExtraVars;
    public String awxScmBranch;
    public Integer awxVerbosity;
    public Integer awxTimeout;
    public Integer awxForks;
    public Integer awxJobSliceCount;
    public Integer awxExecutionEnvironment;
    public String awxInstanceGroups;
    public String awxLabels;
    public Integer awxEstimatedRuntime;
    public String awxVariables;
    public String awxArtifacts;
    public AwxStatus awxStatus;
    public Instant awxNextStatusCheck;

    public QuickdiscoveryStatus quickdiscoveryStatus;
    public String quickdiscoveryError;
    public String quickdiscoveryCiSysid;
    public String quickdiscoveryCiName;
    public Integer quickdiscoveryErrorCounter;

    public TaggingStatus taggingStatus;
    public String taggingError;

    public String title;
    public String description;

    public String changeNumber;
    public String changeSysId;
    public String changeLink;
    public String changeError;

    public Long awxJobId;
    public String awxJobLink;
    public String awxError;

    public String hostname;
    public String ip;
    
    public Boolean notification;

    public Boolean nonPostgres;
    public Boolean nonOss;
    public String nonPostgresJustification;
    public EmailStatus nonPostgresEmailStatus;

    public OffsetDateTime awxStartDate;
    public OffsetDateTime awxEndDate;
    public OffsetDateTime jobEndDate;

    public Duration awxDuration;
    public Duration jobDuration;

    public DbType targetDatabaseType;

    public Boolean isLowPriority;

    public String awxJobName;
    public Boolean awxJobFailed;
    public Boolean awxJobReturnCompleted;
    public String awxJobReturnMessage;
    public String awxJobReturnData;
    public String awxJobOrg;
    public String awxJobErrorMessage;
    public String awxTemplateLink;
    public String awxTemplateName;
}