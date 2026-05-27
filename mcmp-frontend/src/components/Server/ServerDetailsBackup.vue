<template>
  <CommonCard
    title="Snapshots"
    v-show="selectedServer.cloud?.cloudType == 'VCENTER'"
  >
    <template #toolbar-actions>
      <AddSnapshot
        v-if="
          selectedServer.canEdit && selectedServer.cloud?.cloudType == 'VCENTER'
        "
        :server="props.selectedServer"
        :snapshotCount="
          props.snapshots.filter((s) => !s.description?.includes('NetWorker'))
            .length
        "
        @save="addSnapshot()"
      />
    </template>
    <v-data-table
      :loading="loading[0]"
      :headers="snapshotHeaders"
      :items="snapshots"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.createTime="{ item }">
        {{ formatter.formatToGermanLocalTime(item.createTime) }}
      </template>
      <template #item.retentionPeriod="{ item }">
        {{ formatDeleteDate(item) }}
      </template>
      <template #item.edit="{ item }">
        <DeleteRevertSnapshot
          :snapshot="item"
          :action="'revert'"
          @save="revertSnapshot(item)"
        />
        <DeleteRevertSnapshot
          :snapshot="item"
          :action="'delete'"
          @save="deleteSnapshot(item)"
        />
      </template>
      <template #no-data>
        <v-row>
          <v-col> Keine Snapshots gefunden. </v-col>
        </v-row>
      </template>
    </v-data-table>
  </CommonCard>
  <CommonCard
    title="Backups"
    topMargin="0"
  >
    <template #toolbar-actions>
      <!-- Nur Oracle Backups zurzeit -->
      <v-menu
        v-if="
          selectedServer.canEdit &&
          getBackupTypeFromServerName(selectedServer.name) == 'Oracle'
        "
      >
        <template #activator="{ props: menuProps }">
          <v-tooltip
            text="Backup erstellen"
            location="bottom"
          >
            <template #activator="{ props: tooltipProps }">
              <v-btn
                v-bind="{ ...menuProps, ...tooltipProps }"
                variant="flat"
                icon
                aria-label="Backup erstellen"
                class="mr-0"
              >
                <v-icon>{{ mdiPlus }}</v-icon>
              </v-btn>
            </template>
          </v-tooltip>
        </template>
        <v-list>
          <v-list-item>
            <AddBackup
              v-if="
                selectedServer.canEdit &&
                getBackupTypeFromServerName(selectedServer.name) == 'Oracle'
              "
              :type="getBackupTypeFromServerName(selectedServer.name)"
              :server="selectedServer"
            />
          </v-list-item>
        </v-list>
      </v-menu>
    </template>
    <div class="d-flex flex-row mb-2">
      <v-menu
        :close-on-content-click="false"
        location="right"
      >
        <template #activator="{ props }">
          <v-btn
            v-bind="props"
            density="compact"
            class="mr-4"
            :prepend-icon="mdiFilterVariant"
            flat
          >
            Typ Filter
          </v-btn>
        </template>
        <v-list>
          <v-list-item v-for="type in backupTypes">
            <v-checkbox
              :key="type"
              v-model="selectedTypes"
              :label="getBackupTypeName(type)"
              :value="type"
              hide-details
              density="compact"
              class="mr-4"
            />
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
    <v-data-table
      ref="backupTable"
      :headers="backupHeaders"
      :items="filteredBackups"
      :items-per-page="itemsPerPage"
      :page="currentPage"
      :loading="loading[1]"
      class="elevation-1"
      :items-per-page-options="[5, 10, 25, -1]"
      :sort-by="[{ key: 'saveTime', order: 'desc' }]"
      @update:page="currentPage = $event"
      @update:items-per-page="itemsPerPage = $event"
    >
      <template #item.saveTime="{ item }">
        {{ formatDate(item.saveTime) }}
      </template>
      <template #item.backupType="{ item }">
        {{ getBackupTypeName(item.backupType) }}
      </template>
      <template #item.ssretent="{ item }">
        <span v-if="item.totalsize > 0">
          {{ formatDate(item.ssretent) }}
        </span>
        <span v-else>-</span>
      </template>
      <template #item.totalsize="{ item }">
        <span v-if="item.totalsize > 0">
          {{ formatter.formatBytesSmart(item.totalsize) }}
        </span>
        <span v-else>-</span>
      </template>
      <template #item.runtime="{ item }">
        <span v-if="item.totalsize > 0">
          {{ item.runtime || "-" }}
        </span>
        <span v-else>-</span>
      </template>
      <template #no-data>
        <v-row>
          <v-col> Keine Backups gefunden. </v-col>
        </v-row>
      </template>
    </v-data-table>
  </CommonCard>
</template>

<script setup lang="ts">
import type Backup from "@/types/Backup";
import type Snapshot from "@/types/Snapshot";

