package de.muenchen.mcmp.job;

import de.muenchen.mcmp.job.incident.JobIncidentSummary;
import de.muenchen.mcmp.job.node.JobNodeHierarchy;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    Page<Job> findByAwxJobId(long awxJobId, Pageable pageable);

    @Query(value = """
    SELECT j.*
    FROM cmp.server s
    JOIN cmp.job j on s.id = j.server_id
    WHERE s.id = :serverId
    AND (
        :isAdmin
        OR :isReadonly
        OR (:hasLinuxRole AND s.role_linux)
        OR (:hasWindowsRole AND s.role_windows)
        OR (:hasOracleRole AND s.role_oracle)
        OR (:hasNonOracleRole AND s.role_non_oracle)
        OR :hasSecurityRole
        OR :hasOperatorRole
        OR :hasNetworkRole
        OR EXISTS (
            SELECT 1
            FROM cmp.server_assignment sa
            JOIN cmp.appservice a ON sa.appservice_id = a.id
            JOIN cmp."group" g ON a.change_group_id = g.id
            JOIN cmp.group_membership gm ON g.id = gm.group_id
            JOIN cmp.user u ON gm.user_id = u.id
            WHERE sa.server_id = :serverId
            AND u.username = :username
        )
    )
""", nativeQuery = true)
    List<Job> findByServerId(@Param("serverId") Long serverId,
                             @Param("username") String username,
                             @Param("isAdmin") boolean isAdmin,
                             @Param("isReadonly") boolean isReadonly,
                             @Param("hasLinuxRole") boolean hasLinuxRole,
                             @Param("hasWindowsRole") boolean hasWindowsRole,
                             @Param("hasOracleRole") boolean hasOracleRole,
                             @Param("hasNonOracleRole") boolean hasNonOracleRole,
                             @Param("hasSecurityRole") boolean hasSecurityRole,
                             @Param("hasOperatorRole") boolean hasOperatorRole,
                             @Param("hasNetworkRole") boolean hasNetworkRole);

    List<Job> findByUser_Username(String username);

    long countByUser_UsernameAndNotificationTrue(String username);

    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.notification = false WHERE j.user.username = :username AND j.notification = true")
    void resetNotificationsByUsername(@Param("username") String username);

    @Query(value = "SELECT j.hostname FROM cmp.job j WHERE j.server_installation = true AND j.status NOT IN ('successful', 'failed', 'error', 'canceled')", nativeQuery = true)
    List<String> findHostnamesForActiveServerInstallations();

    @Query(value = """
        WITH percentiles AS (
            SELECT
                action_identifier AS action,
                change_required,
                PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM awx_duration)) AS p10,
                PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM awx_duration)) AS p90
            FROM cmp.job
            WHERE created_at >= :startDate AND created_at < :endDate
            GROUP BY action_identifier, change_required
        ),
             base AS (
                 SELECT
                     j.action_identifier AS action,
                     j.change_required,
                     j.change_status,
                     j.awx_status,
                     EXTRACT(EPOCH FROM j.awx_duration) AS awx_secs,
                     p.p10,
                     p.p90
                 FROM cmp.job j
                          JOIN percentiles p ON p.action = j.action_identifier AND p.change_required = j.change_required
                 WHERE j.created_at >= :startDate AND j.created_at < :endDate
             ),
             grouped AS (
                 SELECT
                     action,
                     change_required,
                     COUNT(CASE WHEN change_status = 'rejected' THEN 1 END) AS change_status_rejected,
                     COUNT(CASE WHEN change_status = 'canceled' THEN 1 END) AS change_status_canceled,
                     COUNT(CASE WHEN change_status = 'skipped'  THEN 1 END) AS change_status_skipped,
                     COUNT(CASE WHEN change_status = 'approved' THEN 1 END) AS change_status_approved,
                     COUNT(CASE WHEN change_status = 'failed'   THEN 1 END) AS change_status_failed,
                     COUNT(*)                                                AS total_jobs,
                     COUNT(CASE WHEN awx_status = 'failed'      THEN 1 END) AS awx_status_failed,
                     COUNT(CASE WHEN awx_status = 'successful'  THEN 1 END) AS awx_status_successful,
                     ROUND(MIN(awx_secs))                                    AS awx_duration_min,
                     ROUND(MAX(awx_secs))                                    AS awx_duration_max,
                     ROUND(AVG(awx_secs))                                    AS awx_duration_mittelwert,
                     ROUND(AVG(awx_secs) FILTER (WHERE awx_secs BETWEEN p10 AND p90)) AS awx_duration_trimmed_avg
                 FROM base
                 GROUP BY action, change_required
             ),
             total_percentiles AS (
                 SELECT
                             PERCENTILE_CONT(0.10) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM awx_duration)) AS p10,
                             PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM awx_duration)) AS p90
                 FROM cmp.job
                 WHERE created_at >= :startDate AND created_at < :endDate
                   AND awx_duration IS NOT NULL
             )
        SELECT *, 0 AS sort_order FROM grouped
        UNION ALL
        SELECT
            'SUMME',
            NULL,
            SUM(change_status_rejected),
            SUM(change_status_canceled),
            SUM(change_status_skipped),
            SUM(change_status_approved),
            SUM(change_status_failed),
            SUM(total_jobs),
            SUM(awx_status_failed),
            SUM(awx_status_successful),
            null,
            null,
            null,
            null,
            1
        FROM grouped
        ORDER BY sort_order, action, change_required;
        """, nativeQuery = true)
    List<JobStatistics> findJobStatistics(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(value = """
        SELECT j.action_title as actionTitle, j.change_start_date as changeStartDate, j.change_number as changeNumber, j.change_link as changeLink
        FROM cmp.job j
        WHERE j.server_id = :serverId
        AND j.status NOT IN ('successful', 'failed', 'error', 'canceled', 'rejected')
        AND j.action_identifier LIKE '%GREEN_IT%'
    """, nativeQuery = true)
    List<ActiveGreenItJob> findActiveGreenItJobsByServerId(@Param("serverId") Long serverId);

    @Query(value = """
        SELECT
           a.id as appServiceId,
           a.name as appServiceName,
           ca.api_description as awxApiDescription,
           j.awx_end_date as awxEndDate,
           j.awx_error as awxError,
           j.awx_extra_vars as awxExtraVars,
           j.awx_job_enabled as awxJobEnabled,
           j.awx_job_id as awxJobId,
           j.awx_job_link as awxJobLink,
           j.awx_start_date as awxStartDate,
           j.awx_status as awxStatus,
           j.awx_template_id as awxTemplateId,
           j.awx_template_type as awxTemplateType,
           j.awx_variables as awxVariables,
           j.change_error as changeError,
           j.change_link as changeLink,
           j.change_number as changeNumber,
           j.change_required as changeRequired,
           j.change_start_date as changeStartDate,
           j.change_status as changeStatus,
           j.created_at as createdAt,
           j.description as description,
           j.hostname as hostname,
           j.id as id,
           j.ip as ip,
           j.quickdiscovery as quickdiscovery,
           j.quickdiscovery_ci_name as quickdiscoveryCiName,
           j.quickdiscovery_ci_sysid as quickdiscoveryCiSysid,
           j.quickdiscovery_error as quickdiscoveryError,
           j.quickdiscovery_error_counter as quickdiscoveryErrorCounter,
           j.quickdiscovery_status as quickdiscoveryStatus,
           s.id as serverId,
           j.server_installation as serverInstallation,
           s.name as serverName,
           cs.api_description as snowApiDescription,
           j.status as status,
           j.tagging_error as taggingError,
           j.tagging_status as taggingStatus,
           j.title as title,
           u.name as userName,
           j.tagging_status as taggingStatus,
           j.title as title,
           u.name as userName,
           j.awx_job_error_message as awxJobErrorMessage,
           j.awx_job_artifacts as awxJobArtifacts,
           j.awx_job_org as awxJobOrg,
           j.awx_job_return_data as awxJobReturnData,
           j.awx_job_return_message as awxJobReturnMessage,
           j.awx_job_return_completed as awxJobReturnCompleted,
           j.awx_job_failed as awxJobFailed,
           j.awx_job_status as awxJobStatus,
           EXTRACT(EPOCH FROM j.awx_duration)::bigint as awxJobDuration,
           j.awx_template_link as awxTemplateLink
    FROM cmp.job j
    LEFT JOIN cmp.user u ON j.user_id = u.id
    LEFT JOIN cmp.server s ON j.server_id = s.id
    LEFT JOIN cmp.config_snow cs on j.snow_id = cs.id
    LEFT JOIN cmp.config_awx ca on j.awx_id = ca.id
    LEFT JOIN cmp.appservice a on j.appservice_id = a.id
    WHERE (CAST(:jobId AS bigint) IS NULL OR j.id = :jobId)
    AND (CAST(:awxJobId AS bigint) IS NULL OR j.awx_job_id = :awxJobId)
    AND (CAST(:createdFrom AS timestamp with time zone) IS NULL OR j.created_at >= :createdFrom)
    AND (CAST(:createdTo AS timestamp with time zone) IS NULL OR j.created_at < :createdTo)
    AND (CAST(:changeStartFrom AS timestamp with time zone) IS NULL OR j.change_start_date >= :changeStartFrom)
    AND (CAST(:changeStartTo AS timestamp with time zone) IS NULL OR j.change_start_date < :changeStartTo)
    AND (CAST(:userId AS bigint) IS NULL OR j.user_id = :userId)
    AND (CAST(:serverId AS bigint) IS NULL OR j.server_id = :serverId)
    AND (CAST(:appserviceId AS bigint) IS NULL OR j.appservice_id = :appserviceId)
    AND (CAST(:actionIdentifier AS text) IS NULL OR j.action_identifier = :actionIdentifier)
    AND (CAST(:statusIdentifier AS text) IS NULL OR CAST(j.status AS text) = :statusIdentifier)
    AND (CAST(:awxVariables AS text) IS NULL OR j.awx_variables ILIKE CONCAT('%', :awxVariables, '%'))
    """,
            countQuery = """
    SELECT COUNT(*)
    FROM cmp.job j
    WHERE (CAST(:jobId AS bigint) IS NULL OR j.id = :jobId)
    AND (CAST(:awxJobId AS bigint) IS NULL OR j.awx_job_id = :awxJobId)
    AND (CAST(:createdFrom AS timestamp with time zone) IS NULL OR j.created_at >= :createdFrom)
    AND (CAST(:createdTo AS timestamp with time zone) IS NULL OR j.created_at < :createdTo)
    AND (CAST(:changeStartFrom AS timestamp with time zone) IS NULL OR j.change_start_date >= :changeStartFrom)
    AND (CAST(:changeStartTo AS timestamp with time zone) IS NULL OR j.change_start_date < :changeStartTo)
    AND (CAST(:userId AS bigint) IS NULL OR j.user_id = :userId)
    AND (CAST(:serverId AS bigint) IS NULL OR j.server_id = :serverId)
    AND (CAST(:appserviceId AS bigint) IS NULL OR j.appservice_id = :appserviceId)
    AND (CAST(:actionIdentifier AS text) IS NULL OR j.action_identifier = :actionIdentifier)
    AND (CAST(:statusIdentifier AS text) IS NULL OR CAST(j.status AS text) = :statusIdentifier)
    AND (CAST(:awxVariables AS text) IS NULL OR j.awx_variables ILIKE CONCAT('%', :awxVariables, '%'))
    """, nativeQuery = true)
    Page<JobListComplete> findAllJobsComplete(Pageable pageable,
                                              @Param("jobId") Long jobId,
                                              @Param("awxJobId") Long awxJobId,
                                              @Param("createdFrom") Instant createdFrom,
                                              @Param("createdTo") Instant createdTo,
                                              @Param("changeStartFrom") Instant changeStartFrom,
                                              @Param("changeStartTo") Instant changeStartTo,
                                              @Param("userId") Long userId,
                                              @Param("serverId") Long serverId,
                                              @Param("appserviceId") Long appserviceId,
                                              @Param("actionIdentifier") String actionIdentifier,
                                              @Param("statusIdentifier") String statusIdentifier,
                                              @Param("awxVariables") String awxVariables);

    @Query(value = """
        SELECT
           a.id as appServiceId,
           a.name as appServiceName,
           ca.api_description as awxApiDescription,
           j.awx_end_date as awxEndDate,
           j.awx_error as awxError,
           j.awx_extra_vars as awxExtraVars,
           j.awx_job_enabled as awxJobEnabled,
           j.awx_job_id as awxJobId,
           j.awx_job_link as awxJobLink,
           j.awx_start_date as awxStartDate,
           j.awx_status as awxStatus,
           j.awx_template_id as awxTemplateId,
           j.awx_template_type as awxTemplateType,
           j.awx_variables as awxVariables,
           j.change_error as changeError,
           j.change_link as changeLink,
           j.change_number as changeNumber,
           j.change_required as changeRequired,
           j.change_start_date as changeStartDate,
           j.change_status as changeStatus,
           j.created_at as createdAt,
           j.description as description,
           j.hostname as hostname,
           j.id as id,
           j.ip as ip,
           j.quickdiscovery as quickdiscovery,
           j.quickdiscovery_ci_name as quickdiscoveryCiName,
           j.quickdiscovery_ci_sysid as quickdiscoveryCiSysid,
           j.quickdiscovery_error as quickdiscoveryError,
           j.quickdiscovery_error_counter as quickdiscoveryErrorCounter,
           j.quickdiscovery_status as quickdiscoveryStatus,
           s.id as serverId,
           j.server_installation as serverInstallation,
           s.name as serverName,
           cs.api_description as snowApiDescription,
           j.status as status,
           j.tagging_error as taggingError,
           j.tagging_status as taggingStatus,
           j.title as title,
           u.name as userName,
           j.awx_job_error_message as awxJobErrorMessage,
           j.awx_job_artifacts as awxJobArtifacts,
           j.awx_job_org as awxJobOrg,
           j.awx_job_return_data as awxJobReturnData,
           j.awx_job_return_message as awxJobReturnMessage,
           j.awx_job_return_completed as awxJobReturnCompleted,
           j.awx_job_failed as awxJobFailed,
           j.awx_job_status as awxJobStatus,
           EXTRACT(EPOCH FROM j.awx_duration)::bigint as awxJobDuration,
           j.awx_template_link as awxTemplateLink
    FROM cmp.job j
    LEFT JOIN cmp.user u ON j.user_id = u.id
    LEFT JOIN cmp.server s ON j.server_id = s.id
    LEFT JOIN cmp.config_snow cs on j.snow_id = cs.id
    LEFT JOIN cmp.config_awx ca on j.awx_id = ca.id
    LEFT JOIN cmp.appservice a on j.appservice_id = a.id
    WHERE (CAST(:userId AS bigint) IS NULL OR j.user_id = :userId)
    AND (CAST(:serverId AS bigint) IS NULL OR j.server_id = :serverId)
    """,
            countQuery = """
    SELECT COUNT(*)
    FROM cmp.job j
    WHERE (CAST(:userId AS bigint) IS NULL OR j.user_id = :userId)
    AND (CAST(:serverId AS bigint) IS NULL OR j.server_id = :serverId)
    """, nativeQuery = true)
    Page<JobListBasic> findAllJobsBasic(Pageable pageable,
                                              @Param("userId") Long userId,
                                              @Param("serverId") Long serverId);

    @Query(value = "SELECT DISTINCT j.action_identifier FROM cmp.job j ORDER BY j.action_identifier ASC", nativeQuery = true)
    List<String> findAllActionIdentifiers();

    @Query(value = "SELECT enumlabel FROM pg_enum WHERE enumtypid = (SELECT oid FROM pg_type WHERE typname = 'job_status' AND typnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'cmp')) ORDER BY enumlabel ASC", nativeQuery = true)
    List<String> findAllStatusIdentifiers();

    @Query(value = """
            SELECT
                0 as jobDepth,
                COALESCE(NULLIF(j.awx_job_name, ''), j.awx_job_id::text) as nodeAlias,
                j.awx_job_link as jobAwxLink,
                j.awx_template_type::text as templateType,
                j.awx_job_org as jobOrg,
                j.awx_job_status as jobStatus,
                j.awx_job_failed as jobFailed,
                j.awx_job_return_completed as jobReturnCompleted,
                j.awx_job_return_message as jobReturnMessage,
                j.awx_job_return_data as jobReturnData,
                j.awx_start_date as jobStarted,
                j.awx_end_date as jobFinished,
                EXTRACT(EPOCH FROM j.awx_duration)::bigint as jobDuration,
                j.awx_extra_vars as jobExtraVars,
                j.awx_artifacts as jobArtifacts,
                false as jobIsRootCause,
                j.awx_job_error_message as jobErrorMessage,
                ca.api_description as awxDescription,
                j.awx_job_id as jobId,
                j.awx_template_id as templateId,
                j.awx_template_link as templateLink,
                j.awx_status as awxStatus,
                j.awx_variables as awxLaunchRequest
            FROM cmp.job j
            LEFT JOIN cmp.config_awx ca on j.awx_id = ca.id
            WHERE j.id = :jobId
            UNION ALL
            (SELECT
               job_depth as jobDepth,
               node_alias as nodeAlias,
               job_awx_link as jobAwxLink,
               template_type as templateType,
               job_org as jobOrg,
               job_status as jobStatus,
               job_failed as jobFailed,
               job_return_completed as jobReturnCompleted,
               job_return_message as jobReturnMessage,
               job_return_data as jobReturnData,
               job_started as jobStarted,
               job_finished as jobFinished,
               EXTRACT(EPOCH FROM job_duration)::bigint as jobDuration,
               job_extra_vars as jobExtraVars,
               job_artifacts as jobArtifacts,
               job_is_root_cause as jobIsRootCause,
               job_error_message as jobErrorMessage,
               null as awxDescription,
               job_awx_id as jobId,
               template_id as templateId,
               template_link as templateLink,
               null as awxStatus,
               null as awxLaunchRequest
            FROM cmp.job_nodes
            WHERE job_id = :jobId
            ORDER BY id)
            """, nativeQuery = true)
    List<JobNodeHierarchy> findJobHierarchy(@Param("jobId") Long jobId);

    @Query(value = "SELECT j.status as status, j.sourceType as sourceType, j.incidentNumber as incidentNumber, j.incidentLink as incidentLink, j.success as success, j.closeNotes as closeNotes " +
            "FROM JobIncident j WHERE j.jobId = :jobId ORDER BY j.id DESC")
    List<JobIncidentSummary> findIncidentSummariesByJobId(@Param("jobId") Long jobId);
}