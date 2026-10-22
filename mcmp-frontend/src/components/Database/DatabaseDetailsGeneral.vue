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
      title="Verbindungen & Sessions"
    >
      <database-detail-fields :fields="connectionFields" />
    </common-card>

    <common-card
      v-if="cacheFields.length"
      title="Performance & Cache"
    >
      <database-detail-fields :fields="cacheFields" />
    </common-card>

    <common-card
      v-if="waitEvents.length"
      title="Warteereignisse"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Klasse</th>
            <th class="text-left">Ereignis</th>
            <th class="text-right">Anzahl</th>
            <th class="text-right">Gesamtzeit</th>
            <th class="text-right">Ø Zeit</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="wait in waitEvents"
            :key="`${wait.waitClass}-${wait.event}`"
          >
            <td>{{ wait.waitClass ?? "–" }}</td>
            <td class="font-monospace">{{ wait.event }}</td>
            <td class="text-right">{{ formatNumber(wait.count) ?? "–" }}</td>
            <td class="text-right">{{ formatMs(wait.timeMs) }}</td>
            <td class="text-right">{{ formatMs(wait.averageMs) }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="topStatements.length"
      title="Top-SQL (nach Gesamtzeit)"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">SQL-ID</th>
            <th class="text-left">Statement</th>
            <th class="text-right">Ausführungen</th>
            <th class="text-right">Ø Dauer</th>
            <th class="text-right">Gesamt</th>
            <th class="text-right">Zeilen</th>
            <th class="text-right">Buffer Gets</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="statement in topStatements"
            :key="statement.sqlId ?? statement.text"
          >
            <td class="font-monospace text-caption">
              {{ statement.sqlId ?? "–" }}
            </td>
            <td
              class="statement-text"
              :title="statement.text"
            >
              {{ statement.text }}
            </td>
            <td class="text-right">
              {{ formatNumber(statement.executions) ?? "–" }}
            </td>
            <td class="text-right">
              {{ formatMs(statement.averageElapsedMs) }}
            </td>
            <td class="text-right">
              {{ formatMs(statement.totalElapsedMs) }}
            </td>
            <td class="text-right">
              {{ formatNumber(statement.rowsProcessed) ?? "–" }}
            </td>
            <td class="text-right">
              {{ formatNumber(statement.bufferGets) ?? "–" }}
            </td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="replicationFields.length || replicationNodes.length"
      title="Replikation & Hochverfügbarkeit"
    >
      <database-detail-fields :fields="replicationFields" />
      <v-table
        v-if="replicationNodes.length"
        density="compact"
        class="bg-transparent mt-2"
      >
        <thead>
          <tr>
            <th class="text-left">Knoten</th>
            <th class="text-left">Rolle</th>
            <th class="text-left">Status</th>
            <th class="text-left">Sync-Modus</th>
            <th class="text-right">Lag</th>
            <th class="text-left">Letzter Commit</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="node in replicationNodes"
            :key="node.name"
          >
            <td>{{ node.name }}</td>
            <td>{{ node.role }}</td>
            <td>
              <v-chip
                size="small"
                :color="
                  node.state.toUpperCase().includes('SYNC') ||
                  node.state.toUpperCase().includes('ONLINE') ||
                  node.state.toUpperCase().includes('OPEN') ||
                  node.state.toUpperCase().includes('STREAMING')
                    ? 'success'
                    : 'warning'
                "
                variant="tonal"
              >
                {{ node.state }}
              </v-chip>
            </td>
            <td>{{ node.syncMode ?? "–" }}</td>
            <td class="text-right">
              {{ formatSeconds(node.lagSeconds) ?? "–" }}
            </td>
            <td>{{ node.lastCommitAt ?? "–" }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="archiveFields.length"
      title="Archivierung & Log"
    >
      <database-detail-fields :fields="archiveFields" />
    </common-card>

    <common-card
      v-if="dataFiles.length"
      title="Datendateien"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Datei</th>
            <th class="text-left">Pfad</th>
            <th class="text-left">Typ</th>
            <th class="text-right">Größe</th>
            <th class="text-right">Belegt</th>
            <th class="text-right">Max. Größe</th>
            <th class="text-center">Autoextend</th>
            <th class="text-left">Status</th>
            <th class="text-right">Volume frei</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="file in dataFiles"
            :key="file.name"
          >
            <td>{{ file.name }}</td>
            <td
              class="path-text font-monospace text-caption"
              :title="file.path"
            >
              {{ file.path ?? "–" }}
            </td>
            <td>{{ file.type ?? "–" }}</td>
            <td class="text-right">{{ formatMb(file.sizeMb) }}</td>
            <td class="text-right">{{ formatMb(file.usedMb) }}</td>
            <td class="text-right">{{ formatMb(file.maxSizeMb) }}</td>
            <td class="text-center">
              {{ formatBoolean(file.autoextend) }}
            </td>
            <td>{{ file.status ?? "–" }}</td>
            <td class="text-right">{{ formatGb(file.volumeFreeGb) ?? "–" }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="objectHealthFields.length"
      title="Objektzustand"
    >
      <database-detail-fields :fields="objectHealthFields" />
    </common-card>

    <common-card
      v-if="tableStats.length"
      title="Tabellen (Statistik)"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Schema</th>
            <th class="text-left">Tabelle</th>
            <th class="text-right">Zeilen</th>
            <th class="text-right">Größe</th>
            <th class="text-right">Frei</th>
            <th class="text-right">Dead Rows</th>
            <th class="text-right">Dead %</th>
            <th class="text-left">Letzte Analyse</th>
            <th class="text-left">Scans (seq / idx)</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="table in tableStats"
            :key="`${table.schema}.${table.table}`"
          >
            <td>{{ table.schema }}</td>
            <td>{{ table.table }}</td>
            <td class="text-right">{{ formatNumber(table.rows) ?? "–" }}</td>
            <td class="text-right">{{ formatMb(table.sizeMb) }}</td>
            <td class="text-right">{{ formatMb(table.freeMb) }}</td>
            <td class="text-right">
              {{ formatNumber(table.deadRows) ?? "–" }}
            </td>
            <td class="text-right">
              {{ formatPercentValue(table.deadRowPercent) ?? "–" }}
            </td>
            <td>{{ table.lastAnalyzed ?? "–" }}</td>
            <td>
              {{ formatNumber(table.sequentialScans) ?? "–" }} /
              {{ formatNumber(table.indexScans) ?? "–" }}
            </td>
          </tr>
        </tbody>
      </v-table>
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
      v-if="accounts.length"
      title="Accounts"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Login</th>
            <th class="text-left">Host</th>
            <th class="text-left">Status</th>
            <th class="text-center">Gesperrt</th>
            <th class="center">Pwd. abgelaufen</th>
            <th class="text-left">Rollen / Rechte</th>
            <th class="text-left">Pwd. gültig bis</th>
            <th class="text-right">Conn-Limit</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="account in accounts"
            :key="`${account.user}-${account.host ?? ''}`"
          >
            <td>{{ account.user }}</td>
            <td>{{ account.host ?? "–" }}</td>
            <td>
              <v-chip
                size="small"
                :color="account.canLogin === false ? 'warning' : 'success'"
                variant="tonal"
              >
                {{ account.accountStatus ?? "–" }}
              </v-chip>
            </td>
            <td class="text-center">
              {{ formatBoolean(account.isLocked) }}
            </td>
            <td class="text-center">
              {{ formatBoolean(account.passwordExpired) }}
            </td>
            <td>{{ (account.roles ?? []).join(", ") || "–" }}</td>
            <td>{{ account.passwordExpiresAt ?? "–" }}</td>
            <td class="text-right">
              {{ formatNumber(account.connectionLimit) ?? "–" }}
            </td>
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
      v-if="parameters.length"
      title="Parameter (vom Default abweichend)"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Parameter</th>
            <th class="text-left">Wert</th>
            <th class="text-center">Default</th>
            <th class="text-center">Änderbar</th>
            <th class="text-center">Neustart nötig</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="parameter in parameters"
            :key="parameter.name"
          >
            <td class="font-monospace">{{ parameter.name }}</td>
            <td class="font-monospace">{{ parameter.value }}</td>
            <td class="text-center">
              {{ formatBoolean(parameter.isDefault) }}
            </td>
            <td class="text-center">
              {{ formatBoolean(parameter.modifiable) }}
            </td>
            <td class="text-center">
              {{ formatBoolean(parameter.pendingRestart) }}
            </td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="backupHistory.length"
      title="Backup-Historie (aus der DB)"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Typ</th>
            <th class="text-left">Start</th>
            <th class="text-left">Ende</th>
            <th class="text-right">Größe</th>
            <th class="text-left">Status</th>
            <th class="text-left">Ziel</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="backup in backupHistory"
            :key="`${backup.type}-${backup.startedAt}`"
          >
            <td>{{ backup.type }}</td>
            <td>{{ backup.startedAt }}</td>
            <td>{{ backup.finishedAt ?? "–" }}</td>
            <td class="text-right">{{ formatGb(backup.sizeGb) ?? "–" }}</td>
            <td>
              <v-chip
                size="small"
                :color="backup.status === 'COMPLETED' ? 'success' : 'error'"
                variant="tonal"
              >
                {{ backup.status }}
              </v-chip>
            </td>
            <td>{{ backup.destination ?? "–" }}</td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="securityFields.length"
      title="Sicherheit & Verschlüsselung"
    >
      <database-detail-fields :fields="securityFields" />
    </common-card>

    <common-card
      v-if="schedulerJobs.length"
      title="Scheduler-Jobs"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Job</th>
            <th class="text-left">Schema</th>
            <th class="text-center">Aktiv</th>
            <th class="text-left">Status</th>
            <th class="text-left">Letzter Lauf</th>
            <th class="text-left">Nächster Lauf</th>
            <th class="text-right">Fehler</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="job in schedulerJobs"
            :key="job.name"
          >
            <td>{{ job.name }}</td>
            <td>{{ job.schema ?? "–" }}</td>
            <td class="text-center">{{ formatBoolean(job.enabled) }}</td>
            <td>{{ job.state ?? "–" }}</td>
            <td>{{ job.lastStartAt ?? "–" }}</td>
            <td>{{ job.nextRunAt ?? "–" }}</td>
            <td class="text-right">
              {{ formatNumber(job.failureCount) ?? "–" }}
            </td>
          </tr>
        </tbody>
      </v-table>
    </common-card>

    <common-card
      v-if="errorFields.length"
      title="Fehler (Log / Ringpuffer)"
    >
      <database-detail-fields :fields="errorFields" />
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
      v-if="tablespaces.length"
      title="Tablespaces"
    >
      <v-table
        density="compact"
        class="bg-transparent"
      >
        <thead>
          <tr>
            <th class="text-left">Tablespace</th>
            <th class="text-right">Größe</th>
            <th class="text-right">Belegt</th>
            <th class="text-right">Frei</th>
            <th class="text-right">Auslastung</th>
            <th class="text-center">Autoextend</th>
            <th class="text-right">Max. Größe</th>
            <th class="text-left">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="tablespace in tablespaces"
            :key="tablespace.name"
          >
            <td>{{ tablespace.name }}</td>
            <td class="text-right">{{ formatGb(tablespace.sizeGb) ?? "–" }}</td>
            <td class="text-right">{{ formatGb(tablespace.usedGb) ?? "–" }}</td>
            <td class="text-right">{{ formatGb(tablespace.freeGb) ?? "–" }}</td>
            <td class="text-right">
              {{ formatPercent(tablespace.usedPercent) }}
            </td>
            <td class="text-center">
              {{ formatBoolean(tablespace.autoextend) }}
            </td>
            <td class="text-right">
              {{ formatGb(tablespace.maxSizeGb) ?? "–" }}
            </td>
            <td>
              <v-chip
                size="small"
                color="success"
                variant="tonal"
              >
                {{ tablespace.status ?? "–" }}
              </v-chip>
            </td>
          </tr>
        </tbody>
      </v-table>
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
      v-if="operationFields.length"
      title="Betrieb (Portal-Metadaten)"
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


import type {
  DatabaseDetail,
  DatabaseDetailField,
  DatabaseSessionSummary,
} from "@/types/Database";

import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import DatabaseDetailFields from "@/components/Database/DatabaseDetailFields.vue";
import { buildJdbcUrl } from "@/util/jdbcUrl";

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
const tablespaces = computed(() => detail.value?.tablespaces ?? []);

const sessions = computed(() => detail.value?.sessions ?? null);
const cache = computed(() => detail.value?.cache ?? null);
const replication = computed(() => detail.value?.replication ?? null);
const archive = computed(() => detail.value?.archive ?? null);
const security = computed(() => detail.value?.security ?? null);
const objectHealth = computed(() => detail.value?.objectHealth ?? null);
const errorSummary = computed(() => detail.value?.errors ?? null);

const waitEvents = computed(() => detail.value?.waitEvents ?? []);
const topStatements = computed(() => detail.value?.topStatements ?? []);
const dataFiles = computed(() => detail.value?.dataFiles ?? []);
const tableStats = computed(() => detail.value?.tableStats ?? []);
const accounts = computed(() => detail.value?.accounts ?? []);
const parameters = computed(() => detail.value?.parameters ?? []);
const backupHistory = computed(() => detail.value?.backupHistory ?? []);
const schedulerJobs = computed(() => detail.value?.schedulerJobs ?? []);
const replicationNodes = computed(() => replication.value?.nodes ?? []);
const jdbcUrl = computed(() =>
  detail.value ? buildJdbcUrl(detail.value) : null
);
function toField(
  label: string,
  value: string | number | null | undefined
): DatabaseDetailField | null {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  return text ? { label, value: text } : null;
}

function toBooleanField(
  label: string,
  value?: boolean | null
): DatabaseDetailField | null {
  if (value === null || value === undefined) return null;
  return { label, value: value ? "Ja" : "Nein" };
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

function formatMb(value?: number | null): string {
  if (value === null || value === undefined) return "–";
  if (value >= 1024) {
    return `${(value / 1024).toLocaleString("de-DE", {
      maximumFractionDigits: 1,
    })} GB`;
  }
  return `${value.toLocaleString("de-DE")} MB`;
}

function formatBytesGb(value?: number | null): string | null {
  if (value === null || value === undefined) return null;
  return `${(value / 1073741824).toLocaleString("de-DE", {
    maximumFractionDigits: 1,
  })} GB`;
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

function formatMs(value?: number | null): string {
  if (value === null || value === undefined || value === 0) return "–";
  if (value >= 60000) {
    return `${(value / 60000).toLocaleString("de-DE", {
      maximumFractionDigits: 1,
    })} min`;
  }
  if (value >= 1000) {
    return `${(value / 1000).toLocaleString("de-DE", {
      maximumFractionDigits: 1,
    })} s`;
  }
  return `${value.toLocaleString("de-DE")} ms`;
}

function formatPercentValue(value?: number | null): string | null {
  if (value === null || value === undefined) return null;
  return `${value.toFixed(1).replace(".", ",")} %`;
}

function formatPercent(value?: number | null): string {
  return formatPercentValue(value) ?? "–";
}

function formatBoolean(value?: boolean | null): string {
  if (value === null || value === undefined) return "–";
  return value ? "Ja" : "Nein";
}

function utilizationOf(summary: DatabaseSessionSummary | null): number | null {
  if (!summary) return null;
  if (summary.utilizationPercent !== undefined)
    return summary.utilizationPercent;
  if (!summary.maxConnections || summary.active === undefined) return null;
  return (summary.active / summary.maxConnections) * 100;
}

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
    toField("Startzeit (Uptime-Basis)", database.startupTime),
    toField("Port", database.port),
    toField("Archive-Log-Modus", database.archiveLogMode),
    jdbcUrl.value
      ? {
        label: "JDBC-URL",
        value: jdbcUrl.value,
        fullWidth: true,
        monospace: true,
        copyable: true,
        hint: "Abgeleitet aus Host, Port und Servicename – enthält bewusst keine Zugangsdaten.",
      }
      : null,
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

  const summary = sessions.value;

  return compactFields([
    toField(
      "Max. Verbindungen",
      formatNumber(summary?.maxConnections ?? database.maxConnections)
    ),
    toField("Sessions gesamt", formatNumber(summary?.total)),
    toField(
      "Aktiv",
      formatNumber(summary?.active ?? database.activeConnections)
    ),
    toField("Idle", formatNumber(summary?.idle)),
    toField("Idle in Transaktion", formatNumber(summary?.idleInTransaction)),
    toField("Blockierte Sessions", formatNumber(summary?.blockedSessions)),
    toField("Höchststand genutzt", formatNumber(summary?.maxUsedConnections)),
    toField("Auslastung", formatPercentValue(utilizationOf(summary))),
  ]);
});

const cacheFields = computed<DatabaseDetailField[]>(() => {
  const stats = cache.value;
  if (!stats) return [];

  return compactFields([
    toField(
      "Buffer-Cache-Hit-Ratio",
      formatPercentValue(stats.bufferCacheHitPercent)
    ),
    toField(
      "Shared-Pool-Hit-Ratio",
      formatPercentValue(stats.sharedPoolHitPercent)
    ),
    toField(
      "Plan-Cache-Hit-Ratio",
      formatPercentValue(stats.planCacheHitPercent)
    ),
    toField(
      "Dictionary-Cache-Hit-Ratio",
      formatPercentValue(stats.dictionaryCacheHitPercent)
    ),
    toField("Temp-Dateien", formatNumber(stats.tempFiles)),
    toField("Temp-Volumen", formatBytesGb(stats.tempBytes)),
  ]);
});

const replicationFields = computed<DatabaseDetailField[]>(() => {
  const status = replication.value;
  if (!status) return [];

  return compactFields([
    toField("Modus", status.mode),
    toField("Rolle dieser Instanz", status.role),
    toBooleanField("Status gesund", status.healthy),
    toField("Max. Lag", formatSeconds(status.maxLagSeconds)),
    toField("Knoten", status.nodes.length ? String(status.nodes.length) : null),
  ]);
});

const archiveFields = computed<DatabaseDetailField[]>(() => {
  const status = archive.value;
  if (!status) return [];

  return compactFields([
    toField("Log-Modus", status.logMode),
    toBooleanField("Archivierung aktiv", status.enabled),
    toField("Ziel", status.destination),
    toField("Aufbewahrung", status.retention),
    toField("Archivierte Logs", formatNumber(status.archivedCount)),
    toField("Letzte Archivierung", status.lastArchivedAt),
    toField("Fehlgeschlagen", formatNumber(status.failedCount)),
    toField("Letzter Fehlschlag", status.lastFailedAt),
    toBooleanField("Lücke erkannt", status.gapDetected),
    toField("Bereich belegt", formatPercentValue(status.areaUsedPercent)),
  ]);
});

const objectHealthFields = computed<DatabaseDetailField[]>(() => {
  const health = objectHealth.value;
  if (!health) return [];

  return compactFields([
    toField("Tabellen", formatNumber(health.tables)),
    toField("Indizes", formatNumber(health.indexes)),
    toField("Views", formatNumber(health.views)),
    toField("Invalide Objekte", formatNumber(health.invalidObjects)),
    toField("Unusable Indizes", formatNumber(health.unusableIndexes)),
    toField("Ungenutzte Indizes", formatNumber(health.unusedIndexes)),
    toField("Tabellen ohne PK", formatNumber(health.tablesWithoutPrimaryKey)),
  ]);
});

const securityFields = computed<DatabaseDetailField[]>(() => {
  const info = security.value;
  if (!info) return [];

  return compactFields([
    toBooleanField("TDE / Verschlüsselung at rest", info.tdeEnabled),
    toBooleanField("TLS aktiv", info.tlsEnabled),
    toField("TLS-Version", info.tlsVersion),
    toField("TLS-Cipher", info.tlsCipher),
    toField("Authentifizierung", info.authMode),
    toField("Passwort-Policy", info.passwordPolicy),
    toBooleanField("Audit aktiv", info.auditEnabled),
  ]);
});

const errorFields = computed<DatabaseDetailField[]>(() => {
  const summary = errorSummary.value;
  if (!summary) return [];

  return compactFields([
    toField("Letzter Fehler", summary.lastErrorAt),
    toField("Fehler (24 h)", formatNumber(summary.errorsLast24h)),
    toField("Meldung", summary.lastErrorMessage),
  ]);
});

const operationFields = computed<DatabaseDetailField[]>(() => {
  const database = detail.value;
  if (!database) return [];

  return compactFields([
    toField("Letztes Backup (Kurzinfo)", database.lastBackup),
    toField("Backup-Klasse", database.backupClass),
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

.statement-text {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
  font-size: 0.8rem;
}

.path-text {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-monospace {
  font-family: monospace;
}
</style>
