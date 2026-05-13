package de.muenchen.mcmp.job;

public interface JobStatistics {
    String getAction();

    Boolean getChangeRequired();

    Long getChangeStatusRejected();

    Long getChangeStatusCanceled();

    Long getChangeStatusSkipped();

    Long getChangeStatusApproved();

    Long getChangeStatusFailed();

    Long getTotalJobs();

    Long getAwxStatusFailed();

    Long getAwxStatusSuccessful();

    Double getAwxDurationMin();

    Double getAwxDurationMax();

    Double getAwxDurationMittelwert();

    Double getAwxDurationTrimmedAvg();

    Integer getSortOrder();
}
