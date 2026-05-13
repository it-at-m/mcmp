package de.muenchen.mcmp.job.node;

import java.time.Instant;

public interface JobNodeHierarchy {
    Integer getJobDepth();
    String getNodeAlias();
    String getJobAwxLink();
    String getTemplateType();
    String getJobOrg();
    String getJobStatus();
    Boolean getJobFailed();
    Boolean getJobReturnCompleted();
    String getJobReturnMessage();
    String getJobReturnData();
    Instant getJobStarted();
    Instant getJobFinished();
    Long getJobDuration();
    String getJobExtraVars();
    String getJobArtifacts();
    Boolean getJobIsRootCause();
    String getJobErrorMessage();
    String getAwxDescription();
    Long getJobId();
    Long getTemplateId();
    String getTemplateLink();
    String getAwxStatus();
    String getAwxLaunchRequest();
}
