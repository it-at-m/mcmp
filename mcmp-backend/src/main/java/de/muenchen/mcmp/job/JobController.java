package de.muenchen.mcmp.job;

import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.job.incident.JobIncidentSummary;
import de.muenchen.mcmp.job.node.JobNodeHierarchy;
import de.muenchen.mcmp.mountPoint.MountPointService;
import de.muenchen.mcmp.network.NetworkService;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.HasSpecialRole;
import de.muenchen.mcmp.security.HasUserOrSpecialRole;
import de.muenchen.mcmp.security.IsAdmin;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerFullDTO;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.snapshot.SnapshotService;
import de.muenchen.mcmp.storage.StorageCategory;
import de.muenchen.mcmp.storage.StorageType;
import de.muenchen.mcmp.storage.UnifiedStorageItemDto;
import de.muenchen.mcmp.storage.UnifiedStorageService;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/job")
public class JobController {

    private final JobService jobService;
    private final ServerService serverService;
    private final AppserviceService appserviceService;
    private final NetworkService networkService;
    private final MountPointService mountPointService;
    private final SnapshotService snapshotService;
    private final UnifiedStorageService unifiedStorageService;

    private static final String VMWARE_START_SERVER = "VMWARE_START_SERVER";
    private static final String VMWARE_STOP_SERVER = "VMWARE_STOP_SERVER";
    private static final String VMWARE_RESTART_SERVER = "VMWARE_RESTART_SERVER";
    private static final String VMWARE_CHANGE_CPU_RAM = "VMWARE_CHANGE_CPU_RAM";
    private static final String VMWARE_CREATE_SNAPSHOT = "VMWARE_CREATE_SNAPSHOT";
    private static final String VMWARE_DELETE_SNAPSHOT = "VMWARE_DELETE_SNAPSHOT";
    private static final String VMWARE_REVERT_SNAPSHOT = "VMWARE_REVERT_SNAPSHOT";

    private static final String CHECKMK_SET_DOWNTIME = "CHECKMK_SET_DOWNTIME";
    private static final String CHECKMK_SERVICE_DISCOVERY = "CHECKMK_SERVICE_DISCOVERY";

    private static final String LINUX_DELETE_SERVER = "LINUX_DELETE_SERVER";
    private static final String LINUX_PATCHNIGHT_TIME_CHANGE = "LINUX_PATCHNIGHT_TIME_CHANGE";
    private static final String LINUX_TEMP_ROOT = "LINUX_TEMP_ROOT";
    private static final String LINUX_MOUNTPOINT_CHANGE = "LINUX_MOUNTPOINT_CHANGE";
    private static final String LINUX_RHEL10_SERVER = "LINUX_RHEL10_SERVER";
    private static final String LINUX_RHEL9_SERVER = "LINUX_RHEL9_SERVER";

    private static final String WINDOWS_DELETE_SERVER = "WINDOWS_DELETE_SERVER";
    private static final String WINDOWS_PARTITION_CHANGE = "WINDOWS_PARTITION_CHANGE";
    private static final String WINDOWS_MAINTENANCE_MODE = "WINDOWS_MAINTENANCE_MODE";
    private static final String WINDOWS_MAINTENANCE_MODE_END = "WINDOWS_MAINTENANCE_MODE_END";
    private static final String WINDOWS_TEMP_ADMIN = "WINDOWS_TEMP_ADMIN";
    public static final String WINDOWS_SERVER_2025 = "WINDOWS_SERVER_2025";
    public static final String WINDOWS_SERVER_2022 = "WINDOWS_SERVER_2022";

    private static final String DB_ORACLE_CREATE_BACKUP = "DB_ORACLE_CREATE_BACKUP";

    private static final String ANSIBLE_USER_ADD = "ANSIBLE_USER_ADD";
    private static final String ANSIBLE_USER_REMOVE = "ANSIBLE_USER_REMOVE";

    private static final String LOADBALANCER_F5 = "LOADBALANCER_F5";

    public static final String GREEN_IT_VMWARE_SHUTDOWN = "GREEN_IT_VMWARE_SHUTDOWN";
    public static final String GREEN_IT_VMWARE_RIGHTSIZE = "GREEN_IT_VMWARE_RIGHTSIZE";

    public static final String STORAGE_MODIFY_NFS = "STORAGE_MODIFY_NFS";
    public static final String STORAGE_MODIFY_CIFS = "STORAGE_MODIFY_CIFS";
//    public static final String STORAGE_DELETE_NFS = "STORAGE_DELETE_NFS";
//    public static final String STORAGE_DELETE_CIFS = "STORAGE_DELETE_CIFS";
    public static final String STORAGE_CHANGE_NFS_EXPORT_POLICY = "STORAGE_CHANGE_NFS_EXPORT_POLICY";
    private static final String STORAGE_CREATE_SNAPSHOT_NFS = "STORAGE_CREATE_SNAPSHOT_NFS";
    private static final String STORAGE_CREATE_SNAPSHOT_CIFS = "STORAGE_CREATE_SNAPSHOT_CIFS";
    private static final String STORAGE_DELETE_SNAPSHOT_NFS = "STORAGE_DELETE_SNAPSHOT_NFS";
    private static final String STORAGE_DELETE_SNAPSHOT_CIFS = "STORAGE_DELETE_SNAPSHOT_CIFS";
    private static final String STORAGE_CHANGE_SNAPSHOT_POLICY_NFS = "STORAGE_CHANGE_SNAPSHOT_POLICY_NFS";
    private static final String STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS = "STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS";

    private final UserService userService;

    @HasUserOrSpecialRole
    @GetMapping("/{jobId}/hierarchy")
    public List<JobNodeHierarchy> getJobHierarchy(@PathVariable("jobId") final Long jobId) {
        return jobService.getJobHierarchy(jobId);
    }

    @HasUserOrSpecialRole
    @GetMapping("/{jobId}/incidents")
    public List<JobIncidentSummary> getJobIncidents(@PathVariable("jobId") final Long jobId) {
        return jobService.getIncidentSummariesByJobId(jobId);
    }

    @HasSpecialRole
    @GetMapping("/search")
    public Page<? extends JobListBasic> searchJobs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "itemsPerPage", defaultValue = "10") int itemsPerPage,
            @RequestParam(value = "sortBy", required = false) final String sortBy,
            @RequestParam(value = "sortDesc", defaultValue = "false") final boolean sortDesc,
            @RequestParam(value = "jobId", required = false) final Long jobId,
            @RequestParam(value = "awxJobId", required = false) final Long awxJobId,
            @RequestParam(value = "createdFrom", required = false) final String createdFrom,
            @RequestParam(value = "createdTo", required = false) final String createdTo,
            @RequestParam(value = "changeStartFrom", required = false) final String changeStartFrom,
            @RequestParam(value = "changeStartTo", required = false) final String changeStartTo,
            @RequestParam(value = "userId", required = false) final Long userId,
            @RequestParam(value = "serverId", required = false) final Long serverId,
            @RequestParam(value = "appserviceId", required = false) final Long appserviceId,
            @RequestParam(value = "actionIdentifier", required = false) final String actionIdentifier,
            @RequestParam(value = "statusIdentifier", required = false) final String statusIdentifier,
            @RequestParam(value = "awxVariables", required = false) final String awxVariables
    ) {
        if (page < 1) {
            page = 1;
        }
        if (itemsPerPage < 1 || itemsPerPage > 100) {
            itemsPerPage = 10;
        }
        final Instant createdFromInstant = createdFrom != null && !createdFrom.isBlank() ? Instant.parse(createdFrom) : null;
        final Instant createdToInstant = createdTo != null && !createdTo.isBlank() ? Instant.parse(createdTo) : null;
        final Instant changeStartFromInstant = changeStartFrom != null && !changeStartFrom.isBlank() ? Instant.parse(changeStartFrom) : null;
        final Instant changeStartToInstant = changeStartTo != null && !changeStartTo.isBlank() ? Instant.parse(changeStartTo) : null;

        return jobService.findAllJobsByRole(page, itemsPerPage, sortBy, sortDesc, jobId, awxJobId, createdFromInstant, createdToInstant, changeStartFromInstant, changeStartToInstant, userId, serverId, appserviceId, actionIdentifier, statusIdentifier, awxVariables);
    }

    @HasSpecialRole
    @GetMapping("/actions")
    public List<String> getAllActionIdentifiers() {
        return jobService.findAllActionIdentifiers();
    }

    @HasSpecialRole
    @GetMapping("/status")
    public List<String> getAllAStatusIdentifiers() {
        return jobService.findAllStatusIdentifiers();
    }

    @HasUserOrSpecialRole
    @GetMapping("/server/{serverId}")
    public Page<? extends JobListBasic> getJobsByServerId(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "itemsPerPage", defaultValue = "10") int itemsPerPage,
            @RequestParam(value = "sortBy", required = false) final String sortBy,
            @RequestParam(value = "sortDesc", defaultValue = "false") final boolean sortDesc,
            @PathVariable("serverId") final Long serverId
    ) {
        if (page < 1) {
            page = 1;
        }
        if (itemsPerPage < 1 || itemsPerPage > 100) {
            itemsPerPage = 10;
        }
        return jobService.findAllJobsByRole(page, itemsPerPage, sortBy, sortDesc, null, null, null, null, null, null, null, serverId, null, null, null, null);
    }

    @HasUserOrSpecialRole
    @GetMapping("/user")
    public Page<? extends JobListBasic> getJobsByUser(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "itemsPerPage", defaultValue = "10") int itemsPerPage,
            @RequestParam(value = "sortBy", required = false) final String sortBy,
            @RequestParam(value = "sortDesc", defaultValue = "false") final boolean sortDesc
    ) {
        if (page < 1) {
            page = 1;
        }
        if (itemsPerPage < 1 || itemsPerPage > 100) {
            itemsPerPage = 10;
        }
        final String username = AuthUtils.getUsername();
        final Optional<User> user = userService.findByUsername(username);
        if (user.isPresent()) {
            return jobService.findAllJobsByRole(page, itemsPerPage, sortBy, sortDesc, null, null, null, null, null, null, user.get().getId(), null, null, null, null, null);
        }
        log.warn("User {} is not authorized to view jobs", username);
        throw new AccessDeniedException("You are not allowed to access this users jobs.");
    }

    @HasUserOrSpecialRole
    @GetMapping("/user/notification")
    public long getJobNotificationsByUsername() {
        final String username = AuthUtils.getUsername();
        return jobService.getJobNotificationsByUsername(username);
    }

    @HasUserOrSpecialRole
    @PutMapping("/user/notification")
    public void resetJobNotificationsByUsername() {
        final String username = AuthUtils.getUsername();
        jobService.resetJobNotificationsByUsername(username);
    }

    @IsAdmin
    @GetMapping("/statistics")
    public List<JobStatistics> getJobStatistics(@RequestParam final LocalDate startDate, @RequestParam final LocalDate endDate) {
        return jobService.getJobStatistics(startDate, endDate);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // VMWARE JOBs
    // -----------------------------------------------------------------------------------------------------------------
    @PostMapping("/create/" + VMWARE_START_SERVER)
    public void vmwareStartServer(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_START_SERVER, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        Object scheduleTimeObj = awxExtraVars.get("scheduleTime");
        Instant scheduleTime;
        if (scheduleTimeObj != null) {
            scheduleTime = Instant.parse(scheduleTimeObj.toString());
        } else{
            scheduleTime = null;
        }

        logCreatedJob(VMWARE_START_SERVER, serverId);
        jobService.vmwareStartServer(serverId, VMWARE_START_SERVER, scheduleTime);
    }

    @PostMapping("/create/" + VMWARE_STOP_SERVER)
    public void vmwareStopServer(@RequestParam(name = "serverId") final Long serverId,
                                 @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_STOP_SERVER, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        Object scheduleTimeObj = awxExtraVars.get("scheduleTime");
        Instant scheduleTime;
        if (scheduleTimeObj != null) {
            scheduleTime = Instant.parse(scheduleTimeObj.toString());
        } else{
            scheduleTime = null;
        }

        logCreatedJob(VMWARE_STOP_SERVER, serverId);
        jobService.vmwareStopServer(serverId, VMWARE_STOP_SERVER, scheduleTime);

    }

    @PostMapping("/create/" + VMWARE_RESTART_SERVER)
    public void vmwareRestartServer(@RequestParam(name = "serverId") final Long serverId,
                                    @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_RESTART_SERVER, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        Object scheduleTimeObj = awxExtraVars.get("scheduleTime");
        Instant scheduleTime;
        if (scheduleTimeObj != null) {
            scheduleTime = Instant.parse(scheduleTimeObj.toString());
        } else{
            scheduleTime = null;
        }

        logCreatedJob(VMWARE_RESTART_SERVER, serverId);
        jobService.vmwareRestartServer(serverId, VMWARE_RESTART_SERVER, scheduleTime);
    }

    @PostMapping("/create/" + VMWARE_CHANGE_CPU_RAM)
    public void vmwareChangeCpuRam(@RequestParam(name = "serverId") final Long serverId,
                                   @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_CHANGE_CPU_RAM, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        ServerFullDTO server = serverService.getServerById(serverId);

        Object scheduleTimeObj = awxExtraVars.get("scheduleTime");
        Object schedulePatchnightObj = awxExtraVars.get("schedulePatchnight");
        Instant scheduleTime;
        if (scheduleTimeObj != null) {
            scheduleTime = Instant.parse(scheduleTimeObj.toString());
        } else{
            scheduleTime = null;
        }
        boolean schedulePatchnight = Boolean.parseBoolean(schedulePatchnightObj.toString());

        if (schedulePatchnight && !server.patchnightIncluded()){
            log.warn("Schedule request by user: {} for serverId: {} can't be accomplished because no participation in the patchnight.", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("Can't set a schedule because no participation in the patchnight.");
        }

        // Validate awxExtraVars for CPU and RAM
        Object cpuObj = awxExtraVars.get("cpu");
        Object ramObj = awxExtraVars.get("ram");
        if (cpuObj == null || ramObj == null) {
            log.info("CPU or RAM values not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("CPU and RAM values must be provided.");
        }
        int cpu = Integer.parseInt(cpuObj.toString());
        int ram = Integer.parseInt(ramObj.toString());

        if (cpu < 2 || (cpu > 72 && cpu > server.numCpu()) || ram < 4 || (ram > 72 && ram > server.memoryMb()*1024)) {
            log.warn("Invalid CPU or RAM values provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("CPU must be between 2 and 72, RAM must be between 4 and 100.");
        }

        logCreatedJob(VMWARE_CHANGE_CPU_RAM, serverId);
        jobService.vmwareChangeCpuRam(serverId, VMWARE_CHANGE_CPU_RAM, cpu, ram, scheduleTime, schedulePatchnight);
    }

    @PostMapping("/create/" + VMWARE_CREATE_SNAPSHOT)
    public void vmwareCreateSnapshot(@RequestParam(name = "serverId") final Long serverId,
                                     @RequestBody final Map<String, Object> awxExtraVars){
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_CREATE_SNAPSHOT, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object durationObj = awxExtraVars.get("duration");
        Object descriptionObj = awxExtraVars.get("description");
        Object withShutdownObj = awxExtraVars.get("withShutdown");

        if (durationObj == null) {
            log.info("Duration value not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Duration value must be provided.");
        }
        int duration = Integer.parseInt(durationObj.toString());
        if (duration < 1 || duration > 10) {
            log.warn("Invalid duration value provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("Duration must be between 1 and 10 days.");
        }

        String description = (descriptionObj == null) ? "" : descriptionObj.toString();
        if (description.length() > 50) {
            log.warn("User {} provided a snapshot description that is too long.", AuthUtils.getUsername());
            throw new IllegalArgumentException("The description must not exceed 50 characters.");
        }

        boolean withShutdown = true;
        if (withShutdownObj != null) {
            withShutdown = Boolean.parseBoolean(withShutdownObj.toString());
        }

        logCreatedJob(VMWARE_CREATE_SNAPSHOT, serverId);

        jobService.vmwareCreateSnapshot(serverId, duration*24, description, withShutdown, VMWARE_CREATE_SNAPSHOT);
    }

    @PostMapping("/create/" + VMWARE_DELETE_SNAPSHOT)
    public void vmwareDeleteSnapshot(@RequestParam(name = "serverId") final Long serverId,
                                     @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_DELETE_SNAPSHOT, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object snapshotIdObj = awxExtraVars.get("snapshotId");
        if (snapshotIdObj == null) {
            log.info("Snapshot ID not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Snapshot ID must be provided.");
        }
        long snapshotId = Long.parseLong(snapshotIdObj.toString());

        logCreatedJob(VMWARE_DELETE_SNAPSHOT, serverId);

        jobService.vmwareDeleteSnapshot(serverId, snapshotId, VMWARE_DELETE_SNAPSHOT);
    }

    @PostMapping("/create/" + VMWARE_REVERT_SNAPSHOT)
    public void vmwareRevertSnapshot(@RequestParam(name = "serverId") final Long serverId,
                                     @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_REVERT_SNAPSHOT, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object snapshotIdObj = awxExtraVars.get("snapshotId");
        if (snapshotIdObj == null) {
            log.info("Snapshot ID not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Snapshot ID must be provided.");
        }
        long snapshotId = Long.parseLong(snapshotIdObj.toString());

        logCreatedJob(VMWARE_REVERT_SNAPSHOT, serverId);

        jobService.vmwareRevertSnapshot(serverId, snapshotId, VMWARE_REVERT_SNAPSHOT);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // CHECKMK JOBs
    // -----------------------------------------------------------------------------------------------------------------
    @PostMapping("/create/" + CHECKMK_SET_DOWNTIME)
    public void checkmkSetDowntime(@RequestParam(name = "serverId") final Long serverId,
                                   @RequestBody final Map<String, Object> awxExtraVars){
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_CREATE_SNAPSHOT, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object startDateObj = awxExtraVars.get("startDate");
        Object durationObj = awxExtraVars.get("duration");

        if (startDateObj == null) {
            log.info("Start Date not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Start Date must be provided.");
        }
        String startDate = startDateObj.toString();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

        try {
            LocalDateTime.parse(startDate, formatter);
        } catch (DateTimeParseException e) {
            log.info("Start Date not provided by user in the right Format (DD.MM.YYYY HH:MM:SS): {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("Duration value must be provided in the right format (DD.MM.YYYY HH:MM:SS).");
        }

        if (durationObj == null) {
            log.info("Duration value not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Duration value must be provided.");
        }
        int duration = Integer.parseInt(durationObj.toString());

        logCreatedJob(CHECKMK_SET_DOWNTIME, serverId);

        jobService.checkmkSetDowntime(serverId, startDate, duration, CHECKMK_SET_DOWNTIME);
    }

    @PostMapping("/create/" + CHECKMK_SERVICE_DISCOVERY)
    public void checkmkServiceDiscovery(@RequestParam(name = "serverId") final Long serverId,
                                        @RequestBody final Map<String, Object> awxExtraVars){
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_CREATE_SNAPSHOT, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object actionObj = awxExtraVars.get("action");

        if (actionObj == null) {
            log.info("Action Value not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Action value must be provided.");
        }
        String action = actionObj.toString();

        logCreatedJob(CHECKMK_SERVICE_DISCOVERY, serverId);

        jobService.checkmkServiceDiscovery(serverId, action, CHECKMK_SERVICE_DISCOVERY);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // LINUX JOBs
    // -----------------------------------------------------------------------------------------------------------------
    @PostMapping("/create/" + LINUX_PATCHNIGHT_TIME_CHANGE)
    public void linuxPatchnightTimeChange(@RequestParam(name = "serverId") final Long serverId,
                                          @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(LINUX_PATCHNIGHT_TIME_CHANGE, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars for time
        Object timeObj = awxExtraVars.get("time");
        if (timeObj == null) {
            log.info("Time values not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Time values must be provided.");
        }
        String time = timeObj.toString();

        logCreatedJob(LINUX_PATCHNIGHT_TIME_CHANGE, serverId);

        jobService.linuxPatchnightTimeChange(serverId, LINUX_PATCHNIGHT_TIME_CHANGE, time);
    }

    @PostMapping("/create/" + LINUX_DELETE_SERVER)
    public void linuxDeleteServer(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_STOP_SERVER, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        Object scheduleTimeObj = awxExtraVars.get("scheduleTime");
        Instant scheduleTime;
        if (scheduleTimeObj != null) {
            scheduleTime = Instant.parse(scheduleTimeObj.toString());
        } else{
            scheduleTime = null;
        }

        logCreatedJob(LINUX_DELETE_SERVER, serverId);
        jobService.linuxDeleteServer(serverId, LINUX_DELETE_SERVER, scheduleTime);

    }

    @PostMapping("/create/" + LINUX_TEMP_ROOT)
    public void linuxTempRoot(@RequestParam(name = "serverId") final Long serverId,
                              @RequestBody final Map<String, Object> awxExtraVars) {
        if (!AuthUtils.getCurrentUserRoles().hasOperatorRole() && !serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(LINUX_TEMP_ROOT, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }
        Object durationObj = awxExtraVars.get("duration");
        Object otherUsernameObj = awxExtraVars.get("other_username");

        String duration;
        if (durationObj == null) {
            duration = "3 days"; // set default duration
        } else {
            duration = durationObj.toString().trim();
        }

        // only check for 3 days for now
        if (!duration.equals("3 days")) {
            log.warn("Invalid duration provided by user: {} for serverId: {} for job {} with duration: {}",
                    AuthUtils.getUsername(), serverId, LINUX_TEMP_ROOT, duration);
            throw new IllegalArgumentException("Duration must be '3 days'.");
        }

        String otherUsername = null;
        if (otherUsernameObj != null) {
            otherUsername = otherUsernameObj.toString().trim();
            if (otherUsername.isBlank() || !otherUsername.matches("^[a-zA-Z0-9][a-zA-Z0-9-_.]*$")) {
                log.info("Invalid other username provided by user: {} for serverId: {} for job {}",
                        AuthUtils.getUsername(), serverId, LINUX_TEMP_ROOT);
                throw new IllegalArgumentException("Provided Username is invalid.");
            }
        }


        /*
        // duration check format e.g. "3 days" | for future use
        if (!duration.matches("\\d+\\s+(days|hours)")) {
            log.warn("Invalid duration format provided by user: {} for serverId: {} for job {} with duration: {}",
                    AuthUtils.getUsername(), serverId, LINUX_TEMP_ROOT, duration);
           throw new IllegalArgumentException("Duration must be in format '3 days' or '72 hours'.");
        }
         */

        logCreatedJob(LINUX_TEMP_ROOT, serverId);
        jobService.linuxTempRootOneServerOnly(serverId, LINUX_TEMP_ROOT, duration, otherUsername);
    }

    @PostMapping("/create/" + LINUX_MOUNTPOINT_CHANGE)
    public void linuxMountpointChange(@RequestParam(name = "serverId") final Long serverId,
                                      @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(LINUX_MOUNTPOINT_CHANGE, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object mountPointObj = awxExtraVars.get("mountPoint");
        Object newSizeObj = awxExtraVars.get("newSize");
        Object volumeGroupObj = awxExtraVars.get("volumeGroup");

        if (mountPointObj == null || newSizeObj == null) {
            log.info("Mountpoint Path or new Size for the Mountpoint is not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Mountpoint and new size must be provided.");
        }

        String mountPointPath = mountPointObj.toString();
        int newSize = Integer.parseInt(newSizeObj.toString());
        String logicalName = "";
        String volumeGroup = volumeGroupObj.toString();

        if (mountPointPath.length() > 50) {
            log.warn("Invalid lenth of Mountpoint path lengs had provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new AccessDeniedException("New Mountpoint path is too long (max 50 characters).");
        }

        if (newSize < (mountPointService.getMountPointByServerIdAndPath(serverId, mountPointPath).capacityInBytes() / (1024 * 1024 * 1024)) ||
                newSize > 2000) {
            log.warn("Invalid size provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("New Size could not be smaller then the old size and not bigger then 2000 GB.");
        }

        if (!snapshotService.getSnapshotsByServerId(serverId).isEmpty()) {
            log.warn("Mountpoint can't be changed with snapshot for serverId: {} by user: {}", AuthUtils.getUsername(), serverId);
            throw new AccessDeniedException("Can't change size of mountpoint. Please remove the snapshot first and try it again.");
        }

        if (volumeGroup != "") {
            logicalName = mountPointPath.substring(mountPointPath.lastIndexOf('/') + 1);
        }

        logCreatedJob(LINUX_MOUNTPOINT_CHANGE, serverId);

        jobService.linuxMountpointChange(serverId, LINUX_MOUNTPOINT_CHANGE, mountPointPath, newSize, logicalName, volumeGroup);
    }

    @PostMapping("/create/" + LINUX_RHEL10_SERVER)
    public void linuxRhel10Server(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars){
        // Custom Linux
        Object linux_custom = awxExtraVars.get("linux_custom");
        if (linux_custom != null && Boolean.parseBoolean(linux_custom.toString()) && AuthUtils.getCurrentUserRoles().hasLinuxRole()) {
            jobService.linuxCustomRhelServer(awxExtraVars, "LINUX_RHEL10_SERVER");
            return;
        }

        linuxRecord linuxRecord = getAndCheckLinuxRhelVars(awxExtraVars);
        Object middlewareUserObj = awxExtraVars.get("middleware_user");
        checkServerInstallCanUserEditAppservice(linuxRecord.applicationServiceId());

        // RHEL 10 specific middleware user check
        if (middlewareUserObj == null) {
            log.info("Middleware user not provided by user: {} to order a RHEL 10 server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("Middleware user must be provided.");
        }
        boolean middlewareUser = Boolean.parseBoolean(middlewareUserObj.toString());
        if(Objects.equals(linuxRecord.categoryType, "DB") && middlewareUser) {
            log.info("Middleware user cannot be set for database servers by user: {} to order a RHEL 10 server.", AuthUtils.getUsername());
            throw new IllegalArgumentException("Middleware user cannot be set for database servers.");
        }


        logCreatedJob(LINUX_RHEL10_SERVER, serverId);
        jobService.linuxRhelServer(linuxRecord.fqdnBuildingBlocks(), linuxRecord.categoryType(), linuxRecord.serverTypeMap(), linuxRecord.ram(), linuxRecord.cpu(), linuxRecord.networkGroupId(), linuxRecord.applicationServiceId(), linuxRecord.dbParams(), linuxRecord.nonPostgresReason(), middlewareUser, LINUX_RHEL10_SERVER);
    }

    @PostMapping("/create/" + LINUX_RHEL9_SERVER)
    public void linuxRhel9Server(@RequestParam(name = "serverId") final Long serverId,
                                 @RequestBody final Map<String, Object> awxExtraVars){
        // Custom Linux
        Object linux_custom = awxExtraVars.get("linux_custom");
        if (linux_custom != null && Boolean.parseBoolean(linux_custom.toString()) && AuthUtils.getCurrentUserRoles().hasLinuxRole()) {
            jobService.linuxCustomRhelServer(awxExtraVars, "LINUX_RHEL9_SERVER");
            return;
        }

        linuxRecord linuxRecord = getAndCheckLinuxRhelVars(awxExtraVars);
        checkServerInstallCanUserEditAppservice(linuxRecord.applicationServiceId());

        logCreatedJob(LINUX_RHEL9_SERVER, serverId);
        jobService.linuxRhelServer(linuxRecord.fqdnBuildingBlocks(), linuxRecord.categoryType(), linuxRecord.serverTypeMap(), linuxRecord.ram(), linuxRecord.cpu(), linuxRecord.networkGroupId(), linuxRecord.applicationServiceId(), linuxRecord.dbParams(), linuxRecord.nonPostgresReason(), false, LINUX_RHEL9_SERVER);
    }

    private linuxRecord getAndCheckLinuxRhelVars(Map<String, Object> awxExtraVars) {
        // Validate awxExtraVars
        Object fqdnObj = awxExtraVars.get("fqdn");
        Object categoryTypeObj = awxExtraVars.get("categoryType");
        Object serverTypeObj = awxExtraVars.get("serverType");
        Object ramObj = awxExtraVars.get("ram");
        Object cpuObj = awxExtraVars.get("cpu");
        Object networkGroupIdObj = awxExtraVars.get("network_group_id");
        Object applicationServiceIdObj = awxExtraVars.get("application_service_id");
        Object dbParamsObj = awxExtraVars.get("db_params");
        Object nonPostgresReasonObj = awxExtraVars.get("non_postgres_reason");

        if (fqdnObj == null
                || categoryTypeObj == null
                || ramObj == null
                || cpuObj == null
                || networkGroupIdObj == null
                || applicationServiceIdObj == null) {
            log.info("Not all needed parameter provided by user: {} to order a server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("Not all needed parameter are provided.");
        }

        Map<?, ?> fqdnBuildingBlocks;
        Map<String, Map<?, ?>> dbParams = null;
        try {
            fqdnBuildingBlocks = (Map<?, ?>) fqdnObj;
        } catch (ClassCastException e) {
            log.info("FQDN not provided by user: {} to order a server in the right format.", AuthUtils.getUsername());
            throw new IllegalArgumentException("FQDN is not provided in the right format.");
        }


        String categoryType = categoryTypeObj.toString();
        if ((categoryType.equals("DB") || categoryType.equals("Mixed")) && dbParamsObj == null) {
            log.info("DB Parameters not provided by user: {} to order a database server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("DB Parameters must be provided for database servers.");
        } else if (dbParamsObj != null) {
            dbParams = requireStringMapOfMap(dbParamsObj, "DB Parameters");
        }

        int ram = Integer.parseInt(ramObj.toString());
        int cpu = Integer.parseInt(cpuObj.toString());
        if (cpu < 2 || cpu > 8 || ram < 4 || ram > 64) {
            log.warn("Invalid CPU or RAM provided by user: {} for new RHEL10 Server Install", AuthUtils.getUsername());
            throw new IllegalArgumentException("CPU must be between 2 and 8, RAM must be between 4 and 64 GB.");
        }

        long applicationServiceId = Integer.parseInt(applicationServiceIdObj.toString());
        if (appserviceService.getVisibleAppservice(applicationServiceId) == null){
            throw new AccessDeniedException("You are not allowed to order a server for this application service.");
        }

        Map<?, ?> serverTypeMap = null;
        if (!categoryType.equals("Standard")) {
            try {
                serverTypeMap = (Map<?, ?>) serverTypeObj;
            } catch (ClassCastException e) {
                log.warn("Invalid server type provided by user: {} to order a server.", AuthUtils.getUsername());
                throw new IllegalArgumentException("Server type is invalid.");
            }
        }
        String nonPostgresReason = null;
        if (categoryType.equals("DB") || categoryType.equals("Mixed")) {
            if (!serverTypeMap.get("label").toString().contains("PostgreSQL")) {
                if (nonPostgresReasonObj == null) {
                    log.info("Non-Postgres reason not provided by user: {} to order a database server.", AuthUtils.getUsername());
                    throw new MissingFormatArgumentException("Non-Postgres reason must be provided for non-Postgres database servers.");
                } else {
                    nonPostgresReason = nonPostgresReasonObj.toString();
                    checkNonPostgresReason(nonPostgresReason);
                }
            }
        }

        long networkGroupId = Integer.parseInt(networkGroupIdObj.toString());
        if (!networkService.isAllowedNetworkGroupForAppservice(networkGroupId, applicationServiceId, categoryType.equals("DB"))) {
            throw new AccessDeniedException("You are not allowed to order a server for this combination of application service and network group.");
        }
        return new linuxRecord(fqdnBuildingBlocks, dbParams, categoryType, ram, cpu, applicationServiceId, serverTypeMap, nonPostgresReason, networkGroupId);
    }

    private record linuxRecord(Map<?,?> fqdnBuildingBlocks, Map<String, Map<?,?>> dbParams, String categoryType, int ram, int cpu, long applicationServiceId, Map<?,?> serverTypeMap, String nonPostgresReason, long networkGroupId) {
    }

    // -----------------------------------------------------------------------------------------------------------------
    // WINDOWS JOBs
    // -----------------------------------------------------------------------------------------------------------------
    @PostMapping("/create/" + WINDOWS_DELETE_SERVER)
    public void windowsDeleteServer(@RequestParam(name = "serverId") final Long serverId,
                                    @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(VMWARE_STOP_SERVER, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        Object scheduleTimeObj = awxExtraVars.get("scheduleTime");
        Instant scheduleTime;
        if (scheduleTimeObj != null) {
            scheduleTime = Instant.parse(scheduleTimeObj.toString());
        } else{
            scheduleTime = null;
        }

        logCreatedJob(WINDOWS_DELETE_SERVER, serverId);
        jobService.windowsDeleteServer(serverId, WINDOWS_DELETE_SERVER, scheduleTime);

    }

    @PostMapping("/create/" + WINDOWS_TEMP_ADMIN)
    public void windowsTempAdmin(@RequestParam(name = "serverId") final Long serverId,
                                 @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(WINDOWS_TEMP_ADMIN, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        Object otherUsernameObj = awxExtraVars.get("other_username");

        String otherUsername = null;
        if (otherUsernameObj != null) {
            otherUsername = otherUsernameObj.toString().trim();
            if (otherUsername.isBlank() || !otherUsername.matches("^[a-zA-Z0-9][a-zA-Z0-9-_.]*$")) {
                log.info("Invalid other username provided by user: {} for serverId: {} for job {}",
                        AuthUtils.getUsername(), serverId, LINUX_TEMP_ROOT);
                throw new IllegalArgumentException("Provided Username is invalid.");
            }
        }

        logCreatedJob(WINDOWS_TEMP_ADMIN, serverId);
        jobService.windowsTempAdminOneServerOnly(serverId, WINDOWS_TEMP_ADMIN, otherUsername);
    }

    @PostMapping("/create/" + WINDOWS_PARTITION_CHANGE)
    public void windowsPartitionChange(@RequestParam(name = "serverId") final Long serverId,
                                       @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(WINDOWS_PARTITION_CHANGE, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object partitionObj = awxExtraVars.get("partition");
        Object newSizeObj = awxExtraVars.get("newSize");

        if (partitionObj == null || newSizeObj == null) {
            log.info("Partition or new Size for the Partition is not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Partition and new size must be provided.");
        }

        String partition = partitionObj.toString();
        int newSize = Integer.parseInt(newSizeObj.toString());

        if (newSize < (mountPointService.getMountPointByServerIdAndPath(serverId, partition).capacityInBytes() / (1024 * 1024 * 1024)) ||
                newSize > 2000) {
            log.warn("Invalid size provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("New Size could not be smaller then the old size and not bigger then 2000 GB.");
        }

        if (!snapshotService.getSnapshotsByServerId(serverId).isEmpty()) {
            log.warn("Partition can't be changed with snapshot for serverId: {} by user: {}", AuthUtils.getUsername(), serverId);
            throw new AccessDeniedException("Can't change size of partition. Please remove the snapshot first and try it again.");
        }

        logCreatedJob(WINDOWS_PARTITION_CHANGE, serverId);

        jobService.windowsPartitionChange(serverId, WINDOWS_PARTITION_CHANGE, partition, newSize);
    }

    @PostMapping("/create/" + WINDOWS_MAINTENANCE_MODE)
    public void winMaintenaceMode(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(WINDOWS_MAINTENANCE_MODE, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        // Validate awxExtraVars
        Object maintenanceModeEndObj = awxExtraVars.get("wartungsmodus_ende");

        if (maintenanceModeEndObj == null) {
            log.info("Maintenance Mode end date and time not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Maintenance Mode end date and time value must be provided.");
        }
        String maintenanceModeEnd = maintenanceModeEndObj.toString();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

        try {
            LocalDateTime.parse(maintenanceModeEnd, formatter);
        } catch (DateTimeParseException e) {
            log.info("Maintenance Mode end date and time not provided by user in the right format (DD.MM.YYYY HH:MM:SS): {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("Maintenance Mode end date and time value must be provided in right format.");
        }

        logCreatedJob(WINDOWS_MAINTENANCE_MODE, serverId);

        jobService.windowsMaintenaceMode(serverId, maintenanceModeEnd, WINDOWS_MAINTENANCE_MODE);
    }

    @PostMapping("/create/" + WINDOWS_MAINTENANCE_MODE_END)
    public void winMaintenaceModeEnd(@RequestParam(name = "serverId") final Long serverId) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(WINDOWS_MAINTENANCE_MODE_END, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        logCreatedJob(WINDOWS_MAINTENANCE_MODE_END, serverId);
        jobService.windowsMaintenaceModeEnd(serverId, WINDOWS_MAINTENANCE_MODE_END);
    }

    // ==========================================
// WINDOWS SERVER 2025
// ==========================================
    @PostMapping("/create/" + WINDOWS_SERVER_2025)
    public void windowsServer2025(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        // Validate awxExtraVars
        Object fqdnObj = awxExtraVars.get("fqdn");
        Object categoryTypeObj = awxExtraVars.get("categoryType");
        Object serverTypeObj = awxExtraVars.get("serverType");
        Object ramObj = awxExtraVars.get("ram");
        Object cpuObj = awxExtraVars.get("cpu");
        Object disksObj = awxExtraVars.get("disks");
        Object networkGroupIdObj = awxExtraVars.get("network_group_id");
        Object applicationServiceIdObj = awxExtraVars.get("application_service_id");
        Object osVersionObj = awxExtraVars.get("osVersion");
        Object nonPostgresReasonObj = awxExtraVars.get("non_postgres_reason");
        Object dbParamsObj = awxExtraVars.get("db_params");

        if (fqdnObj == null
                || categoryTypeObj == null
                || ramObj == null
                || cpuObj == null
                || disksObj == null
                || networkGroupIdObj == null
                || applicationServiceIdObj == null
                || osVersionObj == null) {
            log.info("Not all needed parameter provided by user: {} to order a server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("Not all needed parameter are provided.");
        }

        Map<?, ?> fqdnBuildingBlocks = requireMap(fqdnObj, "FQDN");
        List<Map<String, Object>> disks = requireListOfMap(disksObj, "Disks");

        int ram = Integer.parseInt(ramObj.toString());
        int cpu = Integer.parseInt(cpuObj.toString());
        if (cpu < 2 || cpu > 8 || ram < 4 || ram > 64) {
            log.warn("Invalid CPU or RAM provided by user: {} for new Windows Server Install", AuthUtils.getUsername());
            throw new IllegalArgumentException("CPU must be between 2 and 8, RAM must be between 4 and 64 GB.");
        }

        String osVersion = osVersionObj.toString();
        // Prüft, ob die Version zu Windows 2025 passt (als String oder Enum-Wert)
        if (!osVersion.equals("Windows Server 2025") && !osVersion.equals("WIN2025")) {
            log.warn("Invalid OS Version provided by user: {} for new Windows Server 2025 Install", AuthUtils.getUsername());
            throw new IllegalArgumentException("OS Version is invalid for this endpoint.");
        }

        String categoryType = categoryTypeObj.toString();
        String nonPostgresReason = null;
        Map<?, ?> serverTypeMap = null;
        if (!categoryType.equals("Standard")) {
            serverTypeMap = requireMap(serverTypeObj, "Server Type");
        }
        if (categoryType.equals("DB")) {
            if (nonPostgresReasonObj == null) {
                log.info("Non-Postgres reason not provided by user: {} to order a database server.", AuthUtils.getUsername());
                throw new MissingFormatArgumentException("Non-Postgres reason must be provided for non-Postgres database servers.");
            } else {
                nonPostgresReason = nonPostgresReasonObj.toString();
                checkNonPostgresReason(nonPostgresReason);
            }
        }
        Map<String, Map<?, ?>> dbParams = null;
        if ((categoryType.equals("DB") || categoryType.equals("Mixed")) && dbParamsObj == null) {
            log.info("DB Parameters not provided by user: {} to order a database server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("DB Parameters must be provided for database servers.");
        } else if (dbParamsObj != null) {
            dbParams = requireStringMapOfMap(dbParamsObj, "DB Parameters");
        }

        long applicationServiceId = Integer.parseInt(applicationServiceIdObj.toString());
        checkServerInstallCanUserEditAppservice(applicationServiceId);

        long networkGroupId = Integer.parseInt(networkGroupIdObj.toString());
        if (!networkService.isAllowedNetworkGroupForAppservice(networkGroupId, applicationServiceId, categoryTypeObj.toString().equals("DB"))) {
            throw new AccessDeniedException("You are not allowed to order a server for this combination of application service and network group.");
        }

        // Übergabe der spezifischen WINDOWS_SERVER_2025 Konstante an den Service
        jobService.windowsServer(fqdnBuildingBlocks, serverTypeMap, categoryType, ram, cpu,
                disks, networkGroupId, applicationServiceId, osVersion, nonPostgresReason, dbParams, WINDOWS_SERVER_2025);
    }

    // ==========================================
// WINDOWS SERVER 2022
// ==========================================
    @PostMapping("/create/" + WINDOWS_SERVER_2022)
    public void windowsServer2022(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        // Validate awxExtraVars
        Object fqdnObj = awxExtraVars.get("fqdn");
        Object categoryTypeObj = awxExtraVars.get("categoryType");
        Object serverTypeObj = awxExtraVars.get("serverType");
        Object ramObj = awxExtraVars.get("ram");
        Object cpuObj = awxExtraVars.get("cpu");
        Object disksObj = awxExtraVars.get("disks");
        Object networkGroupIdObj = awxExtraVars.get("network_group_id");
        Object applicationServiceIdObj = awxExtraVars.get("application_service_id");
        Object osVersionObj = awxExtraVars.get("osVersion");
        Object nonPostgresReasonObj = awxExtraVars.get("non_postgres_reason");
        Object dbParamsObj = awxExtraVars.get("db_params");

        if (fqdnObj == null
                || categoryTypeObj == null
                || ramObj == null
                || cpuObj == null
                || disksObj == null
                || networkGroupIdObj == null
                || applicationServiceIdObj == null
                || osVersionObj == null) {
            log.info("Not all needed parameter provided by user: {} to order a server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("Not all needed parameter are provided.");
        }

        Map<?, ?> fqdnBuildingBlocks = requireMap(fqdnObj, "FQDN");
        List<Map<String, Object>> disks = requireListOfMap(disksObj, "Disks");

        int ram = Integer.parseInt(ramObj.toString());
        int cpu = Integer.parseInt(cpuObj.toString());
        if (cpu < 2 || cpu > 8 || ram < 4 || ram > 64) {
            log.warn("Invalid CPU or RAM provided by user: {} for new Windows Server Install", AuthUtils.getUsername());
            throw new IllegalArgumentException("CPU must be between 2 and 8, RAM must be between 4 and 64 GB.");
        }

        String osVersion = osVersionObj.toString();
        // Prüft, ob die Version zu Windows 2022 passt (als String oder Enum-Wert)
        if (!osVersion.equals("Windows Server 2022") && !osVersion.equals("WIN2022")) {
            log.warn("Invalid OS Version provided by user: {} for new Windows Server 2022 Install", AuthUtils.getUsername());
            throw new IllegalArgumentException("OS Version is invalid for this endpoint.");
        }

        String categoryType = categoryTypeObj.toString();
        String nonPostgresReason = null;
        Map<?, ?> serverTypeMap = null;
        if (!categoryType.equals("Standard")) {
            serverTypeMap = requireMap(serverTypeObj, "Server Type");
        }
        if (categoryType.equals("DB")) {
            if (nonPostgresReasonObj == null) {
                log.info("Non-Postgres reason not provided by user: {} to order a database server.", AuthUtils.getUsername());
                throw new MissingFormatArgumentException("Non-Postgres reason must be provided for non-Postgres database servers.");
            } else {
                nonPostgresReason = nonPostgresReasonObj.toString();
                checkNonPostgresReason(nonPostgresReason);
            }
        }
        Map<String, Map<?, ?>> dbParams = null;
        if ((categoryType.equals("DB") || categoryType.equals("Mixed")) && dbParamsObj == null) {
            log.info("DB Parameters not provided by user: {} to order a database server.", AuthUtils.getUsername());
            throw new MissingFormatArgumentException("DB Parameters must be provided for database servers.");
        } else if (dbParamsObj != null) {
            dbParams = requireStringMapOfMap(dbParamsObj, "DB Parameters");
        }

        long applicationServiceId = Integer.parseInt(applicationServiceIdObj.toString());
        checkServerInstallCanUserEditAppservice(applicationServiceId);

        long networkGroupId = Integer.parseInt(networkGroupIdObj.toString());
        if (!networkService.isAllowedNetworkGroupForAppservice(networkGroupId, applicationServiceId, categoryTypeObj.toString().equals("DB"))) {
            throw new AccessDeniedException("You are not allowed to order a server for this combination of application service and network group.");
        }

        // Übergabe der spezifischen WINDOWS_SERVER_2022 Konstante an den Service
        jobService.windowsServer(fqdnBuildingBlocks, serverTypeMap, categoryType, ram, cpu,
                disks, networkGroupId, applicationServiceId, osVersion, nonPostgresReason, dbParams, WINDOWS_SERVER_2022);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // DB JOBs
    // -----------------------------------------------------------------------------------------------------------------
    @PostMapping("/create/" + DB_ORACLE_CREATE_BACKUP)
    public void vmwareOracleCreateBackup(@RequestParam(name = "serverId") final Long serverId,
                                         @RequestBody final Map<String, Object> awxExtraVars) {
        if (!serverService.canUserEditServer(serverId)) {
            logTriedToCreateJob(DB_ORACLE_CREATE_BACKUP, serverId);
            throw new AccessDeniedException("You are not allowed to create a job for this server.");
        }

        ServerFullDTO server = serverService.getServerById(serverId);
        if (server.roleOracle() != true) {
            log.warn("User: {} tried to create an Oracle backup job for non-Oracle serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("Server is not an Oracle server.");
        }

        Object flagObj = awxExtraVars.get("flag");
        if (flagObj == null) {
            log.info("Flag value not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("Flag value must be provided.");
        }
        List<String> allowedFlags = List.of("inkrementelle_Sicherung", "volle_Sicherung"); //"offline_Sicherung");
        String flag = flagObj.toString();
        if (!allowedFlags.contains(flag)) {
            log.warn("Invalid flag value provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("Flag value is invalid.");
        }

        logCreatedJob(DB_ORACLE_CREATE_BACKUP, serverId);
        jobService.createOracleBackup(serverId, flag, DB_ORACLE_CREATE_BACKUP);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ANSIBLE JOBs
    // -----------------------------------------------------------------------------------------------------------------
    @PostMapping("/create/" + ANSIBLE_USER_ADD)
    public void ansibleUserAdd(@RequestParam(name = "serverId") final Long serverId,
                               @RequestBody final Map<String, Object> awxExtraVars) {

        final AnsibleUserAddDTO dto = AnsibleUserAddDTO.fromMap(awxExtraVars, ANSIBLE_USER_ADD);

        // check for each server if user has permission
        for (final Long srvId : dto.getServerIds()) {
            if (!serverService.canUserEditServer(srvId)) {
                logTriedToCreateJob(ANSIBLE_USER_ADD, srvId);
                throw new AccessDeniedException("You are not allowed to create a job for server ID: " + srvId);
            }
        }

        for (final Long currentServerId : dto.getServerIds()) {
            logCreatedJob(ANSIBLE_USER_ADD, currentServerId);
            jobService.ansibleUserAdd(dto.getAccountName(), currentServerId, ANSIBLE_USER_ADD);
        }
    }

    @PostMapping("/create/" + ANSIBLE_USER_REMOVE)
    public void ansibleUserRemove(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        Object account_nameObj = awxExtraVars.get("account_name");
        if (account_nameObj == null) {
            log.info("Account name or server IDs not provided by user: {} for job {}", AuthUtils.getUsername(), ANSIBLE_USER_REMOVE);
            throw new MissingFormatArgumentException("Account name and server IDs must be provided.");
        }

        String account_name = account_nameObj.toString();
        if (account_name.isBlank() || !account_name.matches("^svc-ans-[a-z0-9-]{1,12}$")) {
            log.info("Invalid account name provided by user: {} for job {}", AuthUtils.getUsername(), ANSIBLE_USER_REMOVE);
            throw new IllegalArgumentException("Account name is invalid.");
        }

        logCreatedJob(ANSIBLE_USER_REMOVE, serverId);
        jobService.ansibleUserRemove(account_name, ANSIBLE_USER_REMOVE);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Loadbalancer JOBs
    // -----------------------------------------------------------------------------------------------------------------

    @PostMapping("/create/" + LOADBALANCER_F5)
    public void createLoadbalancer(@RequestParam(name = "serverId") final Long serverId,
                                   @RequestBody final Map<String, Object> awxExtraVars) {
        if (!awxExtraVars.containsKey("appservice") || awxExtraVars.get("appservice") == null) {
            throw new MissingFormatArgumentException("Appservice must be provided.");
        }

        final Object appserviceObj = awxExtraVars.get("appservice");
        if (!(appserviceObj instanceof Map<?, ?> appserviceMap)) {
            throw new IllegalArgumentException("Appservice must be provided in the correct format.");
        }
        final Object appserviceIdObj = appserviceMap.get("id");
        if (appserviceIdObj == null) {
            throw new MissingFormatArgumentException("Appservice ID must be provided.");
        }

        final long appserviceId;
        try {
            appserviceId = Long.parseLong(appserviceIdObj.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Appservice ID is invalid.");
        }

        if (appserviceService.getAppservice(appserviceId) == null) {
            throw new AccessDeniedException("You are not allowed to order a loadbalancer for this appservice.");
        }

        if (!awxExtraVars.containsKey("dns") || awxExtraVars.get("dns") == null) {
            throw new MissingFormatArgumentException("DNS entry must be provided.");
        }
        String dns = awxExtraVars.get("dns").toString();
        validateDnsEntry(dns);

        if (!awxExtraVars.containsKey("listener")) {
            throw new MissingFormatArgumentException("Listener configuration must be provided.");
        }
        List<Map<String, Object>> listenerList = requireListOfMap(awxExtraVars.get("listener"), "Listener configuration");
        if (listenerList.isEmpty()) {
            throw new IllegalArgumentException("At least one listener is required.");
        }
        Map<String, Object> listener = listenerList.getFirst();
        Object portObj = listener.get("port");
        if (portObj == null) {
            throw new IllegalArgumentException("Port must be provided.");
        }
        int port;
        try {
            port = Integer.parseInt(portObj.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        listener.put("port", port);
        Object listenerTypeObj = listener.get("listener_type");
        if (!(listenerTypeObj instanceof String listenerType)) {
            throw new IllegalArgumentException("Listener type must be provided.");
        }
        if (!List.of("http", "tcp", "fast-tcp").contains(listenerType)) {
            throw new IllegalArgumentException("Invalid listener type: " + listenerType);
        }

        if (!awxExtraVars.containsKey("server_pools")) {
            throw new MissingFormatArgumentException("Server pools configuration must be provided.");
        }
        List<Map<String, Object>> serverPools = requireListOfMap(awxExtraVars.get("server_pools"), "Server pools configuration");
        if (serverPools.isEmpty()) {
            throw new IllegalArgumentException("At least one server pool is required.");
        }
        Map<String, Object> serverPool = serverPools.getFirst();
        List<Map<String, Object>> memberList = requireListOfMap(serverPool.get("member"), "Server member");
        if (memberList.isEmpty()) {
            throw new IllegalArgumentException("At least one server member is required.");
        }
        for (Map<String, Object> member : memberList) {
            Object ipObj = member.get("ip");
            if (ipObj == null || ipObj.toString().isBlank()) {
                throw new IllegalArgumentException("Member IP is required.");
            }
            final String memberIp = ipObj.toString().trim();
            final List<Server> matchingServers = serverService.findServersByIpAddress(memberIp);
            if (matchingServers.isEmpty()) {
                throw new IllegalArgumentException("No server found for member IP: " + memberIp);
            }
            for (Server matchingServer : matchingServers) {
                if (!serverService.canUserEditServer(matchingServer.getId())) {
                    logTriedToCreateJob(LOADBALANCER_F5, matchingServer.getId());
                    throw new AccessDeniedException("You are not allowed to create a job for all selected member servers.");
                }
            }

            List<?> memberPorts = requireList(member.get("ports"), "Member ports");
            if (memberPorts.isEmpty()) {
                throw new IllegalArgumentException("At least one member port is required.");
            }
            if (memberPorts.size() > 10) {
                throw new IllegalArgumentException("A member may have at most 10 ports.");
            }
            for (Object memberPortObj : memberPorts) {
                int memberPort;
                try {
                    memberPort = Integer.parseInt(memberPortObj.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port format: " + memberPortObj);
                }

                if (memberPort < 1 || memberPort > 65535) {
                    throw new IllegalArgumentException("Member port must be between 1 and 65535.");
                }
            }
        }
        Object lbModeObj = serverPool.get("loadbalancing_mode");
        if (!(lbModeObj instanceof String lbMode)) {
            throw new IllegalArgumentException("Invalid loadbalancing mode: " + lbModeObj);
        }
        if (!List.of("round-robin", "least-connections-member").contains(lbMode)) {
            throw new IllegalArgumentException("Invalid loadbalancing mode: " + lbMode);
        }

        List<?> monitors = serverPool.get("monitors") instanceof List<?> m ? m : List.of();
        if (monitors.isEmpty()) {
            throw new IllegalArgumentException("At least one monitor is required.");
        }
        boolean serversideTls = Boolean.TRUE.equals(listener.get("serverside_tls"));
        java.util.regex.Pattern monitorPathPattern = java.util.regex.Pattern.compile("^/(?!/)[^?#\\s]*(?:\\?[^#\\s]*)?(?:#\\S*)?$");
        for (Object monitorObj : monitors) {
            if (monitorObj instanceof Map<?, ?> monitor) {
                String monitorType = monitor.get("type") != null ? monitor.get("type").toString() : "";
                if (!List.of("http", "https").contains(monitorType)) {
                    throw new IllegalArgumentException("Invalid monitor type: " + monitorType);
                }
                if (serversideTls && "http".equals(monitorType)) {
                    throw new IllegalArgumentException("Monitor type 'http' is not allowed when server pool protocol is 'https'.");
                }
                if (!serversideTls && "https".equals(monitorType)) {
                    throw new IllegalArgumentException("Monitor type 'https' is not allowed when server pool protocol is 'http'.");
                }
                Object path = monitor.get("path");
                if (path == null || path.toString().isBlank()) {
                    throw new IllegalArgumentException("Monitor path is required.");
                }
                if (!monitorPathPattern.matcher(path.toString()).matches()) {
                    throw new IllegalArgumentException("Monitor path is invalid: " + path);
                }
                Object method = monitor.get("method");
                if (!List.of("GET", "HEAD", "OPTIONS").contains(method != null ? method.toString() : "")) {
                    throw new IllegalArgumentException("Invalid monitor method: " + method);
                }
            } else if (!(monitorObj instanceof String s && "tcp".equals(s))) {
                throw new IllegalArgumentException("Invalid monitor entry: " + monitorObj);
            }
        }

        logCreatedJob(LOADBALANCER_F5, serverId);
        jobService.loadbalancerF5(awxExtraVars, LOADBALANCER_F5);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Storage JOBs
    // -----------------------------------------------------------------------------------------------------------------


    @PostMapping("/create/" + STORAGE_MODIFY_NFS)
    public void storageModifyNfs(@RequestParam(name = "serverId") final Long serverId,
                                 @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageModifyShare(
                awxExtraVars,
                serverId,
                StorageType.NFS,
                "NFS",
                STORAGE_MODIFY_NFS
        );
    }

    @PostMapping("/create/" + STORAGE_MODIFY_CIFS)
    public void storageModifyCifs(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageModifyShare(
                awxExtraVars,
                serverId,
                StorageType.CIFS,
                "CIFS",
                STORAGE_MODIFY_CIFS
        );
    }

    private void handleStorageModifyShare(Map<String, Object> awxExtraVars,
                                          Long serverId,
                                          StorageType storageType,
                                          String storageLabel,
                                          String jobIdentifier) {
        Object storageUuidObj = awxExtraVars.get("uuid");
        Object newSizeObj = awxExtraVars.get("new_size");
        Object newSnapshotPercentObj = awxExtraVars.get("new_snapshot_percent");

        if (newSizeObj == null || newSnapshotPercentObj == null || storageUuidObj == null) {
            log.info("{} UUID, new size or new snapshot percentage not provided by user: {} for serverId: {}", storageLabel, AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException(storageLabel + " UUID, new size and new snapshot percentage must be provided.");
        }

        final UnifiedStorageItemDto storageItem;
        try {
            storageItem = unifiedStorageService.getUnifiedStorageItem(storageUuidObj.toString(), storageType);
        } catch (IllegalArgumentException e) {
            log.info("{} UUID provided by user: {} for serverId: {} is invalid.", storageLabel, AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException(storageLabel + " UUID is invalid.");
        }

        final int newSize;
        final int newSnapshotPercent;
        try {
            newSize = Integer.parseInt(newSizeObj.toString());
            newSnapshotPercent = Integer.parseInt(newSnapshotPercentObj.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid {} or {} provided by user: {} for serverId: {} for job {}",
                    "new size", "new snapshot percentage", AuthUtils.getUsername(), serverId, jobIdentifier);
            throw new IllegalArgumentException("The new size and new snapshot percentage must be whole numbers.");
        }

        if (!isEditableStorageCategoryNfsCifs(storageType, storageItem.getStorageCategory())) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        if (!unifiedStorageService.canUserEditStorage(storageItem.getUuid(), storageType)) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        long oldSize = storageItem.getSize() / (1024 * 1024 * 1024);
        long spaceLogicalUsed = storageItem.getSpaceLogicalUsed() == null ? 0L : storageItem.getSpaceLogicalUsed();
        long spaceSnapshotUsed = storageItem.getSpaceSnapshotUsed() == null ? 0L : storageItem.getSpaceSnapshotUsed();
        long spaceLogicalUsedByAfs = storageItem.getSpaceLogicalUsedByAfs() == null ? 0L : storageItem.getSpaceLogicalUsedByAfs();
        if (newSize < 1 || newSize > 2048 ||
                (newSize < oldSize && (
                        spaceLogicalUsed > newSize * 1024L * 1024L * 1024L ||
                        spaceSnapshotUsed > newSize * 1024L * 1024L * 1024L ||
                        spaceLogicalUsedByAfs > newSize * 1024L * 1024L * 1024L)) ||
                newSize > ((3L * oldSize + 1L) / 2L)) {
            log.warn("Invalid new size provided by user: {} for serverId: {} for job {}", AuthUtils.getUsername(), serverId, jobIdentifier);
            throw new IllegalArgumentException("New size must be between 1 and 2048 GB, and can only be smaller than the old size if the used size including snapshots and AFS is smaller than the new size, and can only be bigger than the old size with max 50%.");
        }

        if (newSnapshotPercent < 0 || newSnapshotPercent > 60) {
            log.warn("Invalid new snapshot percentage provided by user: {} for serverId: {} for job {}", AuthUtils.getUsername(), serverId, jobIdentifier);
            throw new IllegalArgumentException("New snapshot percentage must be between 0 and 60.");
        }

        logCreatedJob(jobIdentifier, serverId);
        if (storageType == StorageType.NFS) {
            jobService.storageModifyNfs(storageItem, newSize, newSnapshotPercent);
        } else {
            jobService.storageModifyCifs(storageItem, newSize, newSnapshotPercent);
        }
    }

    @PostMapping("/create/" + STORAGE_CREATE_SNAPSHOT_NFS)
    public void storageCreateSnapshotNfs(@RequestParam(name = "serverId") final Long serverId,
                                  @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageCreateSnapshotShare(
                awxExtraVars,
                serverId,
                StorageType.NFS,
                "NFS",
                STORAGE_CREATE_SNAPSHOT_NFS
        );
    }

    @PostMapping("/create/" + STORAGE_CREATE_SNAPSHOT_CIFS)
    public void storageCreateSnapshotCifs(@RequestParam(name = "serverId") final Long serverId,
                                          @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageCreateSnapshotShare(
                awxExtraVars,
                serverId,
                StorageType.CIFS,
                "CIFS",
                STORAGE_CREATE_SNAPSHOT_CIFS
        );
    }

    private void handleStorageCreateSnapshotShare(Map<String, Object> awxExtraVars,
                                                  Long serverId,
                                                  StorageType storageType,
                                                  String storageLabel,
                                                  String jobIdentifier) {
        Object storageUuidObj = awxExtraVars.get("uuid");
        Object descriptionObj = awxExtraVars.get("description");

        if (storageUuidObj == null || descriptionObj == null) {
            log.info("{} UUID or description not provided by user: {} for serverId: {}", storageLabel, AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException(storageLabel + " UUID and description must be provided.");
        }

        String description = descriptionObj.toString();
        if (!description.matches("^[a-z0-9]{3,20}$")) {
            log.info("Invalid description provided by user: {} for serverId: {} for job {}", AuthUtils.getUsername(), serverId, jobIdentifier);
            throw new IllegalArgumentException("Description must be 3 to 20 characters (only a-z and 0-9).");
        }

        final UnifiedStorageItemDto storageItem;
        try {
            storageItem = unifiedStorageService.getUnifiedStorageItem(storageUuidObj.toString(), storageType);
        } catch (IllegalArgumentException e) {
            log.info("{} UUID provided by user: {} for serverId: {} is invalid.", storageLabel, AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException(storageLabel + " UUID is invalid.");
        }

        if (!isEditableStorageCategoryNfsCifs(storageType, storageItem.getStorageCategory())) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        if (!unifiedStorageService.canUserEditStorage(storageItem.getUuid(), storageType)) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        logCreatedJob(jobIdentifier, serverId);
        if (storageType == StorageType.NFS) {
            jobService.storageCreateSnapshotNfs(storageItem, description);
        } else {
            jobService.storageCreateSnapshotCifs(storageItem, description);
        }
    }

    @PostMapping("/create/" + STORAGE_DELETE_SNAPSHOT_NFS)
    public void storageDeleteSnapshotNfs(@RequestParam(name = "serverId") final Long serverId,
                                          @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageDeleteSnapshotShare(
                awxExtraVars,
                serverId,
                StorageType.NFS,
                "NFS",
                STORAGE_DELETE_SNAPSHOT_NFS
        );
    }

    @PostMapping("/create/" + STORAGE_DELETE_SNAPSHOT_CIFS)
    public void storageDeleteSnapshotCifs(@RequestParam(name = "serverId") final Long serverId,
                                           @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageDeleteSnapshotShare(
                awxExtraVars,
                serverId,
                StorageType.CIFS,
                "CIFS",
                STORAGE_DELETE_SNAPSHOT_CIFS
        );
    }

    private void handleStorageDeleteSnapshotShare(Map<String, Object> awxExtraVars,
                                                   Long serverId,
                                                   StorageType storageType,
                                                   String storageLabel,
                                                   String jobIdentifier) {
        final String snapshotNameRegex = "^[a-z0-9_.-]{3,60}$";
        Object storageUuidObj = awxExtraVars.get("uuid");
        Object snapshotNameObj = awxExtraVars.get("snapshotName");

        if (storageUuidObj == null || snapshotNameObj == null) {
            log.info("{} UUID or snapshotName not provided by user: {} for serverId: {}", storageLabel, AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException(storageLabel + " UUID and snapshotName must be provided.");
        }

        String snapshotName = snapshotNameObj.toString();
        if (!snapshotName.matches(snapshotNameRegex)) {
            log.info("Invalid snapshotName provided by user: {} for serverId: {} for job {}", AuthUtils.getUsername(), serverId, jobIdentifier);
            throw new IllegalArgumentException("Snapshot name must be 3 to 60 characters (only a-z and 0-9 or ._-).");
        }

        final UnifiedStorageItemDto storageItem;
        try {
            storageItem = unifiedStorageService.getUnifiedStorageItem(storageUuidObj.toString(), storageType);
        } catch (IllegalArgumentException e) {
            log.info("{} UUID provided by user: {} for serverId: {} is invalid.", storageLabel, AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException(storageLabel + " UUID is invalid.");
        }

        if (!isEditableStorageCategoryNfsCifs(storageType, storageItem.getStorageCategory())) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        if (!unifiedStorageService.canUserEditStorage(storageItem.getUuid(), storageType)) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        logCreatedJob(jobIdentifier, serverId);
        if (storageType == StorageType.NFS) {
            jobService.storageDeleteSnapshotNfs(storageItem, snapshotName);
        } else {
            jobService.storageDeleteSnapshotCifs(storageItem, snapshotName);
        }
    }

    @PostMapping("/create/" + STORAGE_CHANGE_SNAPSHOT_POLICY_NFS)
    public void storageChangeSnapshotPolicyNfs(@RequestParam(name = "serverId") final Long serverId,
                                          @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageChangeSnapshotPolicyShare(
                awxExtraVars,
                serverId,
                StorageType.NFS,
                "NFS",
                STORAGE_CHANGE_SNAPSHOT_POLICY_NFS
        );
    }

    @PostMapping("/create/" + STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS)
    public void storageChangeSnapshotPolicyCifs(@RequestParam(name = "serverId") final Long serverId,
                                           @RequestBody final Map<String, Object> awxExtraVars) {
        handleStorageChangeSnapshotPolicyShare(
                awxExtraVars,
                serverId,
                StorageType.CIFS,
                "CIFS",
                STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS
        );
    }

    @PostMapping("/create/" + STORAGE_CHANGE_NFS_EXPORT_POLICY)
    public void storageChangeNfsExportPolicy(@RequestParam(name = "serverId") final Long serverId,
                                                @RequestBody final Map<String, Object> awxExtraVars) {
        Object storageUuidObj = awxExtraVars.get("uuid");
        Object fqdnObj = awxExtraVars.get("fqdn");
        Object permissionObj = awxExtraVars.get("permission");

        if (storageUuidObj == null || fqdnObj == null || permissionObj == null) {
            log.info("NFS UUID, fqdn or permission not provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException("NFS UUID, fqdn and permission must be provided.");
        }

        String fqdn = fqdnObj.toString();
        String permission = permissionObj.toString();

        if (fqdn.isBlank()) {
            log.info("FQDN is blank provided by user: {} for serverId: {}", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("FQDN must not be blank.");
        }

        if (!permission.equals("rw") && !permission.equals("ro")) {
            throw new IllegalArgumentException("Permission must be either 'rw' or 'ro'.");
        }

        List<Server> matchingServers = serverService.findByFqdnIn(Collections.singletonList(fqdn));
        if (matchingServers == null || matchingServers.isEmpty()) {
            log.info("FQDN provided by user: {} for serverId: {} is not a valid server.", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("FQDN does not correspond to a valid server.");
        }

        final UnifiedStorageItemDto storageItem;
        try {
            storageItem = unifiedStorageService.getUnifiedStorageItem(storageUuidObj.toString(), StorageType.NFS);
        } catch (IllegalArgumentException e) {
            log.info("NFS UUID provided by user: {} for serverId: {} is invalid.", AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException("NFS UUID is invalid.");
        }

        if (!isEditableStorageCategoryNfsCifs(StorageType.NFS, storageItem.getStorageCategory())) {
            logTriedToCreateJob(STORAGE_CHANGE_NFS_EXPORT_POLICY, null);
            throw new AccessDeniedException("You are not allowed to create a job for this NFS share.");
        }

        if (!unifiedStorageService.canUserEditStorage(storageItem.getUuid(), StorageType.NFS)) {
            logTriedToCreateJob(STORAGE_CHANGE_NFS_EXPORT_POLICY, null);
            throw new AccessDeniedException("You are not allowed to create a job for this NFS share.");
        }

        logCreatedJob(STORAGE_CHANGE_NFS_EXPORT_POLICY, serverId);
        jobService.storageChangeNfsExportPolicy(storageItem, fqdn, permission);
    }

    private void handleStorageChangeSnapshotPolicyShare(Map<String, Object> awxExtraVars,
                                                   Long serverId,
                                                   StorageType storageType,
                                                   String storageLabel,
                                                   String jobIdentifier) {
        Object storageUuidObj = awxExtraVars.get("uuid");
        Object newPolicyObj = awxExtraVars.get("newPolicy");

        if (storageUuidObj == null || newPolicyObj == null) {
            log.info("{} UUID or newPolicy not provided by user: {} for serverId: {}", storageLabel, AuthUtils.getUsername(), serverId);
            throw new MissingFormatArgumentException(storageLabel + " UUID and newPolicy must be provided.");
        }

        String newPolicy = newPolicyObj.toString();
        if (!newPolicy.equals("dcc-6h") && !newPolicy.equals("dcc-24h") && !newPolicy.equals("dcc-24h4d") && !newPolicy.equals("dcc-24h7d") && !newPolicy.equals("none")) {
            log.info("Invalid newPolicy provided by user: {} for serverId: {} for job {}", AuthUtils.getUsername(), serverId, jobIdentifier);
            throw new IllegalArgumentException("Snapshot policy must be a valid predefined value.");
        }

        final UnifiedStorageItemDto storageItem;
        try {
            storageItem = unifiedStorageService.getUnifiedStorageItem(storageUuidObj.toString(), storageType);
        } catch (IllegalArgumentException e) {
            log.info("{} UUID provided by user: {} for serverId: {} is invalid.", storageLabel, AuthUtils.getUsername(), serverId);
            throw new IllegalArgumentException(storageLabel + " UUID is invalid.");
        }

        if (!isEditableStorageCategoryNfsCifs(storageType, storageItem.getStorageCategory())) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        if (!unifiedStorageService.canUserEditStorage(storageItem.getUuid(), storageType)) {
            logTriedToCreateJob(jobIdentifier, null);
            throw new AccessDeniedException("You are not allowed to create a job for this " + storageLabel + " share.");
        }

        logCreatedJob(jobIdentifier, serverId);
        if (storageType == StorageType.NFS) {
            jobService.storageChangeSnapshotPolicyNfs(storageItem, newPolicy);
        } else {
            jobService.storageChangeSnapshotPolicyCifs(storageItem, newPolicy);
        }
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------------------------------------------------

    private void checkNonPostgresReason(String reason) {
        if (reason == null || reason.isBlank()) {
            log.warn("User {} tried to create a non-Postgres job without providing a reason.", AuthUtils.getUsername());
            throw new IllegalArgumentException("A reason must be provided for non-Postgres jobs.");
        } else if (reason.length() > 500) {
            log.warn("User {} provided a reason that is too long for a non-Postgres job.", AuthUtils.getUsername());
            throw new IllegalArgumentException("The reason must not exceed 500 characters.");
        } else if (reason.length() < 25) {
            log.warn("User {} provided a reason that is too short for a non-Postgres job.", AuthUtils.getUsername());
            throw new IllegalArgumentException("The reason must be at least 25 characters long.");
        }
    }

    private void logCreatedJob(String jobIdentifier, Long serverId) {
        log.info("Job {} created by user: {} for serverId: {}", jobIdentifier, AuthUtils.getUsername(), serverId);
    }

    private void logTriedToCreateJob(String jobIdentifier, Long serverId) {
        log.warn("User {} tried to create a job {} for serverId: {} without permission.", AuthUtils.getUsername(), jobIdentifier, serverId);
    }

    private boolean isEditableStorageCategoryNfsCifs(StorageType storageType, StorageCategory category) {
        if (storageType == StorageType.NFS) {
            return category == StorageCategory.NFS_STANDARD_SHARE
                    || category == StorageCategory.NFS_CLONE
                    || category == StorageCategory.NFS_WORM;
        }
        if (storageType == StorageType.CIFS) {
            return category == StorageCategory.CIFS_STANDARD_SHARE
                    || category == StorageCategory.CIFS_CLONE
                    || category == StorageCategory.CIFS_WORM;
        }
        return false;
    }

    private Map<?, ?> requireMap(Object obj, String fieldLabel) {
        if (!(obj instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(fieldLabel + " must be provided in the correct format.");
        }
        return map;
    }

    private List<?> requireList(Object obj, String fieldLabel) {
        if (!(obj instanceof List<?> list)) {
            throw new IllegalArgumentException(fieldLabel + " must be provided in the correct format.");
        }
        return list;
    }

    private List<Map<String, Object>> requireListOfMap(Object obj, String fieldLabel) {
        List<?> list = requireList(obj, fieldLabel);
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(fieldLabel + " must be provided in the correct format.");
            }
            result.add((Map<String, Object>) map);
        }
        return result;
    }

    private Map<String, Map<?, ?>> requireStringMapOfMap(Object obj, String fieldLabel) {
        Map<?, ?> raw = requireMap(obj, fieldLabel);
        Map<String, Map<?, ?>> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> value)) {
                throw new IllegalArgumentException(fieldLabel + " must be provided in the correct format.");
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * Validate a DNS entry in linear time without using complex regular expressions
     * to avoid ReDoS issues. Enforces lowercase a-z, digits and hyphen, labels
     * separated by dots, first label length 3..64 and overall length <= 255.
     * Throws IllegalArgumentException on invalid input.
     */
    private void validateDnsEntry(String dns) {
        if (dns == null) {
            throw new IllegalArgumentException("Invalid DNS entry.");
        }

        // overall length bounds
        if (dns.length() < 3 || dns.length() > 255) {
            throw new IllegalArgumentException("Invalid DNS entry.");
        }

        // fast fail for obvious bad inputs
        if (dns.charAt(0) == '-' || dns.charAt(dns.length() - 1) == '-') {
            throw new IllegalArgumentException("Invalid DNS entry.");
        }

        String[] labels = dns.split("\\.");
        if (labels.length < 2) {
            throw new IllegalArgumentException("Invalid DNS entry.");
        }

        String first = labels[0];
        if (first.length() < 3 || first.length() > 64) {
            throw new IllegalArgumentException("Invalid DNS entry.");
        }

        for (String label : labels) {
            if (label.isEmpty() || label.length() > 64) {
                throw new IllegalArgumentException("Invalid DNS entry.");
            }
            // label must not start or end with hyphen
            if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
                throw new IllegalArgumentException("Invalid DNS entry.");
            }
            // only allow a-z, 0-9 and '-'
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
                    throw new IllegalArgumentException("Invalid DNS entry.");
                }
            }
        }
    }

    private void checkServerInstallCanUserEditAppservice(final Long appserviceId) {
        if (!appserviceService.canUserEditAppservice(appserviceId)) {
            throw new AccessDeniedException("You are not allowed to create a Server for this Application Service.");
        }
    }
}
