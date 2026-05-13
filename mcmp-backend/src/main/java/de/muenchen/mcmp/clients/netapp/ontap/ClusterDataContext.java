package de.muenchen.mcmp.clients.netapp.ontap;

import de.muenchen.mcmp.ontap.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Kontext-Klasse zum Halten aller existierenden und importierten Daten eines Clusters.
 */
class ClusterDataContext {
    ConfigOntapCluster cluster;

    // Existing data maps
    Map<String, OntapSvm> existingSvms = new HashMap<>();
    Map<String, OntapVolume> existingVolumes = new HashMap<>();
    Map<String, OntapAggregate> existingAggregates = new HashMap<>();
    Map<String, OntapExportPolicy> existingExportPolicies = new HashMap<>();
    Map<String, OntapExportPolicyRule> existingRules = new HashMap<>();
    Map<String, OntapCifsShare> existingCifsShares = new HashMap<>();
    Map<String, OntapCifsShareAcl> existingAcls = new HashMap<>();
    Map<String, OntapQtree> existingQtrees = new HashMap<>();
    Map<String, OntapSnapshot> existingSnapshots = new HashMap<>();

    // Imported keys for deletion detection
    final Set<String> importedSvmUuids = new HashSet<>();
    final Set<String> importedVolumeUuids = new HashSet<>();
    final Set<String> importedAggregateUuids = new HashSet<>();
    final Set<String> importedExportPolicyKeys = new HashSet<>();
    final Set<String> importedRuleKeys = new HashSet<>();
    final Set<String> importedCifsShareKeys = new HashSet<>();
    final Set<String> importedAclKeys = new HashSet<>();
    final Set<String> importedQtreeKeys = new HashSet<>();
    final Set<String> importedSnapshotUuids = new HashSet<>();
}