import { mdiFilterVariant, mdiPlus } from "@mdi/js";
import { computed, ref } from "vue";

import jobService from "@/api/jobService";
import CommonCard from "@/components/common/CommonCard.vue";
import AddSnapshot from "@/components/Server/AddSnapshot.vue";
import AddBackup from "@/components/Server/Backup/AddBackup.vue";
import DeleteRevertSnapshot from "@/components/Server/DeleteRevertSnapshot.vue";
import { useFormatter } from "@/composables/formatter.js";
import Server from "@/types/Server";

const props = defineProps<{
  selectedServer: Server;
  snapshots: Snapshot[];
  backups: Backup[];
  loading: boolean[];
}>();

const emit = defineEmits<{
  (e: "changed"): void;
}>();

const selectedTypes = ref<string[]>([]);
const jobLoading = ref(false);
const formatter = useFormatter();

// Paginierungszustand beibehalten
const currentPage = ref(1);
const itemsPerPage = ref(5);
const backupTable = ref();

const snapshotHeaders = [
  { title: "Erstellungsdatum", key: "createTime" },
  { title: "Löschungsdatum", key: "retentionPeriod" },
  { title: "Name", key: "name" },
  { title: "Beschreibung", key: "description" },
  { title: "Bearbeiten", key: "edit", sortable: false },
];

const backupHeaders = [
  { title: "Backupzeit", key: "saveTime", sortable: true },
  { title: "Backuptyp", key: "backupType", sortable: false },
  { title: "Backupname", key: "saveSetName", sortable: false },
  { title: "Aufbewahrungszeit", key: "ssretent", sortable: false },
  { title: "Sicherungsgröße", key: "totalsize", sortable: false },
  { title: "Laufzeit (HH:MM:SS)", key: "runtime", sortable: false },
];

const backupTypeNames: Record<string, string> = {
  da: "MariaDB",
  db: "Oracle",
  dh: "SAP HANA",
  dm: "MongoDB",
  dp: "PostgreSQL",
  ds: "MSSQL",
  dy: "MySQL",
};

const formatDate = (date: string) => {
  if (!date) return "-";
  return new Date(date).toLocaleString("de-DE");
};

// In your script, backupTypes should be a set of codes, not display names
const backupTypes = computed(() =>
  Array.from(
    new Set(props.backups.map((b) => b.backupType).filter(Boolean))
  ).sort()
);

// Use this function to get the display name for a backup type
function getBackupTypeName(type: string) {
  return backupTypeNames[type] || type;
}

// Filter backups based on selected types
const filteredBackups = computed(() => {
  if (selectedTypes.value.length === 0) return props.backups;
  return props.backups.filter((b) =>
    selectedTypes.value.includes(b.backupType)
  );
});

function getBackupTypeFromServerName(serverName: string): string {
  const match = serverName.match(/(da|db|dh|dm|dp|ds|dy)([ckps])\d{3}.*/i);
  if (match) {
    const backupTypeKey = match[1];
    return backupTypeNames[backupTypeKey] || backupTypeKey;
  }
  return "Unbekannt"; // Fallback, falls kein Match gefunden wird
}

function deleteSnapshot(snapshot: Snapshot) {
  jobService
    .startJob(jobLoading, "VMWARE_DELETE_SNAPSHOT", props.selectedServer.id, {
      snapshotId: snapshot.snapshotId,
    })
    .then(() => {
      emit("changed");
    });
  // Dummy API request
  //alert(`Delete Snapshot: ${snapshot.name}`);
}

function revertSnapshot(snapshot: Snapshot) {
  jobService
    .startJob(jobLoading, "VMWARE_REVERT_SNAPSHOT", props.selectedServer.id, {
      snapshotId: snapshot.snapshotId,
    })
    .then(() => {
      emit("changed");
    });
  // Dummy API request
  //alert(`Revert Snapshot: ${snapshot.name}`);
}

function addSnapshot() {
  emit("changed");
  // Dummy API request
  //alert(`Add Snapshot für ${props.selectedServer.name}`);
}

function restoreBackup(item: Backup) {
  // Dummy API call
  alert(
    `Backup mit Name '${item.saveSetName}' und Zeit '${formatter.formatToGermanLocalTime(item.saveTime)}' wird wiederhergestellt (Dummy).`
  );
}

function formatDeleteDate(item: Snapshot): string {
  if (item.description?.includes("NetWorker"))
    return "Wird nach Ende des Backups automatisch gelöscht.";
  if (
    props.snapshots.filter((s) => !s.description?.includes("NetWorker"))
      .length > 1
  )
    return "Keine automatische Löschung (Anzahl > 1)";
  if (!item.retentionPeriod) return "";

  return formatter.formatToGermanLocalTime(item.retentionPeriod).split(",")[0];
}
</script>
