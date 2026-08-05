package de.muenchen.mcmp.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.muenchen.mcmp.action.Action;
import de.muenchen.mcmp.action.ActionRepository;
import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceNameAndSysId;
import de.muenchen.mcmp.appservice.AppserviceRepository;
import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.configuration.AppservicesProperties;
import de.muenchen.mcmp.exception.GreenITIllegalArgumentException;
import de.muenchen.mcmp.greenit.rightsizing.GreenItRightsizing;
import de.muenchen.mcmp.greenit.rightsizing.GreenItRightsizingRepository;
import de.muenchen.mcmp.greenit.shutdown.GreenItShutdown;
import de.muenchen.mcmp.greenit.shutdown.GreenItShutdownRepository;
import de.muenchen.mcmp.infoblox.InfobloxService;
import de.muenchen.mcmp.job.incident.JobIncidentSummary;
import de.muenchen.mcmp.job.node.JobNodeHierarchy;
import de.muenchen.mcmp.loadbalancer.LbPool;
import de.muenchen.mcmp.loadbalancer.LbPoolMember;
import de.muenchen.mcmp.loadbalancer.LbVirtualServer;
import de.muenchen.mcmp.loadbalancer.LbVirtualServerPoolRef;
import de.muenchen.mcmp.loadbalancer.LbVirtualServerRepository;
import de.muenchen.mcmp.network.NetworkGroup;
import de.muenchen.mcmp.network.NetworkGroupRepository;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.storage.UnifiedStorageItemDto;
import de.muenchen.mcmp.types.DbType;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
@AllArgsConstructor
@Slf4j
public class JobService {

    private static final String SERVICENOW_BASE_URL = "https://it-services.muenchen.de";
    private static final String MCMP_BASE_URL = "https://mcmp.muenchen.de";
    private static final Map<String, String> SORT_MAPPINGS;

    static {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("id", "id");
        mappings.put("serverName", "s.name");
        mappings.put("userName", "u.name");
        mappings.put("appServiceName", "a.name");
        mappings.put("hostname", "hostname");
        mappings.put("ip", "ip");
        mappings.put("status", "status");
        mappings.put("createdAt", "created_at");
        mappings.put("changeStartDate", "change_start_date");
        mappings.put("awxStatus", "awx_status");
        mappings.put("awxEndDate", "awx_end_date");
        mappings.put("awxStartDate", "awx_start_date");
        mappings.put("changeStatus", "change_status");
        mappings.put("changeError", "change_error");
        mappings.put("taggingStatus", "tagging_status");
        mappings.put("taggingError", "tagging_error");
        mappings.put("quickdiscoveryStatus", "quickdiscovery_status");
        mappings.put("quickdiscoveryError", "quickdiscovery_error");
        mappings.put("title", "title");
        mappings.put("description", "description");
        SORT_MAPPINGS = Collections.unmodifiableMap(mappings);
    }

    private final JobRepository jobRepository;
    private final ActionRepository actionRepository;
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final AppserviceRepository appserviceRepository;
    private final NetworkGroupRepository networkGroupRepository;
    private final LbVirtualServerRepository lbVirtualServerRepository;
    private final GreenItRightsizingRepository greenItRightsizingRepository;
    private final GreenItShutdownRepository greenItShutdownRepository;
    private final AppservicesProperties appservicesProperties;

    private final InfobloxService infobloxService;

    private final JobMapper jobMapper;

    private final ActionToJobMapper actionToJobMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<JobIncidentSummary> getIncidentSummariesByJobId(final Long jobId) {
        return jobRepository.findIncidentSummariesByJobId(jobId);
    }

    public List<JobNodeHierarchy> getJobHierarchy(final Long jobId) {
        return jobRepository.findJobHierarchy(jobId);
    }

    public List<JobStatistics> getJobStatistics(final LocalDate startDate, final LocalDate endDate) {
        return jobRepository.findJobStatistics(startDate, endDate);
    }

    public long getJobNotificationsByUsername(final String username) {
        return jobRepository.countByUser_UsernameAndNotificationTrue(username);
    }

    public void resetJobNotificationsByUsername(final String username)  {
        jobRepository.resetNotificationsByUsername(username);
    }

    public List<String> findAllActionIdentifiers() {
        return jobRepository.findAllActionIdentifiers();
    }

    public List<String> findAllStatusIdentifiers() {
        return jobRepository.findAllStatusIdentifiers();
    }

    public Page<? extends JobListBasic> findAllJobsByRole(final int page, final int itemsPerPage, final String sortBy, final boolean sortDesc, final Long jobId, final Long awxJobId, final Instant createdFrom, final Instant createdTo, final Instant changeStartFrom, final Instant changeStartTo, final Long userId, final Long serverId, final Long appserviceId, final String actionIdentifier, final String statusIdentifier, final String awxVariables) {
        final Sort sort;
        if (sortBy != null && !sortBy.isBlank()) {
            String actualSortBy = SORT_MAPPINGS.getOrDefault(sortBy, sortBy);
            sort = Sort.by(sortDesc ? Sort.Direction.DESC : Sort.Direction.ASC, actualSortBy);
        } else {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }
        int offset = (page - 1) * itemsPerPage;
        final Pageable pageable = new OffsetBasedPageRequest(offset, itemsPerPage, sort);

        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        if (userRoles.hasAdminRole() || userRoles.hasSecurityRole()) {
            return jobRepository.findAllJobsComplete(pageable, jobId, awxJobId, createdFrom, createdTo, changeStartFrom, changeStartTo, userId, serverId, appserviceId, actionIdentifier, statusIdentifier, awxVariables);
        }
        return jobRepository.findAllJobsBasic(pageable, userId, serverId, appserviceId);
    }

    public Page<JobListBasic> getJobsByAppServiceId(
            final int page,
            final int itemsPerPage,
            final String sortBy,
            final boolean sortDesc,
            final Long appServiceId
    ) {
        final Sort sort;
        if (sortBy != null && !sortBy.isBlank()) {
            String actualSortBy = SORT_MAPPINGS.getOrDefault(sortBy, sortBy);
            sort = Sort.by(sortDesc ? Sort.Direction.DESC : Sort.Direction.ASC, actualSortBy);
        } else {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }
        int offset = (page - 1) * itemsPerPage;
        final Pageable pageable = new OffsetBasedPageRequest(offset, itemsPerPage, sort);

        return jobRepository.findJobsByAppServiceId(pageable, appServiceId);
    }

    public void createJob(final String actionIdentifier, Server server, Map<String, Object> awxExtraVars, Map<String, Object> guiVars){
        createJob(actionIdentifier, server, awxExtraVars, guiVars, null, null);
    }

    public void createJob(final String actionIdentifier, Server server, Map<String, Object> awxExtraVars, Map<String, Object> guiVars, String awxJobTags, String awxSkipTags) {
        createJob(actionIdentifier, server, awxExtraVars, guiVars,null, awxJobTags, awxSkipTags);
    }

    public void createJob(final String actionIdentifier, Server server, Map<String, Object> awxExtraVars, Map<String, Object> guiVars, Instant scheduleTime, String awxJobTags, String awxSkipTags) {
        createJob(actionIdentifier, server, awxExtraVars, guiVars,null, awxJobTags, awxSkipTags, null);
    }

    public void createJob(final String actionIdentifier, Server server, Map<String, Object> awxExtraVars, Map<String, Object> guiVars, Instant scheduleTime, String awxJobTags, String awxSkipTags, String awxInventroyId) {
        Action action = getActionOrThrow(actionIdentifier);

        checkActionEnabled(action);

        User user = getCurrentUserOrThrow();
        if (server != null) {
            guiVars.put("server.fqdn", server.getFqdn());
        }
        guiVars.put("user", user.getUsername());

        // write all fields from action to job
        Job job = new Job();

        if(action.getChangeRequired()){
            if (server == null) {
                throw new IllegalArgumentException("Server cannot be null for actions that require change approval.");
            }
            final List<AppserviceNameAndSysId> appservice = appserviceRepository.findAppservicesByServerId(server.getId());
            if (appservice.size() == 1){
                job.setAppService(appserviceRepository.findBySysId((appservice.getFirst().getSysId())));
            } else {
                final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
                if ((userRoles.hasLinuxRole() || userRoles.hasOperatorRole()) && server.getRoleLinux()) {
                    final String appServiceNumber = appservicesProperties.linux();
                    if (appServiceNumber == null || appServiceNumber.isBlank()) {
                        throw new AccessDeniedException("You are not allowed to create a job for this server because the server is assigned to an Application Service, but the linux Application Service is not configured.");
                    }
                    final Appservice linuxAppservice = appserviceRepository.findByNumber(appServiceNumber);
                    if (linuxAppservice == null) {
                        throw new AccessDeniedException("You are not allowed to create a job for this server because the server is assigned to an Application Service, but the linux Application Service is not configured.");
                    }
                    job.setAppService(linuxAppservice);
                } else if (userRoles.hasWindowsRole() && server.getRoleWindows()) {
                    final String appServiceNumber = appservicesProperties.windows();
                    if (appServiceNumber == null || appServiceNumber.isBlank()) {
                        throw new AccessDeniedException("You are not allowed to create a job for this server because the server is assigned to an Application Service, but the windows Application Service is not configured.");
                    }
                    final Appservice windowsAppservice = appserviceRepository.findByNumber(appServiceNumber);
                    if (windowsAppservice == null) {
                        throw new AccessDeniedException("You are not allowed to create a job for this server because the server is assigned to an Application Service, but the windows Application Service is not configured.");
                    }
                    job.setAppService(windowsAppservice);
                } else if (appservice.size() > 1) {
                    throw new AccessDeniedException("You are not allowed to create a job for this server because the server is assigned to more than one Application Service.");
                } else {
                    throw new AccessDeniedException("You are not allowed to create a job for this server because the server is not assigned to an Application Service.");
                }
            }
        }
        actionToJobMapper.updateJobFromAction(action, job);
        String ActionExtraVars = replaceJobPlaceholderVars(awxExtraVars, job, action.getAwxExtraVars(), guiVars);

        if (awxJobTags != null){job.setAwxJobTags((action.getAwxJobTags().isEmpty()) ? awxJobTags : action.getAwxJobTags() + "," + awxJobTags);}
        if (awxSkipTags != null){job.setAwxSkipTags((action.getAwxSkipTags().isEmpty()) ? awxSkipTags : action.getAwxSkipTags() + "," + awxSkipTags);}
        if (awxInventroyId != null){job.setAwxInventoryId(Integer.valueOf(awxInventroyId));}

        if(scheduleTime != null){
            job.setChangeStartDate(scheduleTime);
            job.setChangeEndDate(scheduleTime.plusSeconds(1));
        }

        job.setAwxExtraVars(mergeJsonStrings(ActionExtraVars, serializeParams(awxExtraVars)));
        job.setServer(server);
        job.setUser(user);
        job.setActionIdentifier(action.getIdentifier());
        job.setVersion(1L);
        jobRepository.save(job);
    }

    public void createJobForNewServer(String actionIdentifier, String fqdn, Appservice appservice, Map<String, Object> awxExtraVars, String nonPostgresMailBody, boolean nonOss, DbType targetDbType) {
        Action action = getActionOrThrow(actionIdentifier);

        checkActionEnabled(action);

        User user = getCurrentUserOrThrow();
        Map<String, Object> guiVars = new HashMap<>();
        guiVars.put("server.fqdn", fqdn);
        guiVars.put("user", user.getUsername());

        // write all fields from action to job
        Job job = new Job();
        actionToJobMapper.updateJobFromAction(action, job);
        String ActionExtraVars = replaceJobPlaceholderVars(awxExtraVars, job, action.getAwxExtraVars(), guiVars);

        if (nonPostgresMailBody != null) {
            job.setNonPostgres(true);
            job.setNonOss(nonOss);
            job.setNonPostgresJustification(nonPostgresMailBody);
        } else {
            job.setNonPostgres(false);
            job.setNonOss(false);
        }

        job.setAwxExtraVars(mergeJsonStrings(ActionExtraVars, serializeParams(awxExtraVars)));
        job.setUser(user);
        job.setAppService(appservice);
        job.setActionIdentifier(action.getIdentifier());
        job.setVersion(1L);
        job.setHostname(fqdn);
        job.setTargetDatabaseType(targetDbType);
        jobRepository.save(job);
    }

    private void checkActionEnabled(Action action) {
        if (!action.getAwxConfig().isEnabled()){
            throw new IllegalArgumentException("⚠\uFE0F AWX Wartung ⚠\uFE0F Bitte versuche es später noch einmal.");
        }
        if(!action.getEnabled()) {
            throw new IllegalArgumentException("Die Aktion ist derzeit deaktiviert aus Wartungsgründen, oder weil ein Fehler vorliegt. Bitte versuche es später erneut.");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // VM Operation JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void startServer(final Long serverId, final String start_server_identifier, final Instant scheduleTime) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());

        String cloudType = server.getCloud().getCloudType().toString();
        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
            params.put("vm_powerstate", "powered-on");
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
            params.put("vm_powerstate", "started");
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        if(scheduleTime != null){
            createJob(start_server_identifier, server, params, new HashMap<>(), scheduleTime,null,null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
        } else {
            createJob(start_server_identifier, server, params, new HashMap<>(), null, null, null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
        }
    }

    public void stopServer(final Long serverId, final String stop_server_identifier, final Instant scheduleTime) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());
        params.put("turned_off_at", new SimpleDateFormat("MM/dd/yy HH:mm:ss").format(new Date()));
        params.put("turned_off_by", AuthUtils.getUsername());
        params.put("turned_off_note", "Server wurde durch den Benutzer " + AuthUtils.getUsername() + " in der MCMP gestoppt.");

        String cloudType = server.getCloud().getCloudType().toString();

        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
            params.put("vm_powerstate", "shutdown-guest");
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
            params.put("vm_powerstate", "stopped");
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        if(scheduleTime != null){
            createJob(stop_server_identifier, server, params, new HashMap<>(), scheduleTime,null,null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
        } else {
            createJob(stop_server_identifier, server, params, new HashMap<>(), null, null, null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
        }
    }

    public void restartServer(final Long serverId, final String restart_server_identifier, final Instant scheduleTime) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());
        params.put("turned_off_at", new SimpleDateFormat("MM/dd/yy HH:mm:ss").format(new Date()));
        params.put("turned_off_by", AuthUtils.getUsername());
        params.put("turned_off_note", "Server wurde durch den Benutzer " + AuthUtils.getUsername() + " in der MCMP gerestarted.");

        String cloudType = server.getCloud().getCloudType().toString();

        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
            params.put("vm_powerstate", "reboot-guest");
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
            params.put("vm_powerstate", "restarted");
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        if(scheduleTime != null){
            createJob(restart_server_identifier, server, params, new HashMap<>(), scheduleTime,null,null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
        } else {
            createJob(restart_server_identifier, server, params, new HashMap<>(), null, null, null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
        }
    }

    public void changeCpuRam(final Long serverId, final String change_cpu_ram_identifier, final Integer cpu, final Integer ram, final Instant scheduleTime, final boolean schedulePatchnight) {
        final Server server = getServerOrThrow(serverId);

        final Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());
        params.put("cpus_new", cpu);
        params.put("memory_new", ram);

        String cloudType = server.getCloud().getCloudType().toString();
        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        final Map<String, Object> guiVars = new HashMap<>();
        guiVars.put("scheduled_time", "");
        guiVars.put("cpus_current", server.getNumCpu());
        guiVars.put("memory_current", server.getMemoryMb() == null ? 0 : server.getMemoryMb() / 1024);

        String tag = "run";
        if (scheduleTime != null) {
            final ZoneId zone = ZoneId.of("Europe/Berlin");

            if (schedulePatchnight) {
                tag = "schedule";
                final String date = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(zone).format(scheduleTime);
                guiVars.put("scheduled_time", "Durchführung der Anpassung in der Patchnight am " + date);
            } else {
                final String dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'").withZone(zone).format(scheduleTime);
                guiVars.put("scheduled_time", "Geplante Durchführungszeit: " + dateTime);
            }
        }
        createJob(change_cpu_ram_identifier, server, params, guiVars, scheduleTime, tag, null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
    }

    public void createSnapshot(final Long serverId, final Integer duration, final String description, final boolean withShutdown, final String create_snapshot_identifier) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());
        params.put("state", "present");
        params.put("TeamName", AuthUtils.getUsername().replace('.','_')); //TODO TEAMNAME nicht username (Wird nach ablöse des Snapshot Tools entfernt)
        params.put("time", duration);
        params.put("snapshot_description", description);

        String cloudType = server.getCloud().getCloudType().toString();
        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        String awxSkipTag = null;
        if (!withShutdown){
            awxSkipTag = "with_power_off, with_power_on";
        }

        createJob(create_snapshot_identifier, server, params, new HashMap<>(), null, awxSkipTag, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
    }

    public void deleteSnapshot(final Long serverId, final Long snapshotId, final String snapshotName, final String delete_snapshot_identifier){
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());
        params.put("state", "absent");
        params.put("TeamName", AuthUtils.getUsername()); //TODO TEAMNAME nicht username (Wird nach ablöse des Snapshot Tools entfernt)

        String cloudType = server.getCloud().getCloudType().toString();
        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
            if (snapshotId == null) throw new MissingFormatArgumentException("Snapshot Id must be provided.");
            params.put("snapshot_id", snapshotId);
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
            if (snapshotName == null) throw new MissingFormatArgumentException("Snapshot Name must be provided.");
            params.put("snapshot_name", snapshotName);
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        createJob(delete_snapshot_identifier, server, params, new HashMap<>(), null, null, null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
    }

    public void revertSnapshot(final Long serverId, final Long snapshotId, final String snapshotName, final String reverte_snapshot_identifier){
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("vm_name", server.getName());
        params.put("TeamName", AuthUtils.getUsername()); //TODO TEAMNAME nicht username (Wird nach ablöse des Snapshot Tools entfernt)

        String cloudType = server.getCloud().getCloudType().toString();
        if (cloudType == "VMWARE"){
            params.put("vcenter_uuid", server.getCloud().getServerGui());
            params.put("state", "revert");
            if (snapshotId == null) throw new MissingFormatArgumentException("Snapshot Id must be provided.");
            params.put("snapshot_id", snapshotId);
        }
        else if (cloudType == "PROXMOX") {
            params.put("cluster_name", server.getCluster());
            params.put("state", "rollback");
            if (snapshotName == null) throw new MissingFormatArgumentException("Snapshot Name must be provided.");
            params.put("snapshot_name", snapshotName);
        }
        else {
            throw new IllegalArgumentException("Cloud type " + cloudType + " is not supported.");
        }

        createJob(reverte_snapshot_identifier, server, params, new HashMap<>(), null , null, (server.getCloud().getAwxInventoryId() == null ? null : server.getCloud().getAwxInventoryId().toString() ));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // CHECKMK JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void checkmkSetDowntime(final Long serverId, final String startDate, final Integer duration, final String checkmk_set_downtime_identifier) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("servers", server.getFqdn());
        params.put("start_date", startDate);
        params.put("duration", duration);
        params.put("comment", "Downtime via MCMP");

        createJob(checkmk_set_downtime_identifier, server, params, new HashMap<>());
    }

    public void checkmkServiceDiscovery(final Long serverId, final String action, final String checkmk_service_discovery_identifier) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("hostname", server.getFqdn());
        params.put("discovery_action", action);

        createJob(checkmk_service_discovery_identifier, server, params, new HashMap<>());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // LINUX JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void linuxPatchnightTimeChange(final Long serverId, final String linux_patchnight_change_identifier, final String time) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("time", time);

        createJob(linux_patchnight_change_identifier, server, params, new HashMap<>());
    }

    public void linuxDeleteServer(final Long serverId, final String delete_server_identifier, final Instant scheduleTime) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();

        if(scheduleTime != null){
            createJob(delete_server_identifier, server, params, new HashMap<>(), scheduleTime,null,null);
        } else {
            createJob(delete_server_identifier, server, params, new HashMap<>());
        }
    }

    public void linuxTempRootOneServerOnly(final Long serverId, final String linuxTempRootIdentifier, final String duration, final String otherUsername) {
        User user = getCurrentUserOrThrow();
        Server server = getServerOrThrow(serverId);

        // Normally, canUserEditServer ensures that the server cannot be edited when locked = true.
        // However, with the introduction of the new operator role, canUserEditServer can be overridden,
        // so locked must be checked again here.
        if (server.getLocked()) {
            log.warn("User {} tried to create a job {} for serverId: {} without permission. Server is locked and cannot be modified.", AuthUtils.getUsername(), linuxTempRootIdentifier, serverId);
            throw new AccessDeniedException("Server is locked and cannot be modified.");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("user", user.getUsername());
        params.put("servers", Collections.singletonList(server.getFqdn()));
        params.put("duration", duration);
        if (otherUsername != null && !otherUsername.isBlank()) {
            params.put("delegate_user", otherUsername);
        }
        else{
            params.put("delegate_user", user.getUsername());
        }

        createJob(linuxTempRootIdentifier, server, params, new HashMap<>());
    }

    public void linuxMountpointChange(final Long serverId, final String linux_mountpoint_change_identifier, final String mountpointPath, final Integer newSize, final String logicalName, final String volumeGroup) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("HOSTNAME", server.getName());
        params.put("MOUNTP", mountpointPath);
        params.put("NEW_SIZE", newSize);
        params.put("LOGICAL_NAME", logicalName);
        params.put("VOLUME_GROUP", volumeGroup);

        createJob(linux_mountpoint_change_identifier, server, params, new HashMap<>());
    }

    public void linuxRhelServer(final Map<?, ?> fqdnBuildingBlocks, final String categoryType, final Map<?, ?> serverTypeMap, final int ram, final int cpu, final Long networkGroupId, final Long applicationServiceId, final Map<String, Map<?, ?>> dbParams, String nonPostgresReason, final boolean middlewareUser, final String linux_rhel_server_identifier) {
        Appservice appservice = getAppserviceOrThrow(applicationServiceId);
        Map<String, Object> params = new HashMap<>();
        boolean nonOss = false;

        // checks for what serverType to install
        final Map<String, List<String>> serverTypeNameToAwxInstallParamName = Map.of(
                "Apache", List.of("webserver_install"),
                "Apache/PHP", List.of("webserver_install", "php_install"),
                "Java", List.of("java_install"),
                "Apache/Tomcat", List.of("webserver_install", "tomcat_install", "java_install")
        );
        if (categoryType.equals("App") || categoryType.equals("Mixed")) {
            String labelToCheck = serverTypeMap.get("label").toString();
            if (categoryType.equals("Mixed")) {
                labelToCheck = labelToCheck.split("\\+")[0].trim();
            }
            for (String param : serverTypeNameToAwxInstallParamName.get(labelToCheck)) {
                params.put(param, true);
            }
        }


        // calculate FQDN
        String serverType;
        if (!categoryType.equals("Standard") && !serverTypeMap.get("kenner").toString().isEmpty()) {
            serverType = serverTypeMap.get("kenner").toString();
        } else {
            serverType = "lx";
        }

        if (middlewareUser) {
            params.put("middleware_user_deploy", true);
        }

        String fqdn = infobloxService.calculateFqdn(fqdnBuildingBlocks.get("prefix").toString(), fqdnBuildingBlocks.get("application").toString(), serverType,
                applicationServiceId, (Integer) fqdnBuildingBlocks.get("customNumber"), fqdnBuildingBlocks.get("domain").toString(), null);

        DbType targetDbType = null;
        if ((categoryType.equals("DB") || categoryType.equals("Mixed")) && dbParams != null) {
            nonOss = handleDBParams(dbParams, params);

            final String dbType = dbParams.get("mariaPostgresMysqlOracle").get("db_type").toString();
            final Optional<DbType> dbTypeOpt = DbTypeMapper.parse(dbType);
            final String dbTypeNormalized = dbTypeOpt.map(DbType::normalizedName).orElse(DbTypeMapper.UNKNOWN);
            targetDbType = dbTypeOpt.orElse(null);

            if (nonPostgresReason != null) {
                final AuthUtils.AuthUserInfo currentUserInfo = AuthUtils.getCurrentUserInfo();
                nonPostgresReason = formatNonPostgresJustification(
                        dbTypeNormalized,
                        appservice.getName(),
                        appservice.getSysId(),
                        appservice.getId(),
                        fqdn,
                        currentUserInfo.name(),
                        currentUserInfo.username(),
                        currentUserInfo.department(),
                        currentUserInfo.email(),
                        nonPostgresReason
                );
            }
        }

        params.put("fqdn", fqdn);
        params.put("requester_username", AuthUtils.getUsername());
        params.put("memory_mb", ram * 1024);
        params.put("cpu", cpu);
        params.put("networkgroup", getNetworkGroupOrThrow(networkGroupId).getName());
        params.put("ansible_user_future_use", "");
        params.put("application_service", appservice.getName());
        params.put("application_service_environment", appservice.getEnvironment());
        params.put("is_microsegmented", appservice.getCswEnforced());
        params.put("application_service_number", appservice.getNumber());
        params.put("os_name", "rhel");
        if (Objects.equals(linux_rhel_server_identifier, "LINUX_RHEL9_SERVER")) {
            params.put("os_version", "9");
        } else {
            params.put("os_version", "10");
        }

        createJobForNewServer(linux_rhel_server_identifier, fqdn, appservice, params, nonPostgresReason, nonOss, targetDbType);
    }

    public void linuxCustomRhelServer(final Map<String, Object> awxExtraVars, final String linux_custom_rhel_server_identifier) {
        String fqdn = awxExtraVars.get("fqdn").toString();
        Appservice appservice = getAppserviceOrThrow(Long.valueOf(awxExtraVars.get("appservice_id").toString()));
        awxExtraVars.remove("appservice_id");

        createJobForNewServer(linux_custom_rhel_server_identifier, fqdn, appservice, awxExtraVars, null, false, null);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // WINDOWS JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void windowsDeleteServer(final Long serverId, final String delete_server_identifier, final Instant scheduleTime) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("db_type", server.getDbMssql() ? "mssql" : "none");
        params.put("application_service_environment",
                server.getAppservices().stream()
                        .findFirst()
                        .map(Appservice::getEnvironment)
                        .map(Enum::name)
                        .orElse("none"));

        if(scheduleTime != null){
            createJob(delete_server_identifier, server, params, new HashMap<>(), scheduleTime,null,null);
        } else {
            createJob(delete_server_identifier, server, params, new HashMap<>());
        }
    }

