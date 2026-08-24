package de.muenchen.mcmp.clients.awx;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.temporaryPrivileges.PrivilegeType;
import de.muenchen.mcmp.temporaryPrivileges.TemporaryPrivilege;
import de.muenchen.mcmp.temporaryPrivileges.TemporaryPrivilegeService;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserService;
import de.muenchen.mcmp.utils.DateTimeUtils;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST Controller for AWX integration.
 * Provides endpoints for AWX EAI (Enterprise Application Integration) to import
 * and manage temporary privileges for users on servers.
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/awx")
public class AwxController {

    private final TemporaryPrivilegeService temporaryPrivilegeService;
    private final ServerService serverService;
    private final UserService userService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter GERMAN_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");

    /**
     * Health check endpoint for AWX integration.
     * <p>
     * This endpoint provides a simple connectivity test for the AWX EAI API.
     * It can be used by monitoring systems or AWX itself to verify that the
     * integration endpoint is accessible and responding.
     *
     * @return A string indicating that the AWX EAI API is accessible
     */
    @GetMapping("/ping")
    public String ping() {
        return "AWX EAI API is accessible";
    }

    /**
     * Processes AWX inventory data and imports temporary privileges.
     * <p>
     * This method is the main entry point for AWX to push inventory data containing
     * information about temporary privileges granted to users on Linux and Windows hosts.
     * The method performs the following operations:
     * <p>
     * 1. Collects all hosts from both Linux and Windows host lists
     * 2. Retrieves matching servers from the database based on FQDN
     * 3. Retrieves or creates users based on usernames from the inventory data
     * 4. Processes each host entry to create or update temporary privileges
     * 5. Removes temporary privileges that are no longer present in the import data
     * <p>
     * The import is designed to be idempotent - running it multiple times with the
     * same data will not create duplicates.
     *
     * @param inventoryDTO The inventory data containing Linux and Windows hosts with their privileges
     * @throws IllegalArgumentException if date parsing fails for validUntil field
     */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public void processAwxData(@Valid @RequestBody final InventoryDTO inventoryDTO) {
        log.info("Received AWX data with {} root Linux hosts, {} Admin Windows hosts and {} Windows in MaintenanceMode.",
                inventoryDTO.linuxHosts() != null ? inventoryDTO.linuxHosts().size() : 0,
                inventoryDTO.windowsHosts() != null ? inventoryDTO.windowsHosts().size() : 0,
                inventoryDTO.windowsMaintenanceModeHosts() != null ? inventoryDTO.windowsMaintenanceModeHosts().size() : 0);

        // Both processes should ideally run in their own transactions or the service handles this.
        // Here we call the methods which should manage transactions themselves.
        processTemporaryPrivileges(inventoryDTO);
        processWindowsMaintenanceMode(inventoryDTO);
    }

