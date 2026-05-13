package de.muenchen.mcmp.clients.netapp.storagegrid;

import de.muenchen.mcmp.storagegrid.ConfigStorageGrid;
import de.muenchen.mcmp.storagegrid.ConfigStorageGridRepository;
import de.muenchen.mcmp.storagegrid.StorageGridAccount;
import de.muenchen.mcmp.storagegrid.StorageGridAccountRepository;
import de.muenchen.mcmp.storagegrid.StorageGridBucket;
import de.muenchen.mcmp.storagegrid.StorageGridBucketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageGridService {

    /** Daten von Clustern, die länger als diese Anzahl Tage nicht aktualisiert wurden, werden gelöscht */
    private static final int STALE_DATA_RETENTION_DAYS = 3;

    private final ConfigStorageGridRepository configStorageGridRepository;
    private final StorageGridAccountRepository storageGridAccountRepository;
    private final StorageGridBucketRepository storageGridBucketRepository;

    @Autowired
    @Lazy
    private StorageGridService self;

    /**
     * Asynchroner Import der StorageGrid-Daten für einen Cluster.
     * Wird im Hintergrund ausgeführt, damit der HTTP-Request sofort zurückkehrt.
     */
    @Async
    public void importAsync(final StorageGridDTO storageGridDTO) {
        try {
            self.importClusterData(storageGridDTO);
        } catch (Exception e) {
            log.error("Fehler beim Import der StorageGrid-Daten für Cluster {}: {}", storageGridDTO.hostname(), e.getMessage(), e);
        }
    }

    /**
     * Hauptmethode für den Import der Cluster-Daten.
     * Führt den kompletten Import in einer Transaktion durch.
     */
    @Transactional
    public void importClusterData(final StorageGridDTO storageGridDTO) {
        final String hostname = storageGridDTO.hostname();
        log.info("Starte Import für StorageGrid Cluster: {}", hostname);

        final ConfigStorageGrid configStorageGrid = findOrCreateCluster(hostname);
        if (configStorageGrid == null) {
            return;
        }

        // Lade alle existierenden Daten für diesen Cluster
        final StorageGridClusterDataContext context = loadExistingData(configStorageGrid);

        // Verarbeite und speichere die neuen Daten
        processImportData(storageGridDTO, configStorageGrid, context);

        // Lösche nicht mehr vorhandene Daten
        deleteObsoleteData(context);

        log.info("Import für StorageGrid Cluster {} abgeschlossen", hostname);
    }

    private ConfigStorageGrid findOrCreateCluster(final String hostname) {
        final Optional<ConfigStorageGrid> clusterOpt = configStorageGridRepository.findByApiEndpoint(hostname);

        if (clusterOpt.isPresent()) {
            log.debug("StorageGrid Cluster mit Endpoint {} gefunden, starte Update", hostname);
            return clusterOpt.get();
        }

        // Cluster existiert nicht - erstelle neuen Eintrag
        log.info("StorageGrid Cluster mit Endpoint {} nicht konfiguriert. Erstelle neuen Eintrag automatisch.", hostname);

        try {
            final ConfigStorageGrid newCluster = new ConfigStorageGrid();
            newCluster.setApiEndpoint(hostname);
            newCluster.setUpdatedAt(new Date());

            final ConfigStorageGrid savedCluster = configStorageGridRepository.save(newCluster);
            log.info("Neuer StorageGrid Cluster-Eintrag erfolgreich erstellt: endpoint='{}', id={}",
                    hostname, savedCluster.getId());

            return savedCluster;
        } catch (Exception e) {
            log.error("Fehler beim Erstellen eines neuen StorageGrid Cluster-Eintrags für Endpoint {}: {}",
                    hostname, e.getMessage(), e);
            return null;
        }
    }

    private StorageGridClusterDataContext loadExistingData(final ConfigStorageGrid configStorageGrid) {
        final StorageGridClusterDataContext context = new StorageGridClusterDataContext();
        context.cluster = configStorageGrid;

        // Accounts
        context.existingAccounts = storageGridAccountRepository.findAllByConfigStorageGridId(configStorageGrid.getId())
                .stream()
                .collect(Collectors.toMap(StorageGridAccount::getAccountId, Function.identity()));

        // Buckets (über Accounts)
        final List<Long> accountIds = context.existingAccounts.values().stream()
                .map(StorageGridAccount::getId).toList();

        if (!accountIds.isEmpty()) {
            context.existingBuckets = storageGridBucketRepository.findAllByStorageGridAccountIdIn(accountIds)
                    .stream()
                    .collect(Collectors.toMap(
                            bucket -> bucket.getStorageGridAccount().getId() + ":" + bucket.getName(),
                            Function.identity()));
        }

        return context;
    }

    private void processImportData(final StorageGridDTO storageGridDTO,
                                   final ConfigStorageGrid cluster,
                                   final StorageGridClusterDataContext context) {

        if (storageGridDTO.accounts() == null) {
            return;
        }

        final List<StorageGridAccount> accountsToSave = new ArrayList<>();
        final List<StorageGridBucket> bucketsToSave = new ArrayList<>();

        // First, process all accounts without buckets to collect them for saving
        for (final StorageGridDTO.AccountWithUsage accountData : storageGridDTO.accounts()) {
            processOrCreateAccount(accountData, cluster, context, accountsToSave);
        }

        if (!accountsToSave.isEmpty()) {
            storageGridAccountRepository.saveAll(accountsToSave);
            // Update context with newly saved accounts so they can be found when processing buckets
            for (StorageGridAccount savedAccount : accountsToSave) {
                context.existingAccounts.put(savedAccount.getAccountId(), savedAccount);
            }
        }

        // Now process buckets, accounts now have IDs
        for (final StorageGridDTO.AccountWithUsage accountData : storageGridDTO.accounts()) {
            final String accountId = accountData.id();
            final StorageGridAccount account = context.existingAccounts.get(accountId);
            if (account != null && accountData.buckets() != null) {
                for (final StorageGridDTO.AccountWithUsage.BucketUsage bucketData : accountData.buckets()) {
                    processOrCreateBucket(bucketData, account, context, bucketsToSave);
                }
            }
        }

        // Save buckets
        if (!bucketsToSave.isEmpty()) {
            storageGridBucketRepository.saveAll(bucketsToSave);
        }

        // Update Cluster-Timestamp
        cluster.setUpdatedAt(new Date());
        configStorageGridRepository.save(cluster);
    }

    private StorageGridAccount processOrCreateAccount(
            final StorageGridDTO.AccountWithUsage accountData,
            final ConfigStorageGrid cluster,
            final StorageGridClusterDataContext context,
            final List<StorageGridAccount> accountsToSave) {

        final String accountId = accountData.id();
        StorageGridAccount account = context.existingAccounts.get(accountId);
        boolean isNew = false;

        if (account == null) {
            account = new StorageGridAccount();
            account.setConfigStoragegrid(cluster);
            account.setAccountId(accountId);
            isNew = true;
        }

        if (isNew || hasAccountChanges(account, accountData)) {
            updateAccountFromData(account, accountData);
            accountsToSave.add(account);
        }

        context.importedAccountIds.add(accountId);
        return account;
    }

    private void processOrCreateBucket(
            final StorageGridDTO.AccountWithUsage.BucketUsage bucketData,
            final StorageGridAccount account,
            final StorageGridClusterDataContext context,
            final List<StorageGridBucket> bucketsToSave) {

        final String bucketKey = account.getId() + ":" + bucketData.name();
        StorageGridBucket bucket = context.existingBuckets.get(bucketKey);
        boolean isNew = false;

        if (bucket == null) {
            bucket = new StorageGridBucket();
            bucket.setStorageGridAccount(account);
            bucket.setName(bucketData.name());
            isNew = true;
        }

        if (isNew || hasBucketChanges(bucket, bucketData)) {
            bucket.setObjectCount(bucketData.objectCount());
            bucket.setDataBytes(bucketData.dataBytes());
            bucket.setQuotaObjectBytes(bucketData.quotaObjectBytes());
            bucket.setRegion(bucketData.region());
            bucketsToSave.add(bucket);
        }

        context.importedBucketKeys.add(bucketKey);
    }

    private boolean hasAccountChanges(final StorageGridAccount account, final StorageGridDTO.AccountWithUsage data) {
        return !Objects.equals(account.getName(), data.name()) ||
                !Objects.equals(account.getUseAccountIdentitySource(), data.useAccountIdentitySource()) ||
                !Objects.equals(account.getAllowPlatformServices(), data.allowPlatformServices()) ||
                !Objects.equals(account.getAllowSelectObjectContent(), data.allowSelectObjectContent()) ||
                !Objects.equals(account.getAllowComplianceMode(), data.allowComplianceMode()) ||
                !Objects.equals(account.getMaxRetentionDays(), data.maxRetentionDays()) ||
                !Objects.equals(account.getMaxRetentionYears(), data.maxRetentionYears()) ||
                !Objects.equals(account.getQuotaObjectBytes(), data.quotaObjectBytes()) ||
                !Objects.equals(account.getDataBytes(), data.dataBytes()) ||
                !Objects.equals(account.getObjectCount(), data.objectCount()) ||
                !Objects.equals(account.getCalculationTime(), data.calculationTime());
    }

    private boolean hasBucketChanges(final StorageGridBucket bucket, final StorageGridDTO.AccountWithUsage.BucketUsage data) {
        return !Objects.equals(bucket.getObjectCount(), data.objectCount()) ||
                !Objects.equals(bucket.getDataBytes(), data.dataBytes()) ||
                !Objects.equals(bucket.getQuotaObjectBytes(), data.quotaObjectBytes()) ||
                !Objects.equals(bucket.getRegion(), data.region());
    }

    private void updateAccountFromData(final StorageGridAccount account, final StorageGridDTO.AccountWithUsage data) {
        account.setName(data.name());
        account.setUseAccountIdentitySource(data.useAccountIdentitySource());
        account.setAllowPlatformServices(data.allowPlatformServices());
        account.setAllowSelectObjectContent(data.allowSelectObjectContent());
        account.setAllowComplianceMode(data.allowComplianceMode());
        account.setMaxRetentionDays(data.maxRetentionDays());
        account.setMaxRetentionYears(data.maxRetentionYears());
        account.setQuotaObjectBytes(data.quotaObjectBytes());
        account.setDataBytes(data.dataBytes());
        account.setObjectCount(data.objectCount());
        account.setCalculationTime(data.calculationTime());
    }

    private void deleteObsoleteData(final StorageGridClusterDataContext context) {
        deleteObsoleteBuckets(context);
        deleteObsoleteAccounts(context);
    }

    private void deleteObsoleteBuckets(final StorageGridClusterDataContext context) {
        final List<StorageGridBucket> toDelete = context.existingBuckets.entrySet().stream()
                .filter(e -> !context.importedBucketKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            storageGridBucketRepository.deleteAll(toDelete);
            log.info("Gelöscht: {} StorageGrid Buckets", toDelete.size());
        }
    }

    private void deleteObsoleteAccounts(final StorageGridClusterDataContext context) {
        final List<StorageGridAccount> toDelete = context.existingAccounts.entrySet().stream()
                .filter(e -> !context.importedAccountIds.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            storageGridAccountRepository.deleteAll(toDelete);
            log.info("Gelöscht: {} StorageGrid Accounts", toDelete.size());
        }
    }

    /**
     * Kontext-Klasse zum Halten aller existierenden und importierten Daten eines StorageGrid Clusters.
     */
    private static class StorageGridClusterDataContext {
        ConfigStorageGrid cluster;

        // Existing data maps
        Map<String, StorageGridAccount> existingAccounts = new HashMap<>();
        Map<String, StorageGridBucket> existingBuckets = new HashMap<>();

        // Imported keys for deletion detection
        final Set<String> importedAccountIds = new HashSet<>();
        final Set<String> importedBucketKeys = new HashSet<>();
    }
}
