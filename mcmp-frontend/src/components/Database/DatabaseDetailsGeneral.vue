<template>
  <div
    v-if="hasDetail"
    class="database-details-general"
  >
    <v-row class="ma-0">
      <v-col class="d-flex flex-wrap align-center ga-2 px-0">
        <span class="text-h6 font-weight-medium">{{ headerName }}</span>
        <v-chip
          size="small"
          color="primary"
          variant="tonal"
        >
          {{ headerEngine }}
        </v-chip>
        <v-chip
          size="small"
          variant="tonal"
        >
          {{ deploymentLabel }}
        </v-chip>
        <v-chip
          v-if="headerEnvironment"
          size="small"
          color="info"
          variant="tonal"
        >
          {{ headerEnvironment }}
        </v-chip>
      </v-col>
    </v-row>

    <div class="dummy-data-banner__description">
      Alle auf dieser Seite angezeigten Daten sind frei erfundene Beispiel- und
      Testdaten. Es handelt sich <strong>nicht</strong> um reale Systeme,
      Datenbanken oder Kundeninformationen!
    </div>

    <common-card
      v-if="generalFields.length"
      title="Allgemein"
    >
      <database-detail-fields :fields="generalFields" />
    </common-card>

    <common-card
      v-if="capacityFields.length"
      title="Kapazität & Speicher"
    >
      <database-detail-fields :fields="capacityFields" />
    </common-card>

    <common-card
      v-if="connectionFields.length"
      title="Verbindungen"
    >
      <database-detail-fields :fields="connectionFields" />
    </common-card>

    <common-card
      v-if="options.length"
      title="Optionen (DB-Optionen)"
    >
      <div class="d-flex flex-wrap ga-2">
        <v-chip
          v-for="option in options"
          :key="option"
          size="small"
          color="primary"
          variant="tonal"
        >
          {{ option }}
        </v-chip>
      </div>
    </common-card>

    <common-card
      v-if="serviceNames.length"
      title="Service-Namen"
    >
      <div class="d-flex flex-wrap ga-2">
        <v-chip
          v-for="service in serviceNames"
          :key="service"
          size="small"
          variant="outlined"
        >
          {{ service }}
        </v-chip>
      </div>
    </common-card>

    <common-card
      v-if="extensions.length"
      title="Extensions & Komponenten"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Name</th>
            <th class="text-left">Version</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="extension in extensions"
            :key="extension.name"
          >
            <td>{{ extension.name }}</td>
            <td>{{ extension.version }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="loginPrivileges.length"
      title="Logins & Berechtigungen"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Login</th>
            <th class="text-left">Host</th>
            <th class="text-left">Berechtigung</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="login in loginPrivileges"
            :key="`${login.user}-${login.host}`"
          >
            <td>{{ login.user }}</td>
            <td>{{ login.host }}</td>
            <td>{{ login.privilege }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="nodes.length"
      title="Server / Cluster-Knoten"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Hostname</th>
            <th class="text-left">FQDN</th>
            <th class="text-left">Rolle</th>
            <th class="text-left">IP-Adresse</th>
            <th class="text-left">Status</th>
            <th class="text-right">CPU</th>
            <th class="text-right">RAM</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="node in nodes"
            :key="node.hostname"
          >
            <td>{{ node.hostname }}</td>
            <td>{{ node.fqdn }}</td>
            <td>{{ node.role }}</td>
            <td>{{ node.ipAddress }}</td>
            <td>
              <v-chip
                size="small"
                color="success"
                variant="tonal"
              >
                {{ node.state }}
              </v-chip>
            </td>
            <td class="text-right">{{ node.cpuCores ?? "–" }}</td>
            <td class="text-right">{{ formatMemory(node.memoryGb) }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="operationFields.length"
      title="Betrieb & CMDB"
    >
      <database-detail-fields :fields="operationFields" />
    </common-card>
  </div>

  <v-alert
    v-else
    type="info"
    variant="tonal"
    class="ma-4"
  >
    Bitte links eine Datenbank auswählen, um die Details anzuzeigen.
  </v-alert>
</template>

<script setup lang="ts">
import type { DatabaseDetail, DatabaseDetailField } from "@/types/Database";

import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import DatabaseDetailFields from "@/components/Database/DatabaseDetailFields.vue";

const props = withDefaults(
  defineProps<{
    databaseDetail?: DatabaseDetail | null;
  }>(),
  {
    databaseDetail: null,
  }
);

const detail = computed(() => props.databaseDetail);
const hasDetail = computed(() => detail.value !== null);

const headerName = computed(() => detail.value?.name ?? "");
const headerEngine = computed(() => detail.value?.engine ?? "");
const headerEnvironment = computed(() => detail.value?.environment ?? "");

const deploymentLabel = computed(() => {
  const type = detail.value?.deploymentType;
  if (!type) return "";
  const labels: Record<string, string> = {
    SINGLE: "Single Instance",
    RAC: "Oracle RAC",
    CLUSTER: "Cluster",
  };
  return labels[type] ?? type;
});

const options = computed(() => detail.value?.options ?? []);
const serviceNames = computed(() => detail.value?.serviceNames ?? []);
const extensions = computed(() => detail.value?.extensions ?? []);
const loginPrivileges = computed(() => detail.value?.loginPrivileges ?? []);
const nodes = computed(() => detail.value?.nodes ?? []);

function toField(
  label: string,
  value: string | number | null | undefined
): DatabaseDetailField | null {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  return text ? { label, value: text } : null;
}

function compactFields(
  entries: Array<DatabaseDetailField | null>
): DatabaseDetailField[] {
  return entries.filter(
    (entry): entry is DatabaseDetailField => entry !== null
  );
}

function formatGb(value?: number | null): string | null {
  if (value === null || value === undefined) return null;
  return `${value.toLocaleString("de-DE")} GB`;
}

function formatNumber(value?: number | null): string | null {
  if (value === null || value === undefined) return null;
  return value.toLocaleString("de-DE");
}

function formatMemory(value?: number | null): string {
  return formatGb(value) ?? "–";
}

function formatSeconds(value?: number | null): string | null {
  if (value === null || value === undefined) return null;
  return `${value} s`;
}

const connectionUtilization = computed(() => {
  const database = detail.value;
  if (
    !database ||
    !database.maxConnections ||
    database.activeConnections === null ||
    database.activeConnections === undefined
  ) {
    return null;
  }
  const percent = (database.activeConnections / database.maxConnections) * 100;
  return `${percent.toFixed(1).replace(".", ",")} %`;
});

const generalFields = computed<DatabaseDetailField[]>(() => {
  const database = detail.value;
  if (!database) return [];

  return compactFields([
    toField("FQDN", database.fqdn),
    toField(
      database.pdbName ? "PDB-Name" : "Datenbankname",
      database.pdbName ?? database.databaseName
    ),
    toField("Instanzname", database.instanceName),
    toField("Version", database.version),
    toField("Edition", database.edition),
    toField("Datenbanktyp", database.databaseType ?? database.deploymentType),
    toField("Cluster", database.clusterName),
    toField("SCAN / Listener", database.scanName),
    toField("Character Set", database.characterSet),
    toField("Collation", database.collation),
    toField("Startzeit", database.startupTime),
    toField("Port", database.port),
    toField("Patch-Stand", database.patchLevel),
    toField("Archive-Log-Modus", database.archiveLogMode),
  ]);
});

const capacityFields = computed<DatabaseDetailField[]>(() => {
  const database = detail.value;
  if (!database) return [];

  return compactFields([
    toField("Größe DB", formatGb(database.sizeDbGb)),
    toField("Größe SGA", formatGb(database.sizeSgaGb)),
    toField("Größe PGA", formatGb(database.sizePgaGb)),
    toField("Größe Log / Redo", formatGb(database.sizeLogGb)),
    toField("Größe TempDB", formatGb(database.sizeTempDbGb)),
    toField("Datenverzeichnis", database.dataDirectory),
  ]);
});

const connectionFields = computed<DatabaseDetailField[]>(() => {
  const database = detail.value;
  if (!database) return [];

  return compactFields([
    toField("Max. Verbindungen", formatNumber(database.maxConnections)),
    toField("Aktive Verbindungen", formatNumber(database.activeConnections)),
    toField("Aktive Logins", formatNumber(database.activeLogins)),
    toField("Auslastung", connectionUtilization.value),
  ]);
});

const operationFields = computed<DatabaseDetailField[]>(() => {
  const database = detail.value;
  if (!database) return [];

  return compactFields([
    toField("Letztes Backup", database.lastBackup),
    toField("Backup-Klasse", database.backupClass),
    toField("Replikation", database.replicationMode),
    toField("Replikations-Lag", formatSeconds(database.replicationLagSeconds)),
    toField("Umgebung", database.environment),
    toField("CI-Name (CMDB)", database.cmdbCiName),
    toField("Owner", database.owner),
    toField("Support-Gruppe", database.supportGroup),
    toField("Standort", database.location),
    toField("SLA-Klasse", database.slaClass),
  ]);
});
</script>

<style scoped>
.database-details-general {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dummy-data-banner__description {
  margin-top: 2px;
  font-size: 0.95rem;
  line-height: 1.35;
  color: red;
}
</style>