    private void processTemporaryPrivileges(final InventoryDTO inventoryDTO) {
        // Collect all InventoryHostDTO from Linux and Windows hosts
        List<InventoryDTO.InventoryHostDTO> allHosts = new ArrayList<>();
        if (inventoryDTO.linuxHosts() != null) {
            allHosts.addAll(inventoryDTO.linuxHosts());
        }
        if (inventoryDTO.windowsHosts() != null) {
            allHosts.addAll(inventoryDTO.windowsHosts());
        }

        if (allHosts.isEmpty()) {
            log.info("No hosts to process in import. Deleting all temporary privileges.");
            temporaryPrivilegeService.deleteAll();
            return;
        }

        // 1. Read all matching servers from the database
        Set<String> fqdns = allHosts.stream()
                .map(InventoryDTO.InventoryHostDTO::fqdn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Server> matchingServers = serverService.findByFqdnIn(new ArrayList<>(fqdns));
        Map<String, Server> serverByFqdn = matchingServers.stream()
                .collect(Collectors.toMap(Server::getFqdn, Function.identity(), (s1, s2) -> s1));
        log.info("Found {} matching servers for {} FQDNs", matchingServers.size(), fqdns.size());

        // 2. Read all matching users from the database or create them
        Set<String> usernames = allHosts.stream()
                .map(InventoryDTO.InventoryHostDTO::user)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<User> existingUsers = userService.findByUsernameIn(new ArrayList<>(usernames));
        Map<String, User> userByUsername = new HashMap<>(existingUsers.stream()
                .collect(Collectors.toMap(User::getUsername, Function.identity())));

        // Create missing users
        for (final String username : usernames) {
            userByUsername.computeIfAbsent(username, u -> {
                try {
                    final User newUser = new User();
                    newUser.setUsername(u);
                    newUser.setSysId(u);
                    newUser.setDepartment("Unknown");
                    newUser.setAdmin(false);
                    return userService.save(newUser);
                } catch (DataIntegrityViolationException e) {
                    // Race Condition: User was created ms ago by another thread, fetching from DB.
                    log.debug("User {} already exists (concurrent creation), fetching from DB.", u);
                    return userService.findByUsername(u).orElseThrow();
                }
            });
        }

        // 3. Read all existing TemporaryPrivileges
        List<TemporaryPrivilege> allExistingPrivileges = temporaryPrivilegeService.findAll();
        Set<Long> privilegeIdsToKeep = new HashSet<>();

        // 4. Process import data - update or create new entries
        for (InventoryDTO.InventoryHostDTO hostDTO : allHosts) {
            if (hostDTO.fqdn() == null || hostDTO.user() == null || hostDTO.created() == null || hostDTO.validUntil() == null ||
                hostDTO.user().isBlank() || hostDTO.fqdn().isBlank() || hostDTO.validUntil().isBlank()) {
                continue;
            }

            final Server server = serverByFqdn.get(hostDTO.fqdn());
            final User user = userByUsername.get(hostDTO.user());
            final PrivilegeType privilegeType = determinePrivilegeType(hostDTO, inventoryDTO);

            if (server == null) {
                log.warn("Server with FQDN {} not found, skipping", hostDTO.fqdn());
                continue;
            }

            if (user == null) {
                log.warn("User {} not found, skipping", hostDTO.user());
                continue;
            }

            try {
                final OffsetDateTime expiresAt = parseValidUntil(hostDTO.validUntil());
                final OffsetDateTime grantedAt = hostDTO.created().atOffset(OffsetDateTime.now().getOffset());

                // Find entry by unique business key (User, Server, Type)
                final Optional<TemporaryPrivilege> existingPrivilege = allExistingPrivileges.stream()
                        .filter(tp ->
                                tp.getUser().getUsername().equals(user.getUsername()) &&
                                        tp.getServer().getFqdn().equals(server.getFqdn()) &&
                                        tp.getPrivilegeType() == privilegeType
                        )
                        .findFirst();

                if (existingPrivilege.isPresent()) {
                    TemporaryPrivilege tp = existingPrivilege.get();

                    // Update timestamps if they changed
                    if (DateTimeUtils.isDateTimeDifferentUTC(tp.getExpiresAt(), expiresAt) ||
                            DateTimeUtils.isDateTimeDifferentUTC(tp.getGrantedAt(), grantedAt)) {

                        tp.setExpiresAt(expiresAt);
                        tp.setGrantedAt(grantedAt);
                        temporaryPrivilegeService.save(tp);

                        log.debug("Updated existing temporary privilege for user {} on server {} (ID: {})",
                                user.getUsername(), server.getFqdn(), tp.getId());
                    } else {
                        log.debug("Found exact match for user {} on server {} - keeping existing entry with ID {}",
                                user.getUsername(), server.getFqdn(), tp.getId());
                    }

                    privilegeIdsToKeep.add(tp.getId());
                } else {
                    // No identical entry found - create new entry
                    final TemporaryPrivilege newPrivilege = new TemporaryPrivilege();
                    newPrivilege.setUser(user);
                    newPrivilege.setServer(server);
                    newPrivilege.setExpiresAt(expiresAt);
                    newPrivilege.setPrivilegeType(privilegeType);
                    newPrivilege.setGrantedAt(grantedAt);

                    TemporaryPrivilege savedPrivilege = temporaryPrivilegeService.save(newPrivilege);
                    privilegeIdsToKeep.add(savedPrivilege.getId());

                    log.debug("Created new temporary privilege for user {} on server {} with type {} (ID: {})",
                            user.getUsername(), server.getFqdn(), privilegeType, savedPrivilege.getId());
                }

            } catch (Exception e) {
                log.error("Error processing temporary privilege for user {} on server {}: {}",
                        user.getUsername(), server.getFqdn(), e.getMessage());
            }
        }

        // 5. Delete all entries not present in import
        final List<Long> privilegesToDelete = allExistingPrivileges.stream()
                .map(TemporaryPrivilege::getId)
                .filter(id -> !privilegeIdsToKeep.contains(id))
                .toList();

        if (!privilegesToDelete.isEmpty()) {
            temporaryPrivilegeService.deleteAll(privilegesToDelete);
            log.info("Deleted {} temporary privileges that are no longer present in import",
                    privilegesToDelete.size());
        }

        log.info("Successfully processed temporary privileges: {} kept/created, {} deleted",
                privilegeIdsToKeep.size(), privilegesToDelete.size());
    }

    /**
     * Processes the Windows maintenance mode hosts from the inventory.
     * Updates the maintenance mode status and expiration date for servers found in the import.
     * Servers that are currently in maintenance mode but not present in the import will have their maintenance mode disabled.
     * Uses direct database updates to avoid optimistic locking issues.
     *
     * @param inventoryDTO The inventory data containing Windows maintenance mode hosts
     */
    private void processWindowsMaintenanceMode(final InventoryDTO inventoryDTO) {
        List<InventoryDTO.InventoryHostDTO> maintenanceHosts = inventoryDTO.windowsMaintenanceModeHosts();
        if (maintenanceHosts == null) {
            maintenanceHosts = Collections.emptyList();
        }

        // 1. Get servers that are currently in maintenance mode
        final List<Server> currentMaintenanceServers = serverService.findByMaintenanceModeTrue();
        final Set<Long> serverIdsInImport = new HashSet<>();

        // 2. Prepare servers for import
        final Set<String> importFqdns = maintenanceHosts.stream()
                .map(InventoryDTO.InventoryHostDTO::fqdn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Optimization: Build Case-Insensitive Map directly when loading, if possible,
        // or here in memory.
        final List<Server> importServers = serverService.findByFqdnIn(new ArrayList<>(importFqdns));
        final Map<String, Server> serverByFqdn = importServers.stream()
                .collect(Collectors.toMap(s -> s.getFqdn().toLowerCase(), Function.identity(), (s1, s2) -> s1));

        // 3. Update imported servers (set maintenance mode)
        for (final InventoryDTO.InventoryHostDTO hostDTO : maintenanceHosts) {
            if (hostDTO.fqdn() == null) continue;

            final Server server = serverByFqdn.get(hostDTO.fqdn().toLowerCase());
            if (server != null) {
                serverIdsInImport.add(server.getId());
                final OffsetDateTime expiresAt = parseValidUntil(hostDTO.validUntil());

                // ONLY execute update if values change to reduce DB load
                if (!server.getMaintenanceMode() || DateTimeUtils.isDateTimeDifferentUTC(server.getMaintenanceModeExpiresAt(), expiresAt)) {
                    // ROBUST UPDATE: We use direct update via query here.
                    // This ignores "Optimistic Locking" errors caused by vCenter updates,
                    // since we explicitly write only these two fields.
                    serverService.updateMaintenanceMode(server.getId(), true, expiresAt);
                }
            } else {
                log.warn("Maintenance Mode Import: Server {} not found in DB.", hostDTO.fqdn());
            }
        }

        // 4. Reset servers that are NOT in the import anymore
        for (final Server server : currentMaintenanceServers) {
            if (!serverIdsInImport.contains(server.getId())) {
                // Auch hier robustes Update
                serverService.updateMaintenanceMode(server.getId(), false, null);
            }
        }
    }

    /**
     * Parses the validUntil string from AWX inventory data into OffsetDateTime.
     * <p>
     * This method handles multiple date formats that might be received from AWX:
     * - ISO format with T and Z: "2027-06-21T11:38:26.000000Z"
     * - Date and time with space (Berlin timezone): "2025-09-28 17:17:17"
     * - ISO format without Z: "2027-06-21T11:38:26.000000"
     * <p>
     * If parsing fails or the input is null/blank, it returns null.
     *
     * @param validUntil The date string to parse
     * @return OffsetDateTime representing when the privilege expires
     */
    private OffsetDateTime parseValidUntil(String validUntil) {
        if (validUntil == null || validUntil.isBlank()) {
            // Fallback: 30 days from now
            return null;
        }

        try {
            // Format: "2027-06-21T11:38:26.000000Z" - ISO format with T and Z
            if (validUntil.contains("T") && validUntil.contains("Z")) {
                return OffsetDateTime.parse(validUntil);
            }
            // Format: "2025-09-28 17:17:17" - Date and time with space (Berlin timezone)
            else if (validUntil.contains(" ") && validUntil.split(" ").length == 2) {
                LocalDateTime localDateTime;
                if (validUntil.contains(".")) {
                    localDateTime = LocalDateTime.parse(validUntil, GERMAN_DATE_TIME_FORMATTER);
                } else {
                    localDateTime = LocalDateTime.parse(validUntil, DATE_TIME_FORMATTER);
                }
                return localDateTime.atZone(BERLIN_ZONE).toOffsetDateTime();
            }
            // Fallback: try ISO format without Z
            else if (validUntil.contains("T")) {
                return OffsetDateTime.parse(validUntil);
            }
            else {
                throw new IllegalArgumentException("Unknown date format: " + validUntil);
            }
        } catch (Exception e) {
            log.warn("Could not parse validUntil '{}', exception: {}", validUntil, e.getMessage());
            return null;
        }
    }

    /**
     * Determines the privilege type based on which host list contains the host.
     * <p>
     * This method checks whether the host is in the Linux hosts list (returns ROOT privilege)
     * or in the Windows hosts list (returns ADMIN privilege). The privilege type determines
     * what kind of elevated access the user has on the target system.
     *
     * @param hostDTO The host data transfer object containing host information
     * @param inventoryDTO The complete inventory containing both Linux and Windows host lists
     * @return PrivilegeType.ROOT for Linux hosts, PrivilegeType.ADMIN for Windows hosts
     */
    private PrivilegeType determinePrivilegeType(InventoryDTO.InventoryHostDTO hostDTO, InventoryDTO inventoryDTO) {
        // Check if the host is in the Linux list
        if (inventoryDTO.linuxHosts() != null && inventoryDTO.linuxHosts().contains(hostDTO)) {
            return PrivilegeType.ROOT;
        }

        // Check if the host is in the Windows list
        if (inventoryDTO.windowsHosts() != null && inventoryDTO.windowsHosts().contains(hostDTO)) {
            return PrivilegeType.ADMIN;
        }

        // Fallback: should not occur as we collect all hosts from both lists
        log.warn("Could not determine privilege type for host {}, defaulting to ROOT", hostDTO.fqdn());
        return PrivilegeType.ROOT;
    }
}