    public void windowsTempAdminOneServerOnly(final Long serverId, final String windows_temp_admin_identifier, final String otherUsername) {
        User user = getCurrentUserOrThrow();
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("servers", Collections.singletonList(server.getName()));
        if (otherUsername != null && !otherUsername.isBlank()) {
            params.put("delegate_user", otherUsername);
        }
        else{
            params.put("delegate_user", user.getUsername());
        }

        createJob(windows_temp_admin_identifier, server, params, new HashMap<>());
    }

    public void windowsPartitionChange(final Long serverId, final String windows_partition_change_identifier, final String partition, final Integer newSize) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        String[] fqdnParts = server.getFqdn().split("\\.");
        params.put("shortname", fqdnParts[0]);
        params.put("ad_domain", fqdnParts[fqdnParts.length - 2] + "." + fqdnParts[fqdnParts.length - 1]);
        params.put("drive_letter", partition.charAt(0));
        params.put("new_disk_size_gb", newSize);
        params.put("umgebung", server.getCloud().getName().split("\\.")[0].replace("vcenter",""));

        createJob(windows_partition_change_identifier, server, params, new HashMap<>());
    }

    public void windowsMaintenaceMode(final Long serverId, final String maintenanceModeEnd, final String win_maintenace_mode_identifier) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("wartungsmodus_ende", maintenanceModeEnd);
        params.put("requester_username",AuthUtils.getUsername());
        params.put("fqdn", server.getFqdn());
        String[] fqdnParts = server.getFqdn().split("\\.");
        params.put("shortname", fqdnParts[0]);
        params.put("ad_domain", fqdnParts[fqdnParts.length - 2] + "." + fqdnParts[fqdnParts.length - 1]);
        params.put("vm_name", server.getName());
        params.put("vcenter_hostname", server.getCloud().getName());

        createJob(win_maintenace_mode_identifier, server, params, new HashMap<>());
    }

    public void windowsMaintenaceModeEnd(final Long serverId, final String win_maintenace_mode_end_identifier) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("servers", server.getFqdn());

        createJob(win_maintenace_mode_end_identifier, server, params, new HashMap<>());
    }

    public void windowsServer(final Map<?, ?> fqdnBuildingBlocks, final Map<?, ?> serverTypeMap, final String categoryType, final int ram, final int cpu, final List<Map<String, Object>> disks, final Long networkGroupId, final Long applicationServiceId, final String osVersion, String nonPostgresReason, final Map<String, Map<?, ?>> dbParams, final String order_windows_server_identifier) {
        Appservice appservice = getAppserviceOrThrow(applicationServiceId);
        NetworkGroup networkGroup = getNetworkGroupOrThrow(networkGroupId);
        boolean nonOss = false;

        if (appservice.getBusinessServiceNumbers() == null || appservice.getBusinessServiceNumbers().isEmpty()) {
            throw new IllegalArgumentException("The Applicationservice must be assigned to at least one Business Service to order a Windows Server.");
        }

        List<String> bsnList = List.of(appservice.getBusinessServiceNumbers().split(","));

        Map<String, Object> params = new HashMap<>();

        // calculate FQDN
        String serverType;
        if (serverTypeMap != null && !serverTypeMap.get("kenner").toString().isEmpty()) {
            serverType = serverTypeMap.get("kenner").toString();
        } else {
            serverType = "wi";
        }

        if (categoryType.equals("DB")) {
            Map<?, ?> dbTypeParams = dbParams.get("mssql");
            params.put("mssql_install", true);
            params.put("db_type", "mssql");
            params.put("mssql_serversort", dbTypeParams.get("mssql_serversort").toString());
        }

        String fqdn = infobloxService.calculateFqdn(fqdnBuildingBlocks.get("prefix").toString(), fqdnBuildingBlocks.get("application").toString(), serverType,
                applicationServiceId, (Integer) fqdnBuildingBlocks.get("customNumber"), fqdnBuildingBlocks.get("domain").toString(), null);

        final String adDomain = Objects.equals(fqdnBuildingBlocks.get("domain").toString(), "srv.muenchen.de") ?
                "muenchen.de" : Objects.equals(fqdnBuildingBlocks.get("domain").toString(), "testlhm.muenchen.de") ? "testlhm.de" : "NOTVALID";

        DbType targetDbType = null;

        if ((categoryType.equals("DB") || categoryType.equals("Mixed")) && dbParams != null) {
            nonOss = true;
            targetDbType = DbType.MSSQL;
            final AuthUtils.AuthUserInfo currentUserInfo = AuthUtils.getCurrentUserInfo();
            if (nonPostgresReason != null) {
                nonPostgresReason = formatNonPostgresJustification(
                        "mssql",
                        appservice.getName(),
                        appservice.getSysId(),
                        appservice.getId(),
                        fqdn,
                        currentUserInfo.name(),
                        currentUserInfo.username(),
                        currentUserInfo.department(),
                        currentUserInfo.email(),
                        nonPostgresReason
                );
            }
        }

        params.put("fqdn", fqdn);
        params.put("service", bsnList);
        params.put("service_owner", List.of(appservice.getOwnedBy().getUsername(), appservice.getServiceOwnerDelegate() != null ? appservice.getServiceOwnerDelegate().getUsername() : ""));
        params.put("bsn_desc", appservice.getName());
        params.put("application_service_environment", appservice.getEnvironment());
        params.put("application_service_number", appservice.getNumber());
        params.put("application_service", appservice.getName());
        params.put("requester_username", AuthUtils.getUsername());
        params.put("is_microsegmented", appservice.getCswEnforced());
        params.put("check_mk_loc", "mia");
        params.put("foreman_location", "MIA");
        params.put("funktionsgruppe", networkGroup.getName());
        params.put("ad_domain", adDomain);
        params.put("operatingsystem", osVersion);
        if (osVersion.equals("Windows Server 2025")) {
            params.put("image", "w2025tmpl_latest");
        } else if (osVersion.equals("Windows Server 2022")) {
            params.put("image", "w2022tmpl_latest");
        } else {
            throw new IllegalArgumentException("Unsupported operating system version: " + osVersion);
        }
        params.put("cpus", cpu);
        params.put("memory_mb", ram * 1024);
        if (categoryType.equals("Standard")) {
            params.put("disk_size_0", disks.getFirst().get("size"));
        } else if (categoryType.equals("DB")) {
            params.put("disk_size_0", 100);
            for (int i = 0; i < disks.size(); i++) {
                params.put("disk_size_" + (i + 1), disks.get(i).get("size"));
            }
        }

        createJobForNewServer(order_windows_server_identifier, fqdn, appservice, params, nonPostgresReason, nonOss, targetDbType);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DB JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void createOracleBackup(final Long serverId, final String flag, final String oracle_backup_identifier) {
        Server server = getServerOrThrow(serverId);

        Map<String, Object> params = new HashMap<>();
        params.put("server", server.getFqdn());
        params.put("user_name", AuthUtils.getUsername());
        params.put("flag", flag);
        params.put("RAC", "N");

        createJob(oracle_backup_identifier, server, params, new HashMap<>());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ANSIBLE JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void ansibleUserAdd(final String account_name, final Long serverId, final String ansible_user_add_identifier) {
        final Server server = getServerOrThrow(serverId);
        final User user = getCurrentUserOrThrow();
        if (!JobUtils.isItmDepartment(user.getDepartment())) {
            throw new AccessDeniedException("Only users from ITM are allowed to perform this action.");
        }

        // divide servers into linux and windows servers
        List<Server> linuxServers = new ArrayList<>();
        if (server.getRoleLinux()) {
            linuxServers.add(server);
        }

        List<Server> windowsServers = new ArrayList<>();
        if (server.getRoleWindows()) {
            windowsServers.add(server);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("account_name", account_name);
        params.put("linux_servers", linuxServers.stream().map(Server::getFqdn).toList());
        params.put("windows_servers", windowsServers.stream().map(Server::getFqdn).toList());
        params.put("requester_username", AuthUtils.getUsername());
        params.put("requester_organization", JobUtils.removeItmPrefix(user.getDepartment()));

        createJob(ansible_user_add_identifier, server, params, new HashMap<>());
    }

    public void ansibleUserRemove(final String account_name, final String ansible_user_remove_identifier) {
        final User user = getCurrentUserOrThrow();
        final String[] departmentParts = user.getDepartment().split("-");
        if (!JobUtils.isItmDepartment(user.getDepartment())) {
            throw new AccessDeniedException("Only users from ITM are allowed to perform this action.");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("account_name", account_name);
        params.put("requester_username", AuthUtils.getUsername());
        params.put("requester_organization", JobUtils.removeItmPrefix(user.getDepartment()));

        createJob(ansible_user_remove_identifier, null, params, new HashMap<>());
    }


    // -----------------------------------------------------------------------------------------------------------------
    // LOADBALANCER JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public void loadbalancerF5(final Map<String, Object> awxExtraVars, final String loadbalancer_f5_identifier) {

        final Map<String, Object> appserviceMap = (Map<String, Object>) awxExtraVars.get("appservice");
        final Long appserviceId = Long.valueOf(appserviceMap.get("id").toString());
        final Appservice appservice = getAppserviceOrThrow(appserviceId);

        // rename x_forwarded_for to x-forwarded-for because frontend Typescript doesn't support dashes in vars
        List<Map<String, Object>> listenerMaps = (List<Map<String, Object>>) awxExtraVars.get("listener");
        for (Map<String, Object> listenerMap : listenerMaps) {
            if (listenerMap.containsKey("x_forwarded_for")) {
                listenerMap.put("x-forwarded-for", listenerMap.get("x_forwarded_for"));
                listenerMap.remove("x_forwarded_for");
            }
        }

        // convert member ports to individual members with port, but avoid duplicates (same ip:port)
        List<Map<String, Object>> serverPools = (List<Map<String, Object>>) awxExtraVars.get("server_pools");
        List<Map<String, Object>> memberList = (List<Map<String, Object>>) serverPools.getFirst().get("member");

        // collect existing ip:port pairs from members that already have a single 'port'
        final Set<String> existingIpPort = new HashSet<>();
        for (Map<String, Object> m : memberList) {
            if (m.containsKey("port") && m.get("ip") != null && m.get("port") != null) {
                existingIpPort.add(m.get("ip").toString() + ":" + m.get("port").toString());
            }
        }

        List<Map<String, Object>> newMemberList = new ArrayList<>();
        for (Map<String, Object> member : memberList) {
            if (!member.containsKey("ports")) continue;
            String name = member.get("name").toString();
            String ip = member.get("ip").toString();
            List<?> ports = (List<?>) member.get("ports");
            if (ports == null) continue;
            for (Object portObj : ports) {
                if (portObj == null) continue;
                String portStr = portObj.toString();
                String key = ip + ":" + portStr;
                // only add if this ip:port combination is not already present
                if (existingIpPort.add(key)) {
                    Map<String, Object> newMember = new HashMap<>();
                    newMember.put("name", name);
                    newMember.put("ip", ip);
                    // try to keep numeric type if possible
                    try {
                        newMember.put("port", Integer.parseInt(portStr));
                    } catch (NumberFormatException e) {
                        newMember.put("port", portStr);
                    }
                    newMemberList.add(newMember);
                }
            }
        }

        memberList.addAll(newMemberList);
        // remove all members that still have key 'ports' instead of 'port'
        memberList.removeIf(member -> member.containsKey("ports"));

        Map<String, Object> params = new HashMap<>(awxExtraVars);
        params.put("requester_username", AuthUtils.getUsername());
        params.put("organisational_unit", AuthUtils.getCurrentUserInfo().department());
        params.put("application_service", appservice.getName());
        params.put("application_service_number", appservice.getNumber());
        params.remove("appservice");
        Map<String, String> ibs342map = new HashMap<>();
        switch (appservice.getUsedFor()) {
            case "Production" -> ibs342map.put("environment", "prod");
            case "Test", "Development" -> ibs342map.put("environment", "test");
            case "Training" -> ibs342map.put("environment", "schulung");
            default -> {}
        }
        params.put("ibs342", ibs342map);
        params.put("csw_enforced", appservice.getCswEnforced());

        createJobForNewServer(loadbalancer_f5_identifier, awxExtraVars.get("dns").toString(), appservice, params, null, false, null);
    }

    public void loadbalancerF5ChangePoolMembers(final Long lbVirtualServerId, final String poolName,
                                                 final List<Map<String, Object>> added, final List<Map<String, Object>> removed,
                                                 final String identifier) {
        final LbVirtualServer lvs = lbVirtualServerRepository.findById(lbVirtualServerId)
                .orElseThrow(() -> new NoSuchElementException("Loadbalancer not found: " + lbVirtualServerId));

        if (lvs.getAppservices().size() != 1) {
            throw new IllegalStateException("Pool members can only be changed for loadbalancers assigned to exactly one application service.");
        }
        final Appservice appservice = lvs.getAppservices().iterator().next();

        final LbPool pool = lvs.getPoolRefs().stream()
                .map(LbVirtualServerPoolRef::getPool)
                .filter(p -> poolName.equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Pool " + poolName + " not found on this loadbalancer."));

        final Set<String> removedKeys = removed.stream()
                .map(r -> r.get("ip").toString().trim() + ":" + r.get("port").toString().trim())
                .collect(Collectors.toSet());

        final List<LbPoolMember> currentMembers = pool.getMembers() != null ? pool.getMembers() : List.of();
        final List<Map<String, Object>> finalMembers = new ArrayList<>();
        final Set<String> memberKeys = new HashSet<>();
        final Set<String> matchedRemovedKeys = new HashSet<>();
        for (final LbPoolMember member : currentMembers) {
            final String key = member.getIp() + ":" + member.getPort();
            if (removedKeys.contains(key)) {
                matchedRemovedKeys.add(key);
                continue;
            }
            memberKeys.add(key);
            final Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("address", member.getIp());
            memberMap.put("port", member.getPort());
            memberMap.put("name", member.getServer() != null ? member.getServer().getName() : member.getIp());
            finalMembers.add(memberMap);
        }
        if (!matchedRemovedKeys.equals(removedKeys)) {
            throw new IllegalArgumentException("One or more members to remove were not found in the pool.");
        }

        for (final Map<String, Object> addedMember : added) {
            final long addedServerId = Long.parseLong(addedMember.get("server_id").toString());
            final int port = Integer.parseInt(addedMember.get("port").toString());
            final Server server = getServerOrThrow(addedServerId);
            final String ip = server.getGuestToolsIpAddress();
            if (ip == null || ip.isBlank()) {
                throw new IllegalArgumentException("Server " + server.getName() + " has no IP address and cannot be added as a pool member.");
            }
            final String key = ip + ":" + port;
            if (!memberKeys.add(key)) {
                throw new IllegalArgumentException("Member " + key + " is already part of the pool.");
            }
            final Map<String, Object> memberMap = new HashMap<>();
            memberMap.put("address", ip);
            memberMap.put("port", port);
            memberMap.put("name", server.getName());
            finalMembers.add(memberMap);
        }

        final Map<String, Object> params = new HashMap<>();
        params.put("requester_username", AuthUtils.getUsername());
        params.put("organisational_unit", AuthUtils.getCurrentUserInfo().department());
        params.put("application_service", appservice.getName());
        params.put("application_service_number", appservice.getNumber());
        params.put("csw_enforced", appservice.getCswEnforced());
        final Map<String, String> ibs342map = new HashMap<>();
        switch (appservice.getUsedFor()) {
            case "Production" -> ibs342map.put("environment", "prod");
            case "Test", "Development" -> ibs342map.put("environment", "test");
            case "Training" -> ibs342map.put("environment", "schulung");
            default -> {}
        }
        params.put("ibs342", ibs342map);
        params.put("pool_name", poolName);
        final Map<String, Object> poolParam = new HashMap<>();
        poolParam.put("members", finalMembers);
        params.put("pool", poolParam);

        createJobForNewServer(identifier, poolName, appservice, params, null, false, null);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // GREEN-IT JOBs
    // -----------------------------------------------------------------------------------------------------------------
    public Long createGreenItRightsizingJob(final GreenItRightsizing greenItRightsizing) {
        if (greenItRightsizing == null) {
            throw new GreenITIllegalArgumentException("GreenItRightsizing must not be null.");
        }
        if (greenItRightsizing.getServer() == null) {
            throw new GreenITIllegalArgumentException("GreenItRightsizing must have a server.");
        }
        if (greenItRightsizing.getAppservice() == null) {
            throw new GreenITIllegalArgumentException("GreenItRightsizing must have an appservice.");
        }

        final Action action = getActionOrThrow(JobController.GREEN_IT_VMWARE_RIGHTSIZE);
        checkActionEnabled(action);
        final Server server = getServerOrThrow(greenItRightsizing.getServer().getId());

        final Instant startTimeInstant = greenItRightsizing.getStartTime()
                .atZoneSameInstant(ZoneId.of("Europe/Berlin"))
                .toInstant();

        final Map<String, Object> guiVars = new HashMap<>();
        guiVars.put("server.fqdn", server.getFqdn());
        guiVars.put("server.name", server.getName());
        guiVars.put("cpus_current", greenItRightsizing.getCpuCurrent());
        guiVars.put("memory_current", greenItRightsizing.getRamCurrent() / 1024);

        final Map<String, Object> awxExtraVars = new HashMap<>();
        awxExtraVars.put("vcenter_uuid", server.getCloud().getServerGui());
        awxExtraVars.put("memory_new", greenItRightsizing.getRamNew() / 1024);
        awxExtraVars.put("vm_name", server.getName());
        awxExtraVars.put("cpus_new", greenItRightsizing.getCpuNew());

        final Job job = new Job();
        actionToJobMapper.updateJobFromAction(action, job);
        final String actionExtraVars = replaceJobPlaceholderVars(awxExtraVars, job, action.getAwxExtraVars(), guiVars);
        job.setChangeStartDate(startTimeInstant);
        job.setChangeEndDate(startTimeInstant.plusSeconds(1));
        job.setAwxExtraVars(mergeJsonStrings(actionExtraVars, serializeParams(awxExtraVars)));
        job.setServer(server);
        job.setAppService(greenItRightsizing.getAppservice());
        job.setActionIdentifier(action.getIdentifier());
        jobRepository.saveAndFlush(job);
        greenItRightsizing.setJob(job);
        greenItRightsizingRepository.saveAndFlush(greenItRightsizing);
        return job.getId();
    }

    public Long createGreenItShutdownJob(final GreenItShutdown greenItShutdownServer) {
        if (greenItShutdownServer == null) {
            throw new GreenITIllegalArgumentException("GreenItShutdown must not be null.");
        }
        if (greenItShutdownServer.getServer() == null) {
            throw new GreenITIllegalArgumentException("GreenItShutdown must have a server.");
        }
        if (greenItShutdownServer.getAppservice() == null) {
            throw new GreenITIllegalArgumentException("GreenItShutdown must have an appservice.");
        }
        final Action action = getActionOrThrow(JobController.GREEN_IT_VMWARE_SHUTDOWN);
        checkActionEnabled(action);
        final Server server = getServerOrThrow(greenItShutdownServer.getServer().getId());



        final Instant startTimeInstant = greenItShutdownServer.getStartTime()
                .atZoneSameInstant(ZoneId.of("Europe/Berlin"))
                .toInstant();
        final String formattedStartTime = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm:ss")
                .withZone(ZoneId.of("Europe/Berlin"))
                .format(startTimeInstant);


        final Map<String, Object> guiVars = new HashMap<>();
        guiVars.put("server.fqdn", server.getFqdn());
        guiVars.put("server.name", server.getName());

        final Map<String, Object> awxExtraVars = new HashMap<>();
        awxExtraVars.put("vm_name", server.getName());
        awxExtraVars.put("vcenter_uuid", server.getCloud().getServerGui());
        awxExtraVars.put("vm_instance_uuid", server.getInstanceUuid());
        awxExtraVars.put("turned_off_at", formattedStartTime);
        awxExtraVars.put("turned_off_note", "${CHANGE}");

        final Job job = new Job();
        actionToJobMapper.updateJobFromAction(action, job);
        final String actionExtraVars = replaceJobPlaceholderVars(awxExtraVars, job, action.getAwxExtraVars(), guiVars);
        job.setChangeStartDate(startTimeInstant);
        job.setChangeEndDate(startTimeInstant.plusSeconds(1));
        job.setAwxExtraVars(mergeJsonStrings(actionExtraVars, serializeParams(awxExtraVars)));
        job.setServer(server);
        job.setAppService(greenItShutdownServer.getAppservice());
        job.setActionIdentifier(action.getIdentifier());
        jobRepository.saveAndFlush(job);
        greenItShutdownServer.setJob(job);
        greenItShutdownRepository.saveAndFlush(greenItShutdownServer);
        return job.getId();
    }


    // -----------------------------------------------------------------------------------------------------------------
    // STORAGE JOBs
    // -----------------------------------------------------------------------------------------------------------------

    public void storageModifyNfs(UnifiedStorageItemDto nfsItem, int newSize, int newSnapshotPercentage) {
        storageModifyShare(nfsItem.getNfs_mount_path(), JobController.STORAGE_MODIFY_NFS, newSize, newSnapshotPercentage);
    }

    public void storageModifyCifs(UnifiedStorageItemDto cifsItem, int newSize, int newSnapshotPercentage) {
        storageModifyShare(cifsItem.getCifs_mount_path(), JobController.STORAGE_MODIFY_CIFS, newSize, newSnapshotPercentage);
    }

//    private void storageDeleteNfs(UnifiedStorageItemDto nfsItem) {
//        Map<String, Object> params = new HashMap<>();
//        createJob(JobController.STORAGE_DELETE_NFS, null, params, new HashMap<>());
//    }
//
//    private void storageDeleteCifs(UnifiedStorageItemDto cifsItem) {
//        Map<String, Object> params = new HashMap<>();
//        createJob(JobController.STORAGE_DELETE_CIFS, null, params, new HashMap<>());
//    }

    public void storageCreateSnapshotNfs(UnifiedStorageItemDto nfsItem, String usage) {
        storageCreateSnapshotShare(nfsItem.getNfs_mount_path(), "STORAGE_CREATE_SNAPSHOT_NFS", usage);
    }

    public void storageCreateSnapshotCifs(UnifiedStorageItemDto cifsItem, String usage) {
        storageCreateSnapshotShare(cifsItem.getCifs_mount_path(), "STORAGE_CREATE_SNAPSHOT_CIFS", usage);
    }

    public void storageDeleteSnapshotNfs(UnifiedStorageItemDto nfsItem, String snapshotName) {
        storageDeleteSnapshotShare(nfsItem.getNfs_mount_path(), "STORAGE_DELETE_SNAPSHOT_NFS", snapshotName);
    }

    public void storageDeleteSnapshotCifs(UnifiedStorageItemDto cifsItem, String snapshotName) {
        storageDeleteSnapshotShare(cifsItem.getCifs_mount_path(), "STORAGE_DELETE_SNAPSHOT_CIFS", snapshotName);
    }

    private void storageModifyShare(String mountPath, String jobIdentifier, int newSize, int newSnapshotPercentage) {
        Map<String, Object> params = new HashMap<>();
        if (Objects.equals(jobIdentifier, JobController.STORAGE_MODIFY_NFS)) {
            params.put("MCMPSTRG_MOUNTPATH", mountPath);
        } else {
            params.put("MCMPSTRG_UNCPATH", mountPath);
        }
        params.put("MCMPSTRG_NEW_SIZE_IN_GB", newSize);
        params.put("MCMPSTRG_NEW_SNAPRESERVE_PERCENT", newSnapshotPercentage);

        createJob(jobIdentifier, null, params, new HashMap<>());
    }

    public void storageChangeSnapshotPolicyNfs(UnifiedStorageItemDto nfsItem, String newPolicy) {
        storageChangeSnapshotPolicyShare(nfsItem.getNfs_mount_path(), "STORAGE_CHANGE_SNAPSHOT_POLICY_NFS", newPolicy);
    }

    public void storageChangeSnapshotPolicyCifs(UnifiedStorageItemDto cifsItem, String newPolicy) {
        storageChangeSnapshotPolicyShare(cifsItem.getCifs_mount_path(), "STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS", newPolicy);
    }

    public void storageChangeNfsExportPolicy(UnifiedStorageItemDto nfsItem, String fqdn, String permission) {
        Map<String, Object> params = new HashMap<>();
        params.put("MCMPSTRG_MOUNTPATH", nfsItem.getNfs_mount_path());
        params.put("MCMPSTRG_FQDN", fqdn);
        params.put("MCMPSTRG_PERMISSION", permission);
        createJob(JobController.STORAGE_CHANGE_NFS_EXPORT_POLICY, null, params, new HashMap<>());
    }

    public void storageChangeCifsPermissions(UnifiedStorageItemDto cifsItem, String ad, String permission) {
        Map<String, Object> params = new HashMap<>();
        params.put("MCMPSTRG_AD_OBJECT", ad);
        params.put("MCMPSTRG_PERMISSION", permission);
        params.put("MCMPSTRG_UNCPATH", cifsItem.getCifs_mount_path());

        createJob(JobController.STORAGE_CHANGE_CIFS_PERMISSIONS, null, params, new HashMap<>());
    }

    private void storageChangeSnapshotPolicyShare(String mountPath, String jobIdentifier, String newPolicy) {
        Map<String, Object> params = new HashMap<>();
        if (Objects.equals(jobIdentifier, "STORAGE_CHANGE_SNAPSHOT_POLICY_NFS")) {
            params.put("MCMPSTRG_MOUNTPATH", mountPath);
        } else {
            params.put("MCMPSTRG_UNCPATH", mountPath);
        }
        params.put("MCMPSTRG_NEW_SNAPSHOT_POLICY", newPolicy);

        createJob(jobIdentifier, null, params, new HashMap<>());
    }

    private void storageCreateSnapshotShare(String mountPath, String jobIdentifier, String usage) {
        Map<String, Object> params = new HashMap<>();
        if (Objects.equals(jobIdentifier, "STORAGE_CREATE_SNAPSHOT_NFS")) {
            params.put("MCMPSTRG_MOUNTPATH", mountPath);
        } else {
            params.put("MCMPSTRG_UNCPATH", mountPath);
        }
        params.put("MCMPSTRG_USAGE", usage);

        createJob(jobIdentifier, null, params, new HashMap<>());
    }

    private void storageDeleteSnapshotShare(String mountPath, String jobIdentifier, String snapshotName) {
        Map<String, Object> params = new HashMap<>();
        if (Objects.equals(jobIdentifier, "STORAGE_DELETE_SNAPSHOT_NFS")) {
            params.put("MCMPSTRG_MOUNTPATH", mountPath);
        } else {
            params.put("MCMPSTRG_UNCPATH", mountPath);
        }
        params.put("MCMPSTRG_SNAPSHOTNAME", snapshotName);

        createJob(jobIdentifier, null, params, new HashMap<>());
    }


    // -----------------------------------------------------------------------------------------------------------------
    // PRIVATE HELPER METHODS
    // -----------------------------------------------------------------------------------------------------------------

    private boolean handleDBParams(Map<String, Map<?, ?>> dbParams, Map<String, Object> params) {
        if (dbParams.containsKey("mariaPostgresMysqlOracle")) {
            Map<String, List<String>> acceptedDBVersions = new HashMap<>();
            acceptedDBVersions.put("postgresql", List.of("16", "17", "18"));
            acceptedDBVersions.put("mysql", List.of("8.4"));
            acceptedDBVersions.put("mariadb", List.of("11.4"));
            acceptedDBVersions.put("oracle", List.of("19c"));
            Map<String, List<String>> acceptedCharsets = new HashMap<>();
            acceptedCharsets.put("mysql", List.of("utf8mb4", "utf8", "latin1"));
            acceptedCharsets.put("mariadb", List.of("utf8mb4", "utf8", "latin1"));
            acceptedCharsets.put("oracle", List.of("AL32UTF8", "WE8MSWIN1252", "WE8ISO8859P1", "WE8ISO8859P15", "WE8ISO8859P9"));
            Map<?, ?> dbTypeParams = dbParams.get("mariaPostgresMysqlOracle");
            if (!dbTypeParams.get("db_type").toString().matches("mariadb|postgresql|mysql|oracle")) {
                throw new IllegalArgumentException("The database type is invalid.");
            }
            if (!acceptedDBVersions.get(dbTypeParams.get("db_type").toString()).contains(dbTypeParams.get("db_version").toString())) {
                throw new IllegalArgumentException("The database version is invalid.");
            }
            if (!dbTypeParams.get("customer_db_name").toString().matches("^[a-zA-Z0-9_-]{1,20}$") ||
                    !dbTypeParams.get("customer_db_user").toString().matches("^[a-zA-Z0-9_-]{1,20}$")) {
                throw new IllegalArgumentException("The database name or username is invalid.");
            }

            params.put("db_type", dbTypeParams.get("db_type").toString());
            params.put("db_version", dbTypeParams.get("db_version").toString());
            params.put("customer_email", AuthUtils.getUsername() + "@muenchen.de");
            params.put("customer_db_name", dbTypeParams.get("customer_db_name").toString());
            params.put("customer_db_user", dbTypeParams.get("customer_db_user").toString());
            params.put("customer_db_schema", "");
            params.put("customer_db_charset", "");
            if(dbTypeParams.get("db_type").toString().equals("postgresql")) {
                if (!dbTypeParams.get("customer_db_schema").toString().matches("^[a-zA-Z0-9_-]{1,20}$")) {
                    throw new IllegalArgumentException("The database schema is invalid.");
                }
                params.put("customer_db_schema", dbTypeParams.get("customer_db_schema").toString());
                if(dbTypeParams.get("postgis") != null && !((List<?>) dbTypeParams.get("postgis")).isEmpty()) {
                    params.put("postgis", dbTypeParams.get("postgis"));
                } else {
                    params.put("postgis", new ArrayList<>());
                }
            }
            if (dbTypeParams.get("db_type").toString().equals("mysql") || dbTypeParams.get("db_type").toString().equals("mariadb") || dbTypeParams.get("db_type").toString().equals("oracle")) {
                if (!acceptedCharsets.get(dbTypeParams.get("db_type").toString()).contains(dbTypeParams.get("customer_db_charset").toString())) {
                    throw new IllegalArgumentException("The database charset is invalid.");
                }
                params.put("customer_db_charset", dbTypeParams.get("customer_db_charset").toString());
            }
            params.put("conn_dima_admin", dbTypeParams.get("conn_dima_admin"));
            params.put("conn_cap", dbTypeParams.get("conn_cap"));
            params.put("conn_app_server", dbTypeParams.get("conn_app_server"));
            if (dbTypeParams.get("customer_app_server") != null && !((List<?>) dbTypeParams.get("customer_app_server")).isEmpty()) {
                params.put("customer_app_server", dbTypeParams.get("customer_app_server"));
            } else {
                params.put("customer_app_server", new ArrayList<>());
            }
            if(dbTypeParams.get("db_type").toString().equals("oracle")) {
                if (Integer.parseInt(dbTypeParams.get("oracle_datasize").toString()) < 15 ||
                        Integer.parseInt(dbTypeParams.get("oracle_datasize").toString()) > 500) {
                    throw new IllegalArgumentException("The Oracle database size is invalid.");
                }
                params.put("oracle_datasize", Integer.parseInt(dbTypeParams.get("oracle_datasize").toString()));
            }
            final String dbType = dbTypeParams.get("db_type").toString();
            return dbType.equals("oracle") || dbType.equals("mssql");
        } else {
            throw new IllegalArgumentException("Database parameters are missing.");
        }
    }

    private Action getActionOrThrow(String identifier) {
        Action action = actionRepository.findByIdentifier(identifier);
        if (action == null) {
            throw new NoSuchElementException(
                    "Action with identifier " + identifier + " does not exist."
            );
        }
        return action;
    }

    private Server getServerOrThrow(Long serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Server not found with id " + serverId));
    }

    private User getCurrentUserOrThrow() {
        User user = userRepository.findByUsername(AuthUtils.getUsername());
        if (user == null) {
            throw new NoSuchElementException(
                    "User does not exist."
            );
        }
        return user;
    }

    private Appservice getAppserviceOrThrow(Long applicationServiceId){
        return appserviceRepository.findById(applicationServiceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Application Service not found with id " + applicationServiceId));
    }

    private NetworkGroup getNetworkGroupOrThrow(Long networkGroupId){
        return networkGroupRepository.findById(networkGroupId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Network Group not found with id " + networkGroupId));
    }


    String serializeParams(Map<String, Object> params) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(params);
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Serialisieren der Parameter", e);
        }
    }

    String mergeJsonStrings(String... jsonStrings) {
        try {
            Map<String, Object> mergedMap = new HashMap<>();
            for (String json : jsonStrings) {
                if (json != null && !json.isBlank()) {
                    Map<String, Object> map = objectMapper.readValue(
                            json, new TypeReference<>() {
                            }
                    );
                    mergedMap.putAll(map);
                }
            }
            return serializeParams(mergedMap);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON String of AwxExtraVars is malformed (String Placeholders in Quotes? \"${some_var}\")", e);
        }
    }

    String getEndDateFromStartDateAndDurationInMinutes(String startDate, Integer durationInMinutes) {
        if (startDate == null || startDate.isBlank() || durationInMinutes == null || durationInMinutes <= 0) {
            return null;
        }
        try {
            // Beispiel: "17.09.2025 13:48:00"
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            java.time.LocalDateTime startDateTime = java.time.LocalDateTime.parse(startDate, formatter);
            java.time.LocalDateTime endDateTime = startDateTime.plusMinutes(durationInMinutes);
            return endDateTime.format(formatter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String replaceJobPlaceholderVars(Map<String, Object> descriptionParams, Job job, String ActionExtraVars, Map<String, Object> guiParams) {
        if (descriptionParams != null) {
            for (Map.Entry<String, Object> entry : descriptionParams.entrySet()) {
                final String placeholder = Pattern.quote("${" + entry.getKey() + "}");
                final String value = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                job.setTitle(job.getTitle() != null ? job.getTitle().replaceAll(placeholder, value).trim() : "");
                job.setDescription(job.getDescription() != null ? job.getDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionTitle(job.getActionTitle() != null ? job.getActionTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionDescription(job.getActionDescription() != null ? job.getActionDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionExecutionTitle(job.getActionExecutionTitle() != null ? job.getActionExecutionTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionExecutionDescription(job.getActionExecutionDescription() != null ? job.getActionExecutionDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionSuccessTitle(job.getActionSuccessTitle() != null ? job.getActionSuccessTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionSuccessDescription(job.getActionSuccessDescription() != null ? job.getActionSuccessDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionErrorTitle(job.getActionErrorTitle() != null ? job.getActionErrorTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionErrorDescription(job.getActionErrorDescription() != null ? job.getActionErrorDescription().replaceAll(placeholder, value).trim() : "");
                job.setChangeJustification(job.getChangeJustification() != null ? job.getChangeJustification().replaceAll(placeholder, value).trim() : "");
                job.setChangeImplementationPlan(job.getChangeImplementationPlan() != null ? job.getChangeImplementationPlan().replaceAll(placeholder, value).trim() : "");
                job.setChangeRiskImpactAnalysis(job.getChangeRiskImpactAnalysis() != null ? job.getChangeRiskImpactAnalysis().replaceAll(placeholder, value).trim() : "");
                job.setChangeBackoutPlan(job.getChangeBackoutPlan() != null ? job.getChangeBackoutPlan().replaceAll(placeholder, value).trim() : "");
                ActionExtraVars = ActionExtraVars != null ? ActionExtraVars.replaceAll(placeholder, entry.getValue().toString()).trim() : "";
            }
        }
        if (guiParams != null) {
            for (Map.Entry<String, Object> entry : guiParams.entrySet()) {
                final String placeholder = Pattern.quote("${" + entry.getKey() + "}");
                final String value = entry.getValue() != null ? entry.getValue().toString().trim() : "";
                job.setTitle(job.getTitle() != null ? job.getTitle().replaceAll(placeholder, value).trim() : "");
                job.setDescription(job.getDescription() != null ? job.getDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionTitle(job.getActionTitle() != null ? job.getActionTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionDescription(job.getActionDescription() != null ? job.getActionDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionExecutionTitle(job.getActionExecutionTitle() != null ? job.getActionExecutionTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionExecutionDescription(job.getActionExecutionDescription() != null ? job.getActionExecutionDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionSuccessTitle(job.getActionSuccessTitle() != null ? job.getActionSuccessTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionSuccessDescription(job.getActionSuccessDescription() != null ? job.getActionSuccessDescription().replaceAll(placeholder, value).trim() : "");
                job.setActionErrorTitle(job.getActionErrorTitle() != null ? job.getActionErrorTitle().replaceAll(placeholder, value).trim() : "");
                job.setActionErrorDescription(job.getActionErrorDescription() != null ? job.getActionErrorDescription().replaceAll(placeholder, value).trim() : "");
                job.setChangeJustification(job.getChangeJustification() != null ? job.getChangeJustification().replaceAll(placeholder, value).trim() : "");
                job.setChangeImplementationPlan(job.getChangeImplementationPlan() != null ? job.getChangeImplementationPlan().replaceAll(placeholder, value).trim() : "");
                job.setChangeRiskImpactAnalysis(job.getChangeRiskImpactAnalysis() != null ? job.getChangeRiskImpactAnalysis().replaceAll(placeholder, value).trim() : "");
                job.setChangeBackoutPlan(job.getChangeBackoutPlan() != null ? job.getChangeBackoutPlan().replaceAll(placeholder, value).trim() : "");
                ActionExtraVars = ActionExtraVars != null ? ActionExtraVars.replaceAll(placeholder, entry.getValue().toString()).trim() : "";
            }
        }
        return ActionExtraVars;
    }

    private String formatNonPostgresJustification(
            final String dbTypeNormalized,
            final String appserviceNameOrNull,
            final String appserviceSnowSysIdOrNull,
            final Long appserviceMcmpIdOrNull,
            final String fqdn,
            final String requestedByName,
            final String requestedByUsername,
            final String requestedByDepartment,
            final String requestedByEmail,
            final String reason
    ) {
        final boolean hasAppserviceName = appserviceNameOrNull != null && !appserviceNameOrNull.isBlank();
        final boolean hasSnowSysId = appserviceSnowSysIdOrNull != null && !appserviceSnowSysIdOrNull.isBlank();
        final boolean hasMcmpId = appserviceMcmpIdOrNull != null;

        final String serviceNowAppserviceUrl = hasSnowSysId
                ? SERVICENOW_BASE_URL + "/nav_to.do?uri=cmdb_ci_service.do?sys_id=" + urlEncode(appserviceSnowSysIdOrNull + "&sysparm_view=EAM")
                : null;

        String baseUrl;
        try {
            baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().replacePath(null).build().toUriString();
        } catch (Exception e) {
            baseUrl = MCMP_BASE_URL;
        }
        final String mcmpAppserviceUrl = hasMcmpId
                ? baseUrl + "/#/appservice/" + appserviceMcmpIdOrNull
                : null;

        final StringBuilder appserviceBlock = new StringBuilder();
        if (hasAppserviceName || serviceNowAppserviceUrl != null || mcmpAppserviceUrl != null) {
            appserviceBlock.append("<div style=\"font-size:16px;font-weight:700;margin-bottom:8px;\">Anwendungsservice</div>");
            appserviceBlock.append("<ul style=\"margin:0;padding-left:0;list-style-position:inside;font-size:14px;line-height:1.6;\">");

            if (hasAppserviceName) {
                appserviceBlock.append("<li><strong>Name:</strong> ")
                        .append(escapeHtml(appserviceNameOrNull))
                        .append("</li>");
            }

            if (serviceNowAppserviceUrl != null || mcmpAppserviceUrl != null) {
                appserviceBlock.append("<li><strong>Links:</strong> ");

                boolean linkAdded = false;
                if (serviceNowAppserviceUrl != null) {
                    appserviceBlock.append("<a href=\"")
                            .append(escapeHtml(serviceNowAppserviceUrl))
                            .append("\">ServiceNow</a>");
                    linkAdded = true;
                }
                if (mcmpAppserviceUrl != null) {
                    if (linkAdded) {
                        appserviceBlock.append(" | ");
                    }
                    appserviceBlock.append("<a href=\"")
                            .append(escapeHtml(mcmpAppserviceUrl))
                            .append("\">MCMP</a>");
                }

                appserviceBlock.append("</li>");
            }

            appserviceBlock.append("</ul>");
        }


        return String.format(
                """
                <html>
                  <head>
                    <meta charset="UTF-8">
                  </head>
                  <body style="margin:0;padding:0;background-color:#f4f6f8;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f4f6f8;">
                      <tr>
                        <td align="center" style="padding:24px 16px;">
                          <table role="presentation" width="640" cellspacing="0" cellpadding="0" border="0" style="width:640px;max-width:640px;background-color:#ffffff;border:1px solid #e5e7eb;">
                            <tr>
                              <td style="padding:24px 24px 16px 24px;font-family:'Segoe UI',Arial,sans-serif;color:#111827;">
                                <div style="font-size:18px;font-weight:700;line-height:1.3;">Neue Datenbankbestellung</div>
                                <div style="margin-top:8px;font-size:14px;line-height:1.6;">
                                  Es wurde ein neuer <strong>%s</strong>-Datenbankserver in der MCMP bestellt.
                                </div>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:0 24px 8px 24px;font-family:'Segoe UI',Arial,sans-serif;color:#111827;">
                                %s
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:8px 24px 8px 24px;font-family:'Segoe UI',Arial,sans-serif;color:#111827;">
                                <div style="font-size:16px;font-weight:700;margin-bottom:8px;">Serverdetails</div>
                                <ul style="margin:0;padding-left:0;list-style-position:inside;font-size:14px;line-height:1.6;">
                                  <li><strong>FQDN:</strong> %s</li>
                                  <li><strong>Beantragt von:</strong>
                                    <ul style="margin-top:4px;padding-left:20px;list-style-type:circle;">
                                      <li><strong>Name:</strong> %s</li>
                                      <li><strong>Username:</strong> %s</li>
                                      <li><strong>Abteilung:</strong> %s</li>
                                      <li><strong>Email:</strong> <a href="mailto:%s?subject=%s">%s</a></li>
                                    </ul>
                                  </li>
                                </ul>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:8px 24px 24px 24px;font-family:'Segoe UI',Arial,sans-serif;color:#111827;">
                                <div style="font-size:16px;font-weight:700;margin-bottom:8px;">Begründung (Angabe aus der Bestellung)</div>
                                <div style="font-size:14px;line-height:1.6;color:#111827;">%s</div>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """,
                escapeHtml(dbTypeNormalized),
                appserviceBlock,
                escapeHtml(fqdn),
                escapeHtml(requestedByName),
                escapeHtml(requestedByUsername),
                escapeHtml(requestedByDepartment),
                escapeHtml(requestedByEmail),
                urlEncode("Bestellung " + fqdn),
                escapeHtml(requestedByEmail),
                nl2brEscaped(reason)
        );
    }

    private static String escapeHtml(final String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String nl2brEscaped(final String input) {
        if (input == null) return "";
        return escapeHtml(input).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    private static String urlEncode(final String input) {
        if (input == null) return "";
        return URLEncoder.encode(input, StandardCharsets.UTF_8).replace("+", "%20");
    }


}