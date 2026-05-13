package de.muenchen.mcmp.job;

import java.time.Instant;

public interface JobListBasic {

    Long getAppServiceId();

    String getAppServiceName();

    String getAwxApiDescription();

    String getAwxError();

    Boolean getAwxJobEnabled();

    Long getAwxJobId();

    String getAwxJobLink();

    String getAwxStatus();

    Instant getAwxEndDate();

    String getAwxExtraVars();

    Instant getAwxStartDate();

    Long getAwxTemplateId();

    String getAwxTemplateType();

    String getAwxVariables();

    String getChangeError();

    String getChangeLink();

    String getChangeNumber();

    Boolean getChangeRequired();

    Instant getChangeStartDate();

    String getChangeStatus();

    Instant getCreatedAt();

    String getDescription();

    String getHostname();

    Long getId();

    String getIp();

    Boolean getQuickdiscovery();

    String getQuickdiscoveryCiName();

    String getQuickdiscoveryCiSysid();

    String getQuickdiscoveryError();

    Integer getQuickdiscoveryErrorCounter();

    String getQuickdiscoveryStatus();

    Long getServerId();

    Boolean getServerInstallation();

    String getServerName();

    String getSnowApiDescription();

    String getStatus();

    String getTaggingError();

    String getTaggingStatus();

    String getTitle();

    String getUserName();

    String getAwxJobErrorMessage();

    String getAwxJobArtifacts();

    String getAwxJobOrg();

    String getAwxJobReturnData();

    String getAwxJobReturnMessage();

    Boolean getAwxJobReturnCompleted();

    Boolean getAwxJobFailed();

    String getAwxJobStatus();

    Long getAwxJobDuration();

    String getAwxTemplateLink();
}