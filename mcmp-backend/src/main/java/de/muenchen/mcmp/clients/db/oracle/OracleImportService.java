package de.muenchen.mcmp.clients.db.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.muenchen.mcmp.database.*;
import de.muenchen.mcmp.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OracleImportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabasePdbInstanceRepository databasePdbInstanceRepository;
    private final DatabasePdbUserRepository databasePdbUserRepository;
    private final DatabasePdbTablespaceRepository databasePdbTablespaceRepository;
    private final ObjectMapper objectMapper;

    public void importData(final OracleDTO dto) {
        if (dto == null || dto.databases() == null || dto.databases().isEmpty()) {
            log.warn("Oracle DB import DTO or databases list is empty, skipping.");
            return;
        }

        final long startTime = System.currentTimeMillis();
        log.info("Starting optimized in-memory Oracle DB import synchronization for {} databases.", dto.databases().size());

        // 1. Alle FQDN + PDB Zuordnungen direkt in die Lookup-Map laden (ohne Zwischenspeicherung der Liste)
        final Map<String, Long> lookupMap = databasePdbInstanceRepository
                .findManagedPoweredOnOracleServerPdbInstances()
                .stream()
                .filter(l -> l.getFqdn() != null && l.getPdb() != null && l.getPdbInstanceId() != null)
                .collect(Collectors.toMap(
                        l -> buildLookupKey(l.getFqdn(), l.getPdb()),
                        DatabasePdbInstanceServerDTO::getPdbInstanceId,
                        (existing, replacement) -> existing
                ));

        // 2. Alle DatabasePdbInstances auf einmal laden (Key: id)
        final Map<Long, DatabasePdbInstance> pdbInstanceMap = databasePdbInstanceRepository.findAll().stream()
                .collect(Collectors.toMap(DatabasePdbInstance::getId, Function.identity()));

        // 3. Alle Users und Tablespaces auf einmal laden und nach pdbInstanceId gruppieren
        final Map<Long, List<DatabasePdbUser>> usersByPdbId = databasePdbUserRepository.findAll().stream()
                .collect(Collectors.groupingBy(u -> u.getDatabasePdbInstance().getId()));

        final Map<Long, List<DatabasePdbTablespace>> tablespacesByPdbId = databasePdbTablespaceRepository.findAll().stream()
                .collect(Collectors.groupingBy(t -> t.getDatabasePdbInstance().getId()));

        // Listen für Batch-Persistierung
        final List<DatabasePdbUser> usersToSave = new ArrayList<>();
        final List<DatabasePdbUser> usersToDelete = new ArrayList<>();
        final List<DatabasePdbTablespace> tablespacesToSave = new ArrayList<>();
        final List<DatabasePdbTablespace> tablespacesToDelete = new ArrayList<>();

        int matchedDatabases = 0;
        int updatedInstancesCount = 0;

        for (final OracleDTO.DatabaseEntryDTO dbEntry : dto.databases()) {
            if (dbEntry.fqdn() == null || dbEntry.pdb() == null) {
                continue;
            }

            final String lookupKey = buildLookupKey(dbEntry.fqdn(), dbEntry.pdb());
            final Long pdbInstanceId = lookupMap.get(lookupKey);

            if (pdbInstanceId == null) {
                log.debug("No matching DatabasePdbInstance found for lookup key '{}'. Skipping.", lookupKey);
                continue;
            }

            final DatabasePdbInstance pdbInstance = pdbInstanceMap.get(pdbInstanceId);
            if (pdbInstance == null) {
                continue;
            }

            matchedDatabases++;

            if (dbEntry.data() == null) {
                continue;
            }

            for (final OracleDTO.QueryDataDTO queryData : dbEntry.data()) {
                if (queryData == null || queryData.queryName() == null) {
                    continue;
                }

                switch (queryData.queryName()) {
                    case "instance_info" -> {
                        if (checkAndUpdateInstanceInfo(pdbInstance, queryData, dbEntry.timestamp())) {
                            databasePdbInstanceRepository.updateInstanceInfo(
                                    pdbInstance.getId(),
                                    pdbInstance.getPdbName(),
                                    pdbInstance.getPdbHostName(),
                                    pdbInstance.getPdbCharacterset(),
                                    pdbInstance.getPdbDatabaseType(),
                                    pdbInstance.getPdbStartupTime(),
                                    pdbInstance.getPdbCollectedAt()
                            );
                            updatedInstancesCount++;
                        }
                    }
                    case "user_info" -> diffUserInfo(pdbInstance, queryData,
                            usersByPdbId.getOrDefault(pdbInstance.getId(), Collections.emptyList()),
                            usersToSave, usersToDelete);

                    case "tablespace_info" -> diffTablespaceInfo(pdbInstance, queryData,
                            tablespacesByPdbId.getOrDefault(pdbInstance.getId(), Collections.emptyList()),
                            tablespacesToSave, tablespacesToDelete);

                    default -> log.trace("Ignoring queryName '{}'", queryData.queryName());
                }
            }
        }

        // 4. Batch-Operationen ausführen (nur falls Änderungen vorliegen)
        if (!usersToSave.isEmpty()) {
            databasePdbUserRepository.saveAll(usersToSave);
        }
        if (!usersToDelete.isEmpty()) {
            databasePdbUserRepository.deleteAll(usersToDelete);
        }
        if (!tablespacesToSave.isEmpty()) {
            databasePdbTablespaceRepository.saveAll(tablespacesToSave);
        }
        if (!tablespacesToDelete.isEmpty()) {
            databasePdbTablespaceRepository.deleteAll(tablespacesToDelete);
        }

        log.info("Oracle DB import finished in {}ms. Matched PDBs: {}/{}, Instances updated: {}, Users to save/delete: {}/{}, Tablespaces to save/delete: {}/{}",
                System.currentTimeMillis() - startTime, matchedDatabases, dto.databases().size(),
                updatedInstancesCount, usersToSave.size(), usersToDelete.size(),
                tablespacesToSave.size(), tablespacesToDelete.size());
    }

    private static String buildLookupKey(final String fqdn, final String pdb) {
        return fqdn.trim().toLowerCase() + ":" + pdb.trim().toLowerCase();
    }

    private boolean checkAndUpdateInstanceInfo(final DatabasePdbInstance pdbInstance,
                                               final OracleDTO.QueryDataDTO queryData,
                                               final OffsetDateTime collectedAt) {
        if (queryData.rows() == null || queryData.rows().isEmpty()) {
            return false;
        }

        try {
            final OracleDTO.InstanceInfoRowDTO row =
                    objectMapper.treeToValue(queryData.rows().getFirst(), OracleDTO.InstanceInfoRowDTO.class);

            final OffsetDateTime startupTime = parseDateTime(row.startupTime());

            boolean changed = !Objects.equals(pdbInstance.getPdbName(), row.pdbName()) ||
                    !Objects.equals(pdbInstance.getPdbHostName(), row.hostName()) ||
                    !Objects.equals(pdbInstance.getPdbCharacterset(), row.characterset()) ||
                    !Objects.equals(pdbInstance.getPdbDatabaseType(), row.databaseType()) ||
                    !DateTimeUtils.isDateTimeEqualUTC(pdbInstance.getPdbStartupTime(), startupTime) ||
                    !DateTimeUtils.isDateTimeEqualUTC(pdbInstance.getPdbCollectedAt(), collectedAt);


            if (changed) {
                pdbInstance.setPdbName(row.pdbName());
                pdbInstance.setPdbHostName(row.hostName());
                pdbInstance.setPdbCharacterset(row.characterset());
                pdbInstance.setPdbDatabaseType(row.databaseType());
                pdbInstance.setPdbStartupTime(startupTime);
                pdbInstance.setPdbCollectedAt(collectedAt);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to parse instance_info for PDB id={}", pdbInstance.getId(), e);
        }

        return false;
    }

    private void diffUserInfo(final DatabasePdbInstance pdbInstance,
                              final OracleDTO.QueryDataDTO queryData,
                              final List<DatabasePdbUser> existingUsers,
                              final List<DatabasePdbUser> usersToSave,
                              final List<DatabasePdbUser> usersToDelete) {

        final Map<String, DatabasePdbUser> existingUserMap = existingUsers.stream()
                .collect(Collectors.toMap(u -> u.getUserName().trim().toUpperCase(), Function.identity(), (a, b) -> a));

        final Set<String> importedUserNames = new HashSet<>();

        if (queryData.rows() != null) {
            for (final var rowNode : queryData.rows()) {
                try {
                    final OracleDTO.UserInfoRowDTO row =
                            objectMapper.treeToValue(rowNode, OracleDTO.UserInfoRowDTO.class);

                    if (row.userName() == null || row.userName().isBlank()) {
                        continue;
                    }

                    final String userNameKey = row.userName().trim().toUpperCase();
                    importedUserNames.add(userNameKey);

                    final DatabasePdbUser existing = existingUserMap.get(userNameKey);
                    final OffsetDateTime lastLogin = parseDateTime(row.lastLogin());

                    if (existing == null) {
                        final DatabasePdbUser newUser = new DatabasePdbUser();
                        newUser.setDatabasePdbInstance(pdbInstance);
                        newUser.setUserName(row.userName().trim());
                        newUser.setAccountStatus(row.accountStatus());
                        newUser.setLastLogin(lastLogin);
                        newUser.setProfile(row.profile());
                        newUser.setTablespaces(row.tablespaces());
                        usersToSave.add(newUser);
                    } else {
                        boolean changed = !Objects.equals(existing.getAccountStatus(), row.accountStatus()) ||
                                !Objects.equals(existing.getLastLogin(), lastLogin) ||
                                !Objects.equals(existing.getProfile(), row.profile()) ||
                                !Objects.equals(existing.getTablespaces(), row.tablespaces());

                        if (changed) {
                            existing.setAccountStatus(row.accountStatus());
                            existing.setLastLogin(lastLogin);
                            existing.setProfile(row.profile());
                            existing.setTablespaces(row.tablespaces());
                            usersToSave.add(existing);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing user_info row for PDB id={}", pdbInstance.getId(), e);
                }
            }
        }

        for (final DatabasePdbUser existing : existingUsers) {
            if (!importedUserNames.contains(existing.getUserName().trim().toUpperCase())) {
                usersToDelete.add(existing);
            }
        }
    }

    private void diffTablespaceInfo(final DatabasePdbInstance pdbInstance,
                                    final OracleDTO.QueryDataDTO queryData,
                                    final List<DatabasePdbTablespace> existingTablespaces,
                                    final List<DatabasePdbTablespace> tablespacesToSave,
                                    final List<DatabasePdbTablespace> tablespacesToDelete) {

        final Map<String, DatabasePdbTablespace> existingTablespaceMap = existingTablespaces.stream()
                .collect(Collectors.toMap(t -> t.getTablespaceName().trim().toUpperCase(), Function.identity(), (a, b) -> a));

        final Set<String> importedTablespaceNames = new HashSet<>();

        if (queryData.rows() != null) {
            for (final var rowNode : queryData.rows()) {
                try {
                    final OracleDTO.TablespaceInfoRowDTO row =
                            objectMapper.treeToValue(rowNode, OracleDTO.TablespaceInfoRowDTO.class);

                    if (row.tablespaceName() == null || row.tablespaceName().isBlank()) {
                        continue;
                    }

                    final String tablespaceNameKey = row.tablespaceName().trim().toUpperCase();
                    importedTablespaceNames.add(tablespaceNameKey);

                    final DatabasePdbTablespace existing = existingTablespaceMap.get(tablespaceNameKey);

                    if (existing == null) {
                        final DatabasePdbTablespace newTablespace = new DatabasePdbTablespace();
                        newTablespace.setDatabasePdbInstance(pdbInstance);
                        newTablespace.setTablespaceName(row.tablespaceName().trim());
                        newTablespace.setTablespaceType(row.tablespaceType());
                        newTablespace.setDataMaxInB(row.dataMaxInB());
                        newTablespace.setDataUsedInB(row.dataUsedInB());
                        tablespacesToSave.add(newTablespace);
                    } else {
                        boolean changed = !Objects.equals(existing.getTablespaceType(), row.tablespaceType()) ||
                                !Objects.equals(existing.getDataMaxInB(), row.dataMaxInB()) ||
                                !Objects.equals(existing.getDataUsedInB(), row.dataUsedInB());

                        if (changed) {
                            existing.setTablespaceType(row.tablespaceType());
                            existing.setDataMaxInB(row.dataMaxInB());
                            existing.setDataUsedInB(row.dataUsedInB());
                            tablespacesToSave.add(existing);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing tablespace_info row for PDB id={}", pdbInstance.getId(), e);
                }
            }
        }

        for (final DatabasePdbTablespace existing : existingTablespaces) {
            if (!importedTablespaceNames.contains(existing.getTablespaceName().trim().toUpperCase())) {
                tablespacesToDelete.add(existing);
            }
        }
    }

    private OffsetDateTime parseDateTime(final String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }

        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString.trim(), DATE_TIME_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(dateTimeString.trim());
            } catch (DateTimeParseException ex) {
                log.warn("Error parsing date/time string: '{}'", dateTimeString);
                return null;
            }
        }
    }
}