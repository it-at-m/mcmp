
package de.muenchen.mcmp.clients.netapp.ontap;

import de.muenchen.mcmp.ontap.*;
import de.muenchen.mcmp.storage.StorageCategory;
import de.muenchen.mcmp.storage.StorageCategoryClassifier;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OntapImportService {

    /** Data from clusters that have not been updated for more than this number of days will be deleted. */
    private static final int STALE_DATA_RETENTION_DAYS = 3;

    private final ConfigOntapClusterRepository clusterRepository;
    private final OntapSvmRepository svmRepository;
    private final OntapVolumeRepository volumeRepository;
    private final OntapExportPolicyRepository exportPolicyRepository;
    private final OntapExportPolicyRuleRepository exportPolicyRuleRepository;
    private final OntapCifsShareRepository cifsShareRepository;
    private final OntapCifsShareAclRepository cifsShareAclRepository;
    private final OntapQtreeRepository qtreeRepository;
    private final OntapSnapshotRepository snapshotRepository;
    private final OntapAggregateRepository ontapAggregateRepository;
    private final EntityManager entityManager;

    @Autowired
    @Lazy
    private OntapImportService self;

    /**
     * Asynchronously imports Ontap data for a cluster.
     * Executed in the background so that the HTTP request returns immediately.
     *
     * @param ontapDTO the OntapDTO containing the cluster data to import
     */
    @Async
    public void importAsync(final OntapDTO ontapDTO) {
        try {
            self.importClusterData(ontapDTO);
        } catch (Exception e) {
            log.error("Error importing Ontap data for cluster {}: {}", ontapDTO.hostname(), e.getMessage(), e);
        }
    }

    /**
     * Main method for importing cluster data.
     * Performs the complete import within a transaction.
     *
     * @param ontapDTO the OntapDTO containing the cluster data to import
     */
    @Transactional
    public void importClusterData(final OntapDTO ontapDTO) {
        final String hostname = ontapDTO.hostname();
        log.info("Starting import for cluster: {}", hostname);

        final ConfigOntapCluster cluster = findOrCreateCluster(hostname);
        if (cluster == null) {
            return;
        }

        // Load all existing data for this cluster
        final ClusterDataContext context = loadExistingData(cluster);

        // Process and save the new data
        processImportData(ontapDTO, cluster, context);

        // Delete data that no longer exists
        deleteObsoleteData(context);

        log.info("Import for cluster {} completed", hostname);
    }

    /**
     * Deletes data from clusters that have not been updated for more than the configured number of days.
     */
    @Transactional
    public void deleteStaleClusterData() {
        final OffsetDateTime threshold = OffsetDateTime.now().minusDays(STALE_DATA_RETENTION_DAYS);
        log.info("Checking for stale cluster data (older than {} days)", STALE_DATA_RETENTION_DAYS);

        final List<ConfigOntapCluster> allClusters = clusterRepository.findAll();

        for (final ConfigOntapCluster cluster : allClusters) {
            if (cluster.getUpdatedAt() != null &&
                    cluster.getUpdatedAt().toInstant().isBefore(threshold.toInstant())) {
                log.info("Deleting stale data for cluster: {} (last update: {})", cluster.getApiEndpoint(), cluster.getUpdatedAt());
                deleteAllClusterData(cluster.getId());
            }
        }
    }

    private ConfigOntapCluster findOrCreateCluster(final String hostname) {
        final Optional<ConfigOntapCluster> clusterOpt = clusterRepository.findByApiEndpoint(hostname);

        if (clusterOpt.isPresent()) {
            log.debug("Cluster with endpoint {} found, starting update", hostname);
            return clusterOpt.get();
        }

        // Cluster does not exist - create new entry automatically
        log.info("Cluster with endpoint {} not configured. Creating new entry automatically.", hostname);

        try {
            final ConfigOntapCluster newCluster = new ConfigOntapCluster();
            newCluster.setApiEndpoint(hostname);
            newCluster.setUpdatedAt(new Date());

            final ConfigOntapCluster savedCluster = clusterRepository.save(newCluster);
            log.info("New cluster entry successfully created: endpoint='{}', id={}", hostname, savedCluster.getId());

            return savedCluster;
        } catch (Exception e) {
            log.error("Error creating a new cluster entry for endpoint {}: {}", hostname, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Loads all existing data for the given cluster into a context object for processing.
     *
     * @param cluster the cluster for which to load existing data
     * @return a ClusterDataContext containing all existing entities mapped by their keys
     */
    private ClusterDataContext loadExistingData(final ConfigOntapCluster cluster) {
        final ClusterDataContext context = new ClusterDataContext();
        context.cluster = cluster;

        // SVMs
        context.existingSvms = svmRepository.findAllByClusterId(cluster.getId())
                .stream()
                .collect(Collectors.toMap(svm -> svm.getSwmUuid().toString(), Function.identity()));

        // Aggregates
        context.existingAggregates = ontapAggregateRepository.findAllByOntapClusterId(cluster.getId())
                .stream()
                .collect(Collectors.toMap(agg -> agg.getAggregateUuid().toString(), Function.identity()));

        // Volumes
        context.existingVolumes = volumeRepository.findAllByClusterId(cluster.getId())
                .stream()
                .collect(Collectors.toMap(vol -> vol.getVolumeUuid().toString(), Function.identity()));

        // Export Policies
        context.existingExportPolicies = exportPolicyRepository.findAllByClusterId(cluster.getId())
                .stream()
                .collect(Collectors.toMap(
                        policy -> policy.getExportPolicyId().toString(),
                        Function.identity()));

        // Volume-dependent data
        final List<Long> volumeIds = context.existingVolumes.values().stream()
                .map(OntapVolume::getId).toList();

        if (!volumeIds.isEmpty()) {
            context.existingCifsShares = cifsShareRepository.findAllByVolumeIdIn(volumeIds)
                    .stream()
                    .collect(Collectors.toMap(
                            share -> share.getVolume().getId() + ":" + share.getName(),
                            Function.identity()));

            context.existingQtrees = qtreeRepository.findAllByVolumeIdIn(volumeIds)
                    .stream()
                    .collect(Collectors.toMap(
                            qtree -> qtree.getVolume().getId() + ":" + qtree.getQtreeId(),
                            Function.identity()));

            // Load CIFS Shares from Qtrees
            final List<Long> qtreeIds = context.existingQtrees.values().stream().map(OntapQtree::getId).toList();
            if (!qtreeIds.isEmpty()) {
                context.existingCifsShares.putAll(cifsShareRepository.findAllByQtreeIdIn(qtreeIds)
                        .stream()
                        .collect(Collectors.toMap(
                                share -> share.getQtree().getId() + ":" + share.getName(),
                                Function.identity())));
            }

            context.existingSnapshots = snapshotRepository.findAllByOntapClusterId(cluster.getId())
                    .stream()
                    .collect(Collectors.toMap(
                            snap -> snap.getSnapshotUuid().toString(),
                            Function.identity()));

            // CIFS Share ACLs
            final List<Long> shareIds = context.existingCifsShares.values().stream()
                    .map(OntapCifsShare::getId).toList();
            if (!shareIds.isEmpty()) {
                context.existingAcls = cifsShareAclRepository.findAllByShareIdIn(shareIds)
                        .stream()
                        .collect(Collectors.toMap(
                                acl -> acl.getShare().getId() + ":" + acl.getUserOrGroup() + ":" + acl.getPermission(),
                                Function.identity()));
            }
        }

        // Export Policy Rules (Clients are now stored as an array on the Rule)
        final List<Long> policyIds = context.existingExportPolicies.values().stream()
                .map(OntapExportPolicy::getId).toList();
        if (!policyIds.isEmpty()) {
            context.existingRules = exportPolicyRuleRepository.findAllByPolicyIdIn(policyIds)
                    .stream()
                    .collect(Collectors.toMap(
                            this::createRuleKey,
                            Function.identity(),
                            (existing, duplicate) -> {
                                log.warn("Duplicate rule key found: {}. Keeping existing entry with id={}, discarding duplicate with id={}",
                                        createRuleKey(existing),
                                        existing.getId(),
                                        duplicate.getId());
                                return existing;
                            }));
        }

        return context;
    }

    /**
     * Processes the import data by collecting and saving entities in dependency order.
     *
     * @param ontapDTO the OntapDTO containing the data to import
     * @param cluster the cluster entity
     * @param context the context containing existing data
     */
    private void processImportData(final OntapDTO ontapDTO,
                                   final ConfigOntapCluster cluster,
                                   final ClusterDataContext context) {

        if (ontapDTO.svms() == null) {
            return;
        }

        final List<OntapAggregate> aggregatesToSave = new ArrayList<>();
        final List<OntapExportPolicy> policiesToSave = new ArrayList<>();
        final List<OntapExportPolicyRule> rulesToSave = new ArrayList<>();
        final List<OntapSvm> svmsToSave = new ArrayList<>();
        final List<OntapSnapshot> snapshotsToSave = new ArrayList<>();
        final List<OntapVolume> volumesToSave = new ArrayList<>();
        final List<OntapCifsShare> sharesToSave = new ArrayList<>();
        final List<OntapCifsShareAcl> aclsToSave = new ArrayList<>();
        final List<OntapQtree> qtreesToSave = new ArrayList<>();

        // Phase 1: Collect and process independent data (Aggregates, Export Policies)
        final Map<String, OntapAggregate> aggregateMap = collectAndProcessAggregates(ontapDTO, cluster, context, aggregatesToSave);
        final Map<String, OntapExportPolicy> policyMap = collectAndProcessExportPolicies(ontapDTO, cluster, context, policiesToSave, rulesToSave);

        // Phase 2: Collect dependent data (SVMs, Volumes, Snapshots, CIFS, Qtrees)
        final List<SvmVolumePair> volumePairs = collectSvmsAndVolumes(ontapDTO, cluster, context, svmsToSave);
        collectSnapshots(volumePairs, context, snapshotsToSave);

        // Phase 3: Process Volumes in topological order along with their dependent data
        processVolumesInDependencyOrder(volumePairs, cluster, policyMap, aggregateMap, context, volumesToSave, sharesToSave, aclsToSave, qtreesToSave, aggregatesToSave);

        // Phase 4: Save everything in dependency order
        saveEntitiesInDependencyOrder(aggregatesToSave, policiesToSave, rulesToSave, svmsToSave, snapshotsToSave, volumesToSave, sharesToSave, aclsToSave, qtreesToSave, context);

        // Phase 5: Set Snapshot-Volume relationships AFTER saving both sides
        setSnapshotVolumeRelationships(volumePairs, context);

        // Update Cluster-Timestamp
        cluster.setUpdatedAt(new Date());
        clusterRepository.save(cluster);
    }

    /**
     * Sets the snapshot-volume relationships after both snapshots and volumes have been saved.
     *
     * @param volumePairs the list of SVM-Volume pairs
     * @param context the context containing existing data
     */
    private void setSnapshotVolumeRelationships(final List<SvmVolumePair> volumePairs,
                                                final ClusterDataContext context) {
        final List<OntapVolume> volumesWithUpdatedRelationships = new ArrayList<>();

        for (final SvmVolumePair pair : volumePairs) {
            if (pair.volumeData().snapshots() == null) continue;

            final OntapVolume volume = context.existingVolumes.get(pair.volumeData().uuid());
            if (volume == null) continue;

            boolean relationshipChanged = false;
            for (final OntapDTO.SnapshotData snapshotData : pair.volumeData().snapshots()) {
                if (snapshotData.uuid() == null) continue;

                final OntapSnapshot snapshot = context.existingSnapshots.get(snapshotData.uuid());
                if (snapshot == null) continue;

                final UUID snapshotUuid = snapshot.getSnapshotUuid();
                if (volume.getOntapSnapshots().stream()
                        .noneMatch(s -> s.getSnapshotUuid().equals(snapshotUuid))) {
                    volume.getOntapSnapshots().add(snapshot);
                    relationshipChanged = true;
                }
            }

            if (relationshipChanged) {
                volumesWithUpdatedRelationships.add(volume);
            }
        }

        if (!volumesWithUpdatedRelationships.isEmpty()) {
            volumeRepository.saveAll(volumesWithUpdatedRelationships);
        }
    }

    /**
     * Processes an aggregate from the OntapDTO data.
     *
     * @param aggData the aggregate data from DTO
     * @param cluster the cluster entity
     * @param context the context containing existing data
     * @param result the map to store processed aggregates
     */
    private void processAggregate(
            final OntapDTO.Aggregates aggData,
            final ConfigOntapCluster cluster,
            final ClusterDataContext context,
            final Map<String, OntapAggregate> result) {

        final String uuid = aggData.uuid();
        OntapAggregate aggregate = context.existingAggregates.get(uuid);
        final boolean isNew = aggregate == null;

        if (isNew) {
            aggregate = new OntapAggregate();
            aggregate.setOntapCluster(cluster);
            aggregate.setAggregateUuid(UUID.fromString(uuid));
            aggregate.setOntapVolumes(new LinkedHashSet<>());
        }

        if (isNew || hasAggregateChanges(aggregate, aggData)) {
            aggregate.setName(aggData.name());
            aggregate.setDiskClass(aggData.diskClass());
            aggregate.setMirrorEnabled(aggData.mirrorEnabled() != null && aggData.mirrorEnabled());
        }

        context.importedAggregateUuids.add(uuid);
        result.put(uuid, aggregate);
    }

    /**
     * Processes CIFS shares for a qtree.
     *
     * @param qtreeData the qtree data
     * @param qtree the qtree entity
     * @param context the context containing existing data
     * @param sharesToSave list to add shares to save
     * @param aclsToSave list to add ACLs to save
     */
    private void processCifsSharesForQtree(
            final OntapDTO.QTreeData qtreeData,
            final OntapQtree qtree,
            final ClusterDataContext context,
            final List<OntapCifsShare> sharesToSave,
            final List<OntapCifsShareAcl> aclsToSave) {

        if (qtreeData.cifsShares() == null) return;

        for (final OntapDTO.ShareData shareData : qtreeData.cifsShares()) {
            final String shareKey = qtree.getId() + ":" + shareData.name();
            OntapCifsShare share = context.existingCifsShares.get(shareKey);
            final boolean isNew = share == null;

            if (isNew) {
                share = new OntapCifsShare();
                share.setQtree(qtree);  // Setze Qtree statt Volume
                share.setName(shareData.name());
            }

            if (isNew || hasCifsShareChanges(share, shareData)) {
                share.setPath(shareData.path());
                share.setMountPathCifs(shareData.mountPathCifs());
                sharesToSave.add(share);
            }

            context.importedCifsShareKeys.add(shareKey);

            // ACLs
            if (shareData.acls() != null) {
                for (final OntapDTO.ACLData aclData : shareData.acls()) {
                    final String aclKey = share.getId() + ":" + aclData.userOrGroup() + ":" + aclData.permission();
                    OntapCifsShareAcl acl = context.existingAcls.get(aclKey);

                    if (acl == null) {
                        acl = new OntapCifsShareAcl();
                        acl.setShare(share);
                        acl.setUserOrGroup(aclData.userOrGroup());
                        acl.setPermission(aclData.permission());
                        aclsToSave.add(acl);
                    }
                    context.importedAclKeys.add(aclKey);
                }
            }
        }
    }

    /*    /**
     * Processes a single export policy.
     *
     * @param policyData the policy data from DTO
     * @param cluster the cluster entity
     * @param context the context containing existing data
     * @param result the map to store processed policies
     * @param policiesToSave list to add policies to save
     * @param rulesToSave list to add rules to save
     */
    private void processExportPolicy(
            final OntapDTO.ExportPolicyData policyData,
            final ConfigOntapCluster cluster,
            final ClusterDataContext context,
            final Map<String, OntapExportPolicy> result,
            final List<OntapExportPolicy> policiesToSave,
            final List<OntapExportPolicyRule> rulesToSave) {

        if (policyData.id() == null) return;

        final String policyKey = createExportPolicyKey(policyData.id());

        // Already processed?
        if (result.containsKey(policyKey)) return;

        OntapExportPolicy policy = context.existingExportPolicies.get(policyKey);
        final boolean isNew = policy == null;

        if (isNew) {
            policy = new OntapExportPolicy();
            policy.setCluster(cluster);
            policy.setExportPolicyId(policyData.id());
        }

        if (isNew || hasExportPolicyChanges(policy, policyData)) {
            policy.setName(policyData.name());
            policiesToSave.add(policy);
        }

        context.importedExportPolicyKeys.add(policyKey);
        result.put(policyKey, policy);

        // Process Rules
        if (policyData.rules() != null) {
            for (final OntapDTO.ExportRuleData ruleData : policyData.rules()) {
                processExportPolicyRule(ruleData, policy, context, rulesToSave);
            }
        }
    }

    /**
     * Processes a single export policy rule.
     *
     * @param ruleData the rule data from DTO
     * @param policy the policy entity
     * @param context the context containing existing data
     * @param rulesToSave list to add rules to save
     */
    private void processExportPolicyRule(
            final OntapDTO.ExportRuleData ruleData,
            final OntapExportPolicy policy,
            final ClusterDataContext context,
            final List<OntapExportPolicyRule> rulesToSave) {

        final String ruleKey = createRuleKeyFromData(policy.getId(), ruleData);

        OntapExportPolicyRule rule = context.existingRules.get(ruleKey);
        final boolean isNew = rule == null;

        if (isNew) {
            rule = new OntapExportPolicyRule();
            rule.setPolicy(policy);
        }

        // Extract clients from the DTO
        final List<String> clients = ruleData.clients() != null
                ? ruleData.clients().stream()
                .map(OntapDTO.ClientMatch::match)
                .sorted()
                .toList()
                : List.of();

        if (isNew || hasExportRuleChanges(rule, ruleData, clients)) {
            rule.setClients(clients);
            rule.setIndex(ruleData.index());
            rule.setProtocols(ruleData.protocols());
            rule.setRwRules(ruleData.rwRule());
            rule.setRoRules(ruleData.roRule());
            rulesToSave.add(rule);
        }

        context.importedRuleKeys.add(ruleKey);
    }

    /**
     * Processes or creates an SVM from the given data.
     *
     * @param svmData the SVM data from DTO
     * @param cluster the cluster entity
     * @param context the context containing existing data
     * @param svmsToSave list to add SVMs to save
     * @return the processed SVM entity
     */
    private OntapSvm processOrCreateSvm(
            final OntapDTO.SVMData svmData,
            final ConfigOntapCluster cluster,
            final ClusterDataContext context,
            final List<OntapSvm> svmsToSave) {

        final String svmUuid = svmData.uuid();
        OntapSvm svm = context.existingSvms.get(svmUuid);
        final boolean isNew = svm == null;

        if (isNew) {
            svm = new OntapSvm();
            svm.setCluster(cluster);
            svm.setSwmUuid(UUID.fromString(svmUuid));
        }

        if (isNew || hasSvmChanges(svm, svmData)) {
            svm.setName(svmData.name());
            svmsToSave.add(svm);
        }

        context.importedSvmUuids.add(svmUuid);
        if (isNew) {
            context.existingSvms.put(svmUuid, svm);
        }
        return svm;
    }

    /**
     * Processes or creates a volume from the given data.
     *
     * @param volumeData the volume data from DTO
     * @param svm the SVM entity
     * @param cluster the cluster entity
     * @param policyMap map of export policies
     * @param aggregateMap map of aggregates
     * @param context the context containing existing data
     * @param volumesToSave list to add volumes to save
     * @return the processed volume entity
     */
    private OntapVolume processOrCreateVolume(
            final OntapDTO.VolumeData volumeData,
            final OntapSvm svm,
            final ConfigOntapCluster cluster,
            final Map<String, OntapExportPolicy> policyMap,
            final Map<String, OntapAggregate> aggregateMap,
            final ClusterDataContext context,
            final List<OntapVolume> volumesToSave) {

        final String volumeUuid = volumeData.uuid();
        OntapVolume volume = context.existingVolumes.get(volumeUuid);
        final boolean isNew = volume == null;

        if (isNew) {
            volume = new OntapVolume();
            volume.setCluster(cluster);
            volume.setSvm(svm);
            volume.setVolumeUuid(UUID.fromString(volumeUuid));
            volume.setOntapAggregates(new LinkedHashSet<>());
        }

        // Assign Export Policy
        OntapExportPolicy exportPolicy = null;
        if (volumeData.exportPolicy() != null && volumeData.exportPolicy().id() != null) {
            exportPolicy = policyMap.get(createExportPolicyKey(volumeData.exportPolicy().id()));
        }

        // Aggregate assignment ONLY on the Volume side (inverse side, not owner)
        // The owner side (OntapAggregate.ontapVolumes) is filled ONLY AFTER Volume save
        if (volumeData.aggregateUUIDs() != null) {
            final Set<String> existingAggUuids = volume.getOntapAggregates().stream()
                    .map(agg -> agg.getAggregateUuid().toString())
                    .collect(Collectors.toSet());

            final Set<String> newAggUuids = new HashSet<>(volumeData.aggregateUUIDs());

            if (!existingAggUuids.equals(newAggUuids)) {
                volume.getOntapAggregates().clear();

                for (final String aggUuid : volumeData.aggregateUUIDs()) {
                    final OntapAggregate aggregate = aggregateMap.get(aggUuid);
                    if (aggregate != null) {
                        volume.getOntapAggregates().add(aggregate);
                    }
                }
            }
        }

        if (isNew || hasVolumeChanges(volume, volumeData, exportPolicy, context)) {
            updateVolumeFromData(volume, volumeData, exportPolicy, context);
            volumesToSave.add(volume);
        }

        context.importedVolumeUuids.add(volumeUuid);
        if (isNew) {
            context.existingVolumes.put(volumeUuid, volume);
        }
        return volume;
    }

    /**
     * Processes CIFS shares for a volume.
     *
     * @param volumeData the volume data
     * @param volume the volume entity
     * @param context the context containing existing data
     * @param sharesToSave list to add shares to save
     * @param aclsToSave list to add ACLs to save
     */
    private void processCifsShares(
            final OntapDTO.VolumeData volumeData,
            final OntapVolume volume,
            final ClusterDataContext context,
            final List<OntapCifsShare> sharesToSave,
            final List<OntapCifsShareAcl> aclsToSave) {

        if (volumeData.cifsShares() == null) return;

        for (final OntapDTO.ShareData shareData : volumeData.cifsShares()) {
            final String shareKey = volume.getId() + ":" + shareData.name();
            OntapCifsShare share = context.existingCifsShares.get(shareKey);
            final boolean isNew = share == null;

            if (isNew) {
                share = new OntapCifsShare();
                share.setVolume(volume);
                share.setName(shareData.name());
            }

            if (isNew || hasCifsShareChanges(share, shareData)) {
                share.setPath(shareData.path());
                share.setMountPathCifs(shareData.mountPathCifs());
                sharesToSave.add(share);
            }

            context.importedCifsShareKeys.add(shareKey);

            // ACLs
            if (shareData.acls() != null) {
                for (final OntapDTO.ACLData aclData : shareData.acls()) {
                    final String aclKey = share.getId() + ":" + aclData.userOrGroup() + ":" + aclData.permission();
                    OntapCifsShareAcl acl = context.existingAcls.get(aclKey);

                    if (acl == null) {
                        acl = new OntapCifsShareAcl();
                        acl.setShare(share);
                        acl.setUserOrGroup(aclData.userOrGroup());
                        acl.setPermission(aclData.permission());
                        aclsToSave.add(acl);
                    }
                    context.importedAclKeys.add(aclKey);
                }
            }
        }
    }

    /**
     * Processes qtrees for a volume.
     *
     * @param volumeData the volume data
     * @param volume the volume entity
     * @param policyMap map of export policies
     * @param context the context containing existing data
     * @param qtreesToSave list to add qtrees to save
     * @param sharesToSave list to add shares to save
     * @param aclsToSave list to add ACLs to save
     */
    private void processQtrees(
            final OntapDTO.VolumeData volumeData,
            final OntapVolume volume,
            final Map<String, OntapExportPolicy> policyMap,
            final ClusterDataContext context,
            final List<OntapQtree> qtreesToSave,
            final List<OntapCifsShare> sharesToSave,
            final List<OntapCifsShareAcl> aclsToSave) {

        if (volumeData.qTrees() == null) return;

        for (final OntapDTO.QTreeData qtreeData : volumeData.qTrees()) {
            if (qtreeData.id() == null) continue;

            final String qtreeKey = volume.getId() + ":" + qtreeData.id();
            OntapQtree qtree = context.existingQtrees.get(qtreeKey);
            final boolean isNew = qtree == null;

            if (isNew) {
                qtree = new OntapQtree();
                qtree.setVolume(volume);
                qtree.setQtreeId(qtreeData.id());
            }

            // Export Policy zuordnen
            OntapExportPolicy exportPolicy = null;
            if (qtreeData.exportPolicy() != null && qtreeData.exportPolicy().id() != null) {
                exportPolicy = policyMap.get(createExportPolicyKey(qtreeData.exportPolicy().id()));
            }

            if (isNew || hasQtreeChanges(qtree, qtreeData, exportPolicy)) {
                updateQtreeFromData(qtree, qtreeData, exportPolicy);
                qtreesToSave.add(qtree);
            }
            processCifsSharesForQtree(qtreeData, qtree, context, sharesToSave, aclsToSave);

            final StorageCategory qtreeCategory = StorageCategoryClassifier.classifyQtree(qtreeData.mountPathNfs());
            if (!Objects.equals(qtree.getStorageCategory(), qtreeCategory)) {
                qtree.setStorageCategory(qtreeCategory);
                if (!qtreesToSave.contains(qtree)) {
                    qtreesToSave.add(qtree);
                }
            }

            context.importedQtreeKeys.add(qtreeKey);
        }
    }

    /**
     * Processes snapshots for a volume.
     *
     * @param volumeData the volume data
     * @param volume the volume entity
     * @param context the context containing existing data
     * @param snapshotsToSave list to add snapshots to save
     */
    private void processSnapshots(
            final OntapDTO.VolumeData volumeData,
            final OntapVolume volume,
            final ClusterDataContext context,
            final List<OntapSnapshot> snapshotsToSave) {

        if (volumeData.snapshots() == null) return;

        for (final OntapDTO.SnapshotData snapshotData : volumeData.snapshots()) {
            if (snapshotData.uuid() == null) continue;

            final String snapshotUuid = snapshotData.uuid();
            OntapSnapshot snapshot = context.existingSnapshots.get(snapshotUuid);
            final boolean isNew = snapshot == null;

            if (isNew) {
                snapshot = new OntapSnapshot();
                snapshot.setOntapCluster(context.cluster);
                snapshot.setSnapshotUuid(UUID.fromString(snapshotUuid));
            }

            if (isNew || hasSnapshotChanges(snapshot, snapshotData)) {
                snapshot.setName(snapshotData.name());
                snapshot.setCreateTime(snapshotData.createTime());
                snapshotsToSave.add(snapshot);
            }

            final OntapSnapshot finalSnapshot = snapshot;

            // Add relationship only if not already present (loads lazy collection if needed)
            if (volume != null && volume.getOntapSnapshots().stream()
                    .noneMatch(s -> s.getSnapshotUuid().equals(finalSnapshot.getSnapshotUuid()))) {
                volume.getOntapSnapshots().add(snapshot);
            }
            context.importedSnapshotUuids.add(snapshotUuid);
            if (isNew) {
                context.existingSnapshots.put(snapshotUuid, snapshot);
            }
        }
    }

    /**
     * Collects and processes aggregates from the OntapDTO.
     *
     * @param ontapDTO the OntapDTO containing the data
     * @param cluster the cluster entity
     * @param context the context containing existing data
     * @param aggregatesToSave list to add aggregates to save
     * @return a map of processed aggregates
     */
    private Map<String, OntapAggregate> collectAndProcessAggregates(final OntapDTO ontapDTO,
                                                                    final ConfigOntapCluster cluster,
                                                                    final ClusterDataContext context,
                                                                    final List<OntapAggregate> aggregatesToSave) {
        final Map<String, OntapAggregate> aggregateMap = new HashMap<>();
        if (ontapDTO.aggregates() != null) {
            for (final OntapDTO.Aggregates aggData : ontapDTO.aggregates()) {
                processAggregate(aggData, cluster, context, aggregateMap);
            }
            // All aggregates must be saved so that the join table
            // ontap_aggregate_has_volumes is correctly filled after volume processing
            aggregatesToSave.addAll(aggregateMap.values());
        }
        return aggregateMap;
    }

    /**
     * Collects and processes export policies from the OntapDTO.
     *
     * @param ontapDTO the OntapDTO containing the data
     * @param cluster the cluster entity
     * @param context the context containing existing data
     * @param policiesToSave list to add policies to save
     * @param rulesToSave list to add rules to save
     * @return a map of processed export policies
     */
    private Map<String, OntapExportPolicy> collectAndProcessExportPolicies(final OntapDTO ontapDTO,
                                                                           final ConfigOntapCluster cluster,
                                                                           final ClusterDataContext context,
                                                                           final List<OntapExportPolicy> policiesToSave,
                                                                           final List<OntapExportPolicyRule> rulesToSave) {
        final Map<String, OntapExportPolicy> policyMap = new HashMap<>();
        if (ontapDTO.svms() != null) {
            for (final OntapDTO.SVMData svmData : ontapDTO.svms()) {
                if (svmData.volumes() != null) {
                    for (final OntapDTO.VolumeData volumeData : svmData.volumes()) {
                        // Volume Export Policy
                        if (volumeData.exportPolicy() != null) {
                            processExportPolicy(volumeData.exportPolicy(), cluster, context, policyMap, policiesToSave, rulesToSave);
                        }
                        // Qtree Export Policies
                        if (volumeData.qTrees() != null) {
                            for (final OntapDTO.QTreeData qtreeData : volumeData.qTrees()) {
                                if (qtreeData.exportPolicy() != null) {
                                    processExportPolicy(qtreeData.exportPolicy(), cluster, context, policyMap, policiesToSave, rulesToSave);
                                }
                            }
                        }
                    }
                }
            }
        }
        return policyMap;
    }

    private String createExportPolicyKey(final Long policyId) {
        return policyId == null ? null : policyId.toString();
    }

    private StorageCategory computeVolumeStorageCategory(final OntapDTO.VolumeData volumeData) {
        StorageCategory category = StorageCategoryClassifier.classifyNfs(volumeData.mountPathNfs());
        if (category == null && volumeData.cifsShares() != null) {
            category = volumeData.cifsShares().stream()
                    .map(s -> StorageCategoryClassifier.classifyCifs(s.mountPathCifs()))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
        }
        return category;
    }

    /**
     * Collects SVMs and volumes from the OntapDTO.
     *
     * @param ontapDTO the OntapDTO containing the data
     * @param cluster the cluster entity
     * @param context the context containing existing data
     * @param svmsToSave list to add SVMs to save
     * @return a list of SVM-Volume pairs
     */
    private List<SvmVolumePair> collectSvmsAndVolumes(final OntapDTO ontapDTO,
                                                      final ConfigOntapCluster cluster,
                                                      final ClusterDataContext context,
                                                      final List<OntapSvm> svmsToSave) {
        final List<SvmVolumePair> volumePairs = new ArrayList<>();
        for (final OntapDTO.SVMData svmData : ontapDTO.svms()) {
            final OntapSvm svm = processOrCreateSvm(svmData, cluster, context, svmsToSave);
            if (svmData.volumes() != null) {
                for (final OntapDTO.VolumeData volumeData : svmData.volumes()) {
                    volumePairs.add(new SvmVolumePair(svm, volumeData));
                }
            }
        }
        return volumePairs;
    }

    /**
     * Collects snapshots from volume pairs.
     *
     * @param volumePairs the list of SVM-Volume pairs
     * @param context the context containing existing data
     * @param snapshotsToSave list to add snapshots to save
     */
    private void collectSnapshots(final List<SvmVolumePair> volumePairs,
                                  final ClusterDataContext context,
                                  final List<OntapSnapshot> snapshotsToSave) {
        for (final SvmVolumePair pair : volumePairs) {
            processSnapshots(pair.volumeData(), null, context, snapshotsToSave);
        }
    }

    /**
     * Processes volumes in dependency order, handling parent-child relationships.
     *
     * @param volumePairs the list of SVM-Volume pairs
     * @param cluster the cluster entity
     * @param policyMap map of export policies
     * @param aggregateMap map of aggregates
     * @param context the context containing existing data
     * @param volumesToSave list to add volumes to save
     * @param sharesToSave list to add shares to save
     * @param aclsToSave list to add ACLs to save
     * @param qtreesToSave list to add qtrees to save
     * @param aggregatesToSave list to add aggregates to save
     */
    private void processVolumesInDependencyOrder(final List<SvmVolumePair> volumePairs,
                                                 final ConfigOntapCluster cluster,
                                                 final Map<String, OntapExportPolicy> policyMap,
                                                 final Map<String, OntapAggregate> aggregateMap,
                                                 final ClusterDataContext context,
                                                 final List<OntapVolume> volumesToSave,
                                                 final List<OntapCifsShare> sharesToSave,
                                                 final List<OntapCifsShareAcl> aclsToSave,
                                                 final List<OntapQtree> qtreesToSave,
                                                 final List<OntapAggregate> aggregatesToSave) {
        final Set<String> processedVolumeUuids = new HashSet<>();
        final Queue<SvmVolumePair> queue = new LinkedList<>();

        // Add volumes without parent or with already existing parent to the queue
        for (final SvmVolumePair pair : volumePairs) {
            final String parentUuid = pair.volumeData().parentVolumeUuid();
            if (parentUuid == null || context.existingVolumes.containsKey(parentUuid)) {
                queue.add(pair);
            }
        }

        while (!queue.isEmpty()) {
            final SvmVolumePair pair = queue.poll();
            if (processedVolumeUuids.contains(pair.volumeData().uuid())) {
                continue;
            }

            // Process the volume
            final OntapVolume volume = processOrCreateVolume(pair.volumeData(), pair.svm(), cluster, policyMap, aggregateMap, context, volumesToSave);

            processCifsShares(pair.volumeData(), volume, context, sharesToSave, aclsToSave);
            processQtrees(pair.volumeData(), volume, policyMap, context, qtreesToSave, sharesToSave, aclsToSave);

            // Classify storage category after shares and qtrees are resolved
            final StorageCategory volumeCategory = computeVolumeStorageCategory(pair.volumeData());
            if (!Objects.equals(volume.getStorageCategory(), volumeCategory)) {
                volume.setStorageCategory(volumeCategory);
                if (!volumesToSave.contains(volume)) {
                    volumesToSave.add(volume);
                }
            }
            processedVolumeUuids.add(pair.volumeData().uuid());

            // Add children whose parent has now been processed
            for (final SvmVolumePair child : volumePairs) {
                if (!processedVolumeUuids.contains(child.volumeData().uuid())) {
                    final String childParentUuid = child.volumeData().parentVolumeUuid();
                    if (childParentUuid != null && childParentUuid.equals(pair.volumeData().uuid())) {
                        queue.add(child);
                    }
                }
            }
        }
    }

    /**
     * Saves entities in dependency order to ensure foreign key constraints are satisfied.
     *
     * @param aggregatesToSave list of aggregates to save
     * @param policiesToSave list of policies to save
     * @param rulesToSave list of rules to save
     * @param svmsToSave list of SVMs to save
     * @param snapshotsToSave list of snapshots to save
     * @param volumesToSave list of volumes to save
     * @param sharesToSave list of shares to save
     * @param aclsToSave list of ACLs to save
     * @param qtreesToSave list of qtrees to save
     * @param context the context containing existing data
     */
    private void saveEntitiesInDependencyOrder(final List<OntapAggregate> aggregatesToSave,
                                               final List<OntapExportPolicy> policiesToSave,
                                               final List<OntapExportPolicyRule> rulesToSave,
                                               final List<OntapSvm> svmsToSave,
                                               final List<OntapSnapshot> snapshotsToSave,
                                               final List<OntapVolume> volumesToSave,
                                               final List<OntapCifsShare> sharesToSave,
                                               final List<OntapCifsShareAcl> aclsToSave,
                                               final List<OntapQtree> qtreesToSave,
                                               final ClusterDataContext context) {
        // Save in dependency order
        // Snapshots must be saved first: volumes may reference them via parentSnapshot,
        // and any subsequent saveAll can trigger a Hibernate flush that would fail
        // if those snapshot references are still transient.
        if (!snapshotsToSave.isEmpty()) {
            snapshotRepository.saveAll(snapshotsToSave);
        }
        if (!policiesToSave.isEmpty()) {
            exportPolicyRepository.saveAll(policiesToSave);
        }
        if (!rulesToSave.isEmpty()) {
            exportPolicyRuleRepository.saveAll(rulesToSave);
        }
        if (!svmsToSave.isEmpty()) {
            svmRepository.saveAll(svmsToSave);
        }
        // 1. Save aggregates without volumes (ontapVolumes collection is still empty)
        if (!aggregatesToSave.isEmpty()) {
            ontapAggregateRepository.saveAll(aggregatesToSave);
        }
        // 2. Save volumes (they now have aggregate IDs via the inverse side ontapAggregates)
        if (!volumesToSave.isEmpty()) {
            volumeRepository.saveAll(volumesToSave);
        }
        // 3. Now fill the owner side: Aggregate → Volumes (both have IDs)
        if (!aggregatesToSave.isEmpty()) {
            // Consider all imported volumes (not just changed ones!)
            final Collection<OntapVolume> allImportedVolumes = context.existingVolumes.values();
            for (final OntapAggregate aggregate : aggregatesToSave) {
                final Set<OntapVolume> linkedVolumes = aggregate.getOntapVolumes();
                linkedVolumes.clear();
                for (final OntapVolume volume : allImportedVolumes) {
                    if (volume.getOntapAggregates().contains(aggregate)) {
                        linkedVolumes.add(volume);
                    }
                }
            }
            ontapAggregateRepository.saveAll(aggregatesToSave);
        }
        if (!qtreesToSave.isEmpty()) {
            qtreeRepository.saveAll(qtreesToSave);
        }
        if (!sharesToSave.isEmpty()) {
            cifsShareRepository.saveAll(sharesToSave);
        }
        if (!aclsToSave.isEmpty()) {
            cifsShareAclRepository.saveAll(aclsToSave);
        }
    }


    // --- Change Detection Methods ---


    /**
     * Checks if an aggregate has changes compared to the DTO data.
     *
     * @param aggregate the existing aggregate entity
     * @param data the aggregate data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasAggregateChanges(final OntapAggregate aggregate, final OntapDTO.Aggregates data) {
        return !Objects.equals(aggregate.getName(), data.name()) ||
                !Objects.equals(aggregate.getDiskClass(), data.diskClass()) ||
                !Objects.equals(aggregate.getMirrorEnabled(), data.mirrorEnabled());
    }

    /**
     * Checks if an export policy has changes compared to the DTO data.
     *
     * @param policy the existing policy entity
     * @param data the policy data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasExportPolicyChanges(final OntapExportPolicy policy, final OntapDTO.ExportPolicyData data) {
        return !Objects.equals(policy.getName(), data.name());
    }

    /**
     * Checks if an SVM has changes compared to the DTO data.
     *
     * @param svm the existing SVM entity
     * @param data the SVM data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasSvmChanges(final OntapSvm svm, final OntapDTO.SVMData data) {
        return !Objects.equals(svm.getName(), data.name());
    }

    /**
     * Checks if a volume's parent data has changes.
     *
     * @param volume the existing volume entity
     * @param data the volume data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasVolumeParentDataChanges(final OntapVolume volume, final OntapDTO.VolumeData data,
                                               final ClusterDataContext context) {
        final OntapVolume parentVolume = data.parentVolumeUuid() != null ? context.existingVolumes.get(data.parentVolumeUuid()) : null;
        final OntapSnapshot parentSnapshot = data.parentSnapshotUuid() != null ? context.existingSnapshots.get(data.parentSnapshotUuid()) : null;
        final OntapSvm parentSvm = data.parentSvmUuid() != null ? context.existingSvms.get(data.parentSvmUuid()) : null;
        return !Objects.equals(volume.getParentVolumeId(), parentVolume != null ? parentVolume.getId() : null) ||
                !Objects.equals(volume.getParentSnapshotId(), parentSnapshot != null ? parentSnapshot.getId() : null) ||
                !Objects.equals(volume.getParentSvmId(), parentSvm != null ? parentSvm.getId() : null);
    }

    /**
     * Checks if a volume has changes compared to the DTO data.
     *
     * @param volume the existing volume entity
     * @param data the volume data from DTO
     * @param exportPolicy the export policy to compare
     * @return true if there are changes, false otherwise
     */
    private boolean hasVolumeChanges(final OntapVolume volume, final OntapDTO.VolumeData data,
                                     final OntapExportPolicy exportPolicy, final ClusterDataContext context) {
        return !Objects.equals(volume.getName(), data.name()) ||
                !Objects.equals(volume.getSize(), data.size()) ||
                !Objects.equals(volume.getState(), data.state()) ||
                !Objects.equals(volume.getType(), data.type()) ||
                !Objects.equals(volume.getStyle(), data.style()) ||
                !Objects.equals(volume.getSnapshotPolicy(), data.snapshotPolicy()) ||
                !Objects.equals(volume.getNasPath(), data.nasPath()) ||
                !Objects.equals(volume.getMountPathNfs(), data.mountPathNfs()) ||
                !Objects.equals(volume.getIsFlexClone(), data.isFlexClone()) ||
                !Objects.equals(volume.getIsSplitInitiated(), data.isSplitInitiated()) ||
                !Objects.equals(volume.getExportPolicy(), exportPolicy) ||
                hasSpaceDataChanges(volume, data.space()) ||
                hasSnaplockChanges(volume, data.snaplock()) ||
                hasVolumeParentDataChanges(volume, data, context);
    }

    /**
     * Checks if space data has changes.
     *
     * @param volume the existing volume entity
     * @param space the space data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasSpaceDataChanges(final OntapVolume volume, final OntapDTO.SpaceData space) {
        if (space == null) {
            return volume.getSpaceAvailablePercent() != null;
        }
        return !Objects.equals(volume.getSpaceAvailablePercent(), space.availablePercent()) ||
                !Objects.equals(volume.getSpaceAfsTotal(), space.afsTotal()) ||
                hasLogicalSpaceChanges(volume, space.logicalSpace()) ||
                hasSnapshotSpaceChanges(volume, space.snapshot());
    }

    /**
     * Checks if logical space data has changes.
     *
     * @param volume the existing volume entity
     * @param logical the logical space data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasLogicalSpaceChanges(final OntapVolume volume, final OntapDTO.LogicalSpaceData logical) {
        if (logical == null) {
            return volume.getSpaceLogicalUsed() != null;
        }
        return !Objects.equals(volume.getSpaceLogicalUsed(), logical.used()) ||
                !Objects.equals(volume.getSpaceLogicalAvailable(), logical.available()) ||
                !Objects.equals(volume.getSpaceLogicalUsedPercent(), logical.usedPercent()) ||
                !Objects.equals(volume.getSpaceLogicalUsedByAfs(), logical.usedByAfs());
    }

    /**
     * Checks if snapshot space data has changes.
     *
     * @param volume the existing volume entity
     * @param snapshot the snapshot space data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasSnapshotSpaceChanges(final OntapVolume volume, final OntapDTO.SnapshotSpaceData snapshot) {
        if (snapshot == null) {
            return volume.getSpaceSnapshotUsed() != null;
        }
        return !Objects.equals(volume.getSpaceSnapshotReservePercent(), snapshot.reservePercent()) ||
                !Objects.equals(volume.getSpaceSnapshotReserveSize(), snapshot.reserveSize()) ||
                !Objects.equals(volume.getSpaceSnapshotUsed(), snapshot.used());
    }

    /**
     * Checks if snaplock data has changes.
     *
     * @param volume the existing volume entity
     * @param snaplock the snaplock data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasSnaplockChanges(final OntapVolume volume, final OntapDTO.SnaplockData snaplock) {
        if (snaplock == null) {
            return volume.getSnaplockAppendModeEnabled() != null;
        }
        return !Objects.equals(volume.getSnaplockAppendModeEnabled(), snaplock.appendModeEnabled()) ||
                !Objects.equals(volume.getSnaplockAutocommitPeriod(), snaplock.autocommitPeriod()) ||
                !Objects.equals(volume.getSnaplockType(), snaplock.type()) ||
                hasRetentionChanges(volume, snaplock.retention());
    }

    /**
     * Checks if retention data has changes.
     *
     * @param volume the existing volume entity
     * @param retention the retention data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasRetentionChanges(final OntapVolume volume, final OntapDTO.RetentionData retention) {
        if (retention == null) {
            return volume.getSnaplockRetentionDefault() != null;
        }
        return !Objects.equals(volume.getSnaplockRetentionDefault(), retention.defaultValue()) ||
                !Objects.equals(volume.getSnaplockRetentionMinimum(), retention.minimum()) ||
                !Objects.equals(volume.getSnaplockRetentionMaximum(), retention.maximum());
    }

    /**
     * Checks if a CIFS share has changes compared to the DTO data.
     *
     * @param share the existing share entity
     * @param data the share data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasCifsShareChanges(final OntapCifsShare share, final OntapDTO.ShareData data) {
        return !Objects.equals(share.getPath(), data.path()) ||
                !Objects.equals(share.getMountPathCifs(), data.mountPathCifs());
    }

    /**
     * Checks if a qtree has changes compared to the DTO data.
     *
     * @param qtree the existing qtree entity
     * @param data the qtree data from DTO
     * @param exportPolicy the export policy to compare
     * @return true if there are changes, false otherwise
     */
    private boolean hasQtreeChanges(final OntapQtree qtree, final OntapDTO.QTreeData data,
                                    final OntapExportPolicy exportPolicy) {
        return !Objects.equals(qtree.getName(), data.name()) ||
                !Objects.equals(qtree.getPath(), data.path()) ||
                !Objects.equals(qtree.getMountPathNfs(), data.mountPathNfs()) ||
                !Objects.equals(qtree.getSecurityStyle(), data.securityStyle()) ||
                !Objects.equals(qtree.getExportPolicy(), exportPolicy) ||
                hasQuotaChanges(qtree, data.quota());
    }

    /**
     * Checks if quota data has changes.
     *
     * @param qtree the existing qtree entity
     * @param quota the quota data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasQuotaChanges(final OntapQtree qtree, final OntapDTO.QuotaData quota) {
        if (quota == null) {
            return qtree.getQuotaIndex() != null;
        }
        return !Objects.equals(qtree.getQuotaIndex(), quota.index()) ||
                !Objects.equals(qtree.getQuotaType(), quota.type()) ||
                !Objects.equals(qtree.getQuotaHardLimit(), quota.hardLimit()) ||
                !Objects.equals(qtree.getQuotaUsedBytes(), quota.usedBytes()) ||
                !Objects.equals(qtree.getQuotaUsedPercent(), quota.usedPercent());
    }

    /**
     * Checks if a snapshot has changes compared to the DTO data.
     *
     * @param snapshot the existing snapshot entity
     * @param data the snapshot data from DTO
     * @return true if there are changes, false otherwise
     */
    private boolean hasSnapshotChanges(final OntapSnapshot snapshot, final OntapDTO.SnapshotData data) {
        return !Objects.equals(snapshot.getName(), data.name()) ||
                !Objects.equals(snapshot.getCreateTime(), data.createTime());
    }

    /**
     * Checks if an export policy rule has changes compared to the DTO data (including clients).
     *
     * @param rule the existing rule entity
     * @param data the rule data from DTO
     * @param clients the list of clients
     * @return true if there are changes, false otherwise
     */
    private boolean hasExportRuleChanges(final OntapExportPolicyRule rule,
                                         final OntapDTO.ExportRuleData data,
                                         final List<String> clients) {
        return !Objects.equals(rule.getClients(), clients) ||
                !Objects.equals(rule.getProtocols(), data.protocols()) ||
                !Objects.equals(rule.getRwRules(), data.rwRule()) ||
                !Objects.equals(rule.getRoRules(), data.roRule());
    }


    // --- Update Methods ---


    /**
     * Updates a volume entity from the DTO data.
     *
     * @param volume the volume entity to update
     * @param data the volume data from DTO
     * @param exportPolicy the export policy to assign
     * @param context the context containing existing data
     */
    private void updateVolumeFromData(final OntapVolume volume, final OntapDTO.VolumeData data,
                                      final OntapExportPolicy exportPolicy, final ClusterDataContext context) {
        volume.setName(data.name());
        volume.setSize(data.size());
        volume.setState(data.state());
        volume.setType(data.type());
        volume.setStyle(data.style());
        volume.setSnapshotPolicy(data.snapshotPolicy());
        volume.setNasPath(data.nasPath());
        volume.setMountPathNfs(data.mountPathNfs());
        volume.setIsFlexClone(data.isFlexClone());
        volume.setIsSplitInitiated(data.isSplitInitiated());
        volume.setExportPolicy(exportPolicy);

        // Parent data
        if (data.parentVolumeUuid() != null) {
            volume.setParentVolume(context.existingVolumes.get(data.parentVolumeUuid()));
        }
        if (data.parentSnapshotUuid() != null) {
            volume.setParentSnapshot(context.existingSnapshots.get(data.parentSnapshotUuid()));
        }
        if (data.parentSvmUuid() != null) {
            volume.setParentSvm(context.existingSvms.get(data.parentSvmUuid()));
        }


        // Space data
        if (data.space() != null) {
            volume.setSpaceAvailablePercent(data.space().availablePercent());
            volume.setSpaceAfsTotal(data.space().afsTotal());

            if (data.space().logicalSpace() != null) {
                volume.setSpaceLogicalUsed(data.space().logicalSpace().used());
                volume.setSpaceLogicalAvailable(data.space().logicalSpace().available());
                volume.setSpaceLogicalUsedPercent(data.space().logicalSpace().usedPercent());
                volume.setSpaceLogicalUsedByAfs(data.space().logicalSpace().usedByAfs());
            }

            if (data.space().snapshot() != null) {
                volume.setSpaceSnapshotReservePercent(data.space().snapshot().reservePercent());
                volume.setSpaceSnapshotReserveSize(data.space().snapshot().reserveSize());
                volume.setSpaceSnapshotUsed(data.space().snapshot().used());
            }
        }

        // Snaplock data
        if (data.snaplock() != null) {
            volume.setSnaplockAppendModeEnabled(data.snaplock().appendModeEnabled());
            volume.setSnaplockAutocommitPeriod(data.snaplock().autocommitPeriod());
            volume.setSnaplockType(data.snaplock().type());

            if (data.snaplock().retention() != null) {
                volume.setSnaplockRetentionDefault(data.snaplock().retention().defaultValue());
                volume.setSnaplockRetentionMinimum(data.snaplock().retention().minimum());
                volume.setSnaplockRetentionMaximum(data.snaplock().retention().maximum());
            }
        }
    }

    /**
     * Updates a qtree entity from the DTO data.
     *
     * @param qtree the qtree entity to update
     * @param data the qtree data from DTO
     * @param exportPolicy the export policy to assign
     */
    private void updateQtreeFromData(final OntapQtree qtree, final OntapDTO.QTreeData data,
                                     final OntapExportPolicy exportPolicy) {
        qtree.setName(data.name());
        qtree.setPath(data.path());
        qtree.setMountPathNfs(data.mountPathNfs());
        qtree.setSecurityStyle(data.securityStyle());
        qtree.setExportPolicy(exportPolicy);

        if (data.quota() != null) {
            qtree.setQuotaIndex(data.quota().index());
            qtree.setQuotaType(data.quota().type());
            qtree.setQuotaHardLimit(data.quota().hardLimit());
            qtree.setQuotaUsedBytes(data.quota().usedBytes());
            qtree.setQuotaUsedPercent(data.quota().usedPercent());
        }
    }


    // --- Delete Methods ---

    /**
     * Deletes obsolete data in reverse order of dependencies.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteData(final ClusterDataContext context) {
        // Delete in reverse order of dependencies
        deleteObsoleteRules(context);
        deleteObsoleteAcls(context);
        deleteObsoleteCifsShares(context);
        deleteObsoleteQtrees(context);
        deleteObsoleteSnapshots(context);
        deleteObsoleteVolumes(context);
        deleteObsoleteSvms(context);
        deleteObsoleteAggregates(context);
        deleteObsoleteExportPolicies(context);
    }

    /**
     * Deletes obsolete aggregates.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteAggregates(final ClusterDataContext context) {
        final List<OntapAggregate> toDelete = context.existingAggregates.entrySet().stream()
                .filter(e -> !context.importedAggregateUuids.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            // Clear ontapVolumes collection so Hibernate doesn't try to cascade
            // transient volume references during commit flush (owner side of ManyToMany relationship)
            for (final OntapAggregate aggregate : toDelete) {
                aggregate.getOntapVolumes().clear();
            }
            entityManager.flush();

            ontapAggregateRepository.deleteAll(toDelete);
            log.info("Deleted: {} aggregates", toDelete.size());
        }
    }

    /**
     * Deletes obsolete rules.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteRules(final ClusterDataContext context) {
        final List<OntapExportPolicyRule> toDelete = context.existingRules.entrySet().stream()
                .filter(e -> !context.importedRuleKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            exportPolicyRuleRepository.deleteAll(toDelete);
            log.info("Deleted: {} export policy rules", toDelete.size());
        }
    }

    /**
     * Deletes obsolete ACLs.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteAcls(final ClusterDataContext context) {
        final List<OntapCifsShareAcl> toDelete = context.existingAcls.entrySet().stream()
                .filter(e -> !context.importedAclKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            cifsShareAclRepository.deleteAll(toDelete);
            log.info("Deleted: {} ACLs", toDelete.size());
        }
    }

    /**
     * Deletes obsolete CIFS shares.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteCifsShares(final ClusterDataContext context) {
        final List<OntapCifsShare> toDelete = context.existingCifsShares.entrySet().stream()
                .filter(e -> !context.importedCifsShareKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            cifsShareRepository.deleteAll(toDelete);
            log.info("Deleted: {} CIFS shares", toDelete.size());
        }
    }

    /**
     * Deletes obsolete qtrees.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteQtrees(final ClusterDataContext context) {
        final List<OntapQtree> toDelete = context.existingQtrees.entrySet().stream()
                .filter(e -> !context.importedQtreeKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            qtreeRepository.deleteAll(toDelete);
            log.info("Deleted: {} qtrees", toDelete.size());
        }
    }

    /**
     * Deletes obsolete snapshots.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteSnapshots(final ClusterDataContext context) {
        final List<OntapSnapshot> toDelete = context.existingSnapshots.entrySet().stream()
                .filter(e -> !context.importedSnapshotUuids.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            final Set<OntapSnapshot> toDeleteSet = new HashSet<>(toDelete);

            // Remove snapshot from all Volume.ontapSnapshots collections that are still in the session
            // so Hibernate doesn't try to cascade the transient snapshot during commit flush
            // (OntapVolume is owner side of ManyToMany relationship ontap_snapshot_has_volumes)
            for (final OntapVolume volume : context.existingVolumes.values()) {
                volume.getOntapSnapshots().removeIf(toDeleteSet::contains);
            }
            entityManager.flush();

            snapshotRepository.deleteAll(toDelete);
            log.info("Deleted: {} snapshots", toDelete.size());
        }
    }

    /**
     * Deletes obsolete volumes.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteVolumes(final ClusterDataContext context) {
        final List<OntapVolume> toDelete = context.existingVolumes.entrySet().stream()
                .filter(e -> !context.importedVolumeUuids.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            final Set<OntapVolume> toDeleteSet = new HashSet<>(toDelete);

            // IMPORTANT: break Volume -> Snapshot references before any flush
            // (ManyToMany join table + clone lineage fields)
            for (final OntapVolume volume : toDelete) {
                if (volume.getOntapSnapshots() != null) {
                    volume.getOntapSnapshots().clear();
                }
                volume.setParentSnapshot(null);
            }

            // Remove volume from all Aggregate collections that are still in the session,
            // so Hibernate doesn't try to cascade the transient volume during commit flush
            for (final OntapAggregate aggregate : context.existingAggregates.values()) {
                aggregate.getOntapVolumes().removeIf(toDeleteSet::contains);
            }
            entityManager.flush();

            // Null parentVolume reference (for clone hierarchies)
            for (final OntapVolume volume : toDelete) {
                volume.setParentVolume(null);
            }
            volumeRepository.saveAll(toDelete);
            entityManager.flush();

            volumeRepository.deleteAll(toDelete);

            // Aus dem Context entfernen
            toDelete.forEach(v -> context.existingVolumes.values().remove(v));

            log.info("Deleted: {} volumes", toDelete.size());
        }
    }

    /**
     * Deletes obsolete SVMs.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteSvms(final ClusterDataContext context) {
        final List<OntapSvm> toDelete = context.existingSvms.entrySet().stream()
                .filter(e -> !context.importedSvmUuids.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            svmRepository.deleteAll(toDelete);
            log.info("Deleted: {} SVMs", toDelete.size());
        }
    }

    /**
     * Deletes obsolete export policies.
     *
     * @param context the context containing existing data
     */
    private void deleteObsoleteExportPolicies(final ClusterDataContext context) {
        final List<OntapExportPolicy> toDelete = context.existingExportPolicies.entrySet().stream()
                .filter(e -> !context.importedExportPolicyKeys.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!toDelete.isEmpty()) {
            // Rules are deleted by CASCADE
            exportPolicyRepository.deleteAll(toDelete);
            log.info("Deleted: {} export policies", toDelete.size());
        }
    }

    /**
     * Deletes all cluster data using CASCADE DELETE from the database.
     *
     * @param clusterId the cluster ID
     */
    private void deleteAllClusterData(final Long clusterId) {
        // Nutzt CASCADE DELETE der Datenbank
        ontapAggregateRepository.deleteAllByOntapClusterId(clusterId);
        svmRepository.deleteAllByClusterId(clusterId);
        volumeRepository.deleteAllByClusterId(clusterId);
        exportPolicyRepository.deleteAllByClusterId(clusterId);
    }

    // --- Helper Methods ---

    /**
     * Extracts sorted client matches from a rule for consistent key calculation.
     *
     * @param clients the list of clients
     * @return the sorted list of clients
     */
    private List<String> getSortedClients(final List<String> clients) {
        if (clients == null || clients.isEmpty()) {
            return List.of();
        }
        return clients.stream().sorted().toList();
    }

    /**
     * Creates a unique key for an existing rule (including clients).
     *
     * @param rule the rule entity
     * @return the unique key
     */
    private String createRuleKey(final OntapExportPolicyRule rule) {
        return rule.getPolicy().getId() + ":" +
                Objects.hashCode(getSortedClients(rule.getClients())) + ":" +
                Objects.hashCode(rule.getProtocols()) + ":" +
                Objects.hashCode(rule.getRwRules()) + ":" +
                Objects.hashCode(rule.getRoRules());
    }

    /**
     * Creates a unique key for a new rule from the DTO data.
     *
     * @param policyId the policy ID
     * @param ruleData the rule data from DTO
     * @return the unique key
     */
    private String createRuleKeyFromData(final Long policyId, final OntapDTO.ExportRuleData ruleData) {
        final List<String> clients = ruleData.clients() != null
                ? ruleData.clients().stream()
                .map(OntapDTO.ClientMatch::match)
                .sorted()
                .toList()
                : List.of();

        return policyId + ":" +
                Objects.hashCode(clients) + ":" +
                Objects.hashCode(ruleData.protocols()) + ":" +
                Objects.hashCode(ruleData.rwRule()) + ":" +
                Objects.hashCode(ruleData.roRule());
    }

}