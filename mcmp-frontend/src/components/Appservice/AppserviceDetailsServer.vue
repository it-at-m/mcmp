<template>
  <div
    v-if="props.selectedAppservice?.servers?.length"
    class="links"
  >
    <common-card
      :title="cardTitle"
      top-margin="0"
      :is-default-expanded="false"
    >
      <template #append-title>
        <count-badge :count="serverCount" />
        <v-tooltip
          v-if="anyServerHasWarnings"
          location="top"
          text="Handlungsbedarf an mind. einem Server"
        >
          <template #activator="{ props: tooltipProps }">
            <v-icon
              v-bind="tooltipProps"
              :icon="mdiAlert"
              color="orange"
              size="22"
              class="ml-2"
            />
          </template>
        </v-tooltip>
      </template>
      <template #toolbar-actions>
        <div class="action-buttons">
          <add-snapshot
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleForSnapshot
            "
            :parent-disabled-tooltip="snapshotDisabledTooltip"
            @save="onBatchOrderCompleteDone"
          />
          <action-button
            color="btn_green"
            :icon="mdiPlay"
            :tooltip="powerStartTooltip"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleToStart
            "
            job-to-call="START_SERVER"
            show-confirm-dialog
            confirm-dialog-title="VM Starten"
            confirm-dialog-text="Wollen Sie diese VMs wirklich starten?"
            @change="onBatchOrderCompleteDone"
          />
          <action-button
            color="btn_red"
            :icon="mdiStop"
            :tooltip="powerStopTooltip"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleToStop
            "
            job-to-call="STOP_SERVER"
            show-confirm-dialog
            confirm-dialog-title="VM Stoppen"
            confirm-dialog-text="Wollen Sie diese VMs wirklich stoppen?"
            use-extra-sure-dialog
            extra-sure-checkbox-text="Mir ist bewusst, dass durch das Stoppen der VM eine Serviceunterbrechung entsteht."
            @change="onBatchOrderCompleteDone"
          />
          <action-button
            color="btn_red"
            :icon="mdiRestart"
            :tooltip="powerRestartTooltip"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleToRestart
            "
            job-to-call="RESTART_SERVER"
            show-confirm-dialog
            confirm-dialog-title="VM Neustarten"
            confirm-dialog-text="Wollen Sie diese VMs wirklich neustarten?"
            use-extra-sure-dialog
            extra-sure-checkbox-text="Mir ist bewusst, dass durch das Neustarten der VM eine Serviceunterbrechung entsteht."
            @change="onBatchOrderCompleteDone"
          />
          <pause-server-btn
            :icon="mdiWrenchClock"
            :tooltip="powerPauseTooltip"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleToPause
            "
            @change="onBatchOrderCompleteDone"
          />
          <root-admin-rechte-btn
            :icon="mdiKeyChain"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :disabled="!allSelectedServersEligibleForRoot"
            :tooltip="rootAdminTooltip"
            :show-confirm-dialog="true"
            :confirm-dialog-title="`Root/Admin-Rechte für ${selectedServers.length} Server bestellen`"
            :confirm-dialog-text="`Sind Sie sicher, dass Sie Root/Admin-Rechte für die ${selectedServers.length} ausgewählten Server bestellen möchten?`"
            @change="onBatchOrderCompleteDone"
          />
          <check-mk-dialog
            title="Downtime setzen"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleForDowntime
            "
            :parent-disabled-tooltip="downtimeDisabledTooltip"
            @save="onBatchOrderCompleteDone"
          />
          <win-wartungs-modus-batch-menu
            :selected-server-ids="selectedServers"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleForWindowsMaintenance
            "
            :parent-disabled-tooltip="windowsMaintenanceDisabledTooltip"
            @save="onBatchOrderCompleteDone"
          />
        </div>
      </template>
      <v-data-table
        v-model="selectedServers"
        :headers="headers"
        :items="servers"
        :items-per-page="-1"
        item-value="id"
        density="compact"
        hover
        show-select
        hide-default-footer
        class="server-table"
      >
        <template #item.serverKind="{ item }">
          <v-tooltip
            v-if="serverKindText(item.serverKind)"
            :text="serverKindText(item.serverKind)"
          >
            <template #activator="{ props }">
              <v-icon
                v-bind="props"
                size="small"
                class="server-kind-icon"
              >
                {{ serverKindIcon(item.serverKind) }}
              </v-icon>
            </template>
          </v-tooltip>
        </template>
        <template #item.name="{ item }">
          <div class="d-flex align-center">
            <span class="font-weight-bold">
              <router-link :to="`/server/${item.id}`">
                {{ item.name }}
              </router-link>
            </span>
            <v-tooltip
              v-if="item.hasWarnings"
              location="top"
              text="Handlung erforderlich"
            >
              <template #activator="{ props: tooltipProps }">
                <router-link
                  :to="`/server/${item.id}`"
                  class="d-flex align-center text-decoration-none"
                >
                  <v-icon
                    v-bind="tooltipProps"
                    :icon="mdiAlert"
                    color="orange"
                    size="20"
                    class="ml-1"
                  />
                </router-link>
              </template>
            </v-tooltip>
          </div>
        </template>
        <template #item.powerState="{ item }">
          <div class="d-flex align-center">
            <div class="power-state-icon-inline mr-2">
              <v-icon
                :color="
                  item.powerState === 'poweredOn'
                    ? 'btn_green'
                    : item.powerState === 'poweredOff'
                      ? 'btn_red'
                      : 'accent'
                "
                size="20"
              >
                {{
                  item.powerState === "poweredOn"
                    ? mdiPlayCircle
                    : item.powerState === "poweredOff"
                      ? mdiStopCircle
                      : mdiPauseCircle
                }}
              </v-icon>
            </div>
            <span>
              {{
                item.powerState === "poweredOn"
                  ? "Ein"
                  : item.powerState === "poweredOff"
                    ? "Aus"
                    : "Standby"
              }}
            </span>
          </div>
        </template>
        <template #item.os="{ item }">
          <div class="d-flex align-center">
            <os-cell
              :os-full-name="item.os || ''"
              size="small"
              class="os-icon-inline mr-2"
            />
            <span class="text-caption">
              {{ item.os }}
            </span>
          </div>
        </template>
        <template #item.appserviceNames="{ item }">
          <div class="app-services-cell">
            <template v-if="item.appserviceNames">
              <ul class="pl-4">
                <li
                  v-for="(service, index) in item.appserviceNames.split('|')"
                  :key="index"
                >
                  {{ service.trim() }}
                </li>
              </ul>
            </template>
            <span v-else>-</span>
          </div>
        </template>
        <template #item.memoryMb="{ item }">
          {{ formatter.formatMBtoGB(item.memoryMb ?? 0) }} GB
        </template>
        <template #item.vdisksCapacityInBytes="{ item }">
          {{ formatter.formatBtoGB(item.vdisksCapacityInBytes ?? 0) }} GB
        </template>
      </v-data-table>
    </common-card>
  </div>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";
import type Server from "@/types/Server.ts";
import type { DataTableHeader } from "vuetify";

import {
  mdiAlert,
  mdiCloud,
  mdiKeyChain,
  mdiPauseCircle,
  mdiPlay,
  mdiPlayCircle,
  mdiRestart,
  mdiServer,
  mdiStop,
  mdiStopCircle,
  mdiWrenchClock,
} from "@mdi/js";
import { computed, ref, watch } from "vue";

import serverService from "@/api/serverService";
import snapshotService from "@/api/snapshotService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import ActionButton from "@/components/Server/ActionButtons/ActionButton.vue";
import CheckMkDialog from "@/components/Server/ActionButtons/CheckMkDialog.vue";
import PauseServerBtn from "@/components/Server/ActionButtons/PauseServerBtn.vue";
import RootAdminRechteBtn from "@/components/Server/ActionButtons/RootAdminRechteBtn.vue";
import WinWartungsModusBatchMenu from "@/components/Server/ActionButtons/WinWartungsModusBatchMenu.vue";
import AddSnapshot from "@/components/Server/AddSnapshot.vue";
import OsCell from "@/components/Server/OsCell.vue";
import { useFormatter } from "@/composables/formatter.ts";
import { useUserStore } from "@/stores/user.ts";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();
const userStore = useUserStore();
const isOperator = computed(() =>
  userStore.getUser?.authorities.includes("ROLE_OPERATOR")
);

const cardTitle = computed(() => "Zugeordnete Server");
const serverCount = computed(
  () => props.selectedAppservice?.servers?.length ?? 0
);

const serverKindText = (kind?: string | null) => {
  if (!kind) return "";
  switch (String(kind).toUpperCase()) {
    case "HARDWARE":
      return "Hardware Server";
    case "VIRTUAL":
      return "Virtuelle Maschine";
    default:
      return "";
  }
};

const serverKindIcon = (kind?: string | null) => {
  if (!kind) return "";
  switch (String(kind).toUpperCase()) {
    case "HARDWARE":
      return mdiServer;
    case "VIRTUAL":
      return mdiCloud;
    default:
      return "";
  }
};

const localeCompare = (a: unknown, b: unknown) =>
  String(a ?? "")
    .toLowerCase()
    .localeCompare(String(b ?? "").toLowerCase());

const numericCompare = (a: unknown, b: unknown) =>
  Number(a ?? 0) - Number(b ?? 0);

const headers: DataTableHeader[] = [
  { title: "Typ", key: "serverKind", sort: localeCompare, width: 60 },
  { title: "Servername", key: "name", sort: localeCompare },
  { title: "Status", key: "powerState", sort: localeCompare },
  { title: "Betriebssystem", key: "os", sort: localeCompare },
  { title: "Anwendungsservice", key: "appserviceNames", sortable: false },
  { title: "CPUs", key: "numCpu", sort: numericCompare },
  { title: "RAM", key: "memoryMb", sort: numericCompare },
  {
    title: "Disks",
    key: "vdisksCapacityInBytes",
    sort: numericCompare,
  },
];

const servers = computed(() => props.selectedAppservice?.servers || []);

const selectedServers = ref<number[]>([]);
const fullServerCache = ref<Map<number, Server | null>>(new Map());
const snapshotCountCache = ref<Map<number, number | null>>(new Map());

const loadFullServer = async (id: number) => {
  const numId = Number(id);
  if (fullServerCache.value.has(numId)) return;
  const loading = ref(false);
  try {
    const server = await serverService.getServerById(loading, numId);
    fullServerCache.value.set(numId, server as Server);
    loadSnapshotCount(numId);
  } catch (e) {
    fullServerCache.value.set(numId, null);
    loadSnapshotCount(numId);
  }
};

const preloadFullServers = (ids: number[]) => {
  ids.forEach((id) => {
    loadFullServer(id);
    loadSnapshotCount(id);
  });
};

const loadSnapshotCount = async (id: number) => {
  const numId = Number(id);
  if (snapshotCountCache.value.has(numId)) return;
  const loading = ref(false);
  try {
    const snaps = await snapshotService.getSnapshotsByServerId(loading, numId);
    snapshotCountCache.value.set(numId, snaps?.length ?? 0);
  } catch (e) {
    snapshotCountCache.value.set(numId, null);
  }
};

const serversForBatch = computed(() => {
  return selectedServers.value
    .map((id) => fullServerCache.value.get(Number(id)))
    .filter((s): s is Server => !!s);
});

watch(
  () => props.selectedAppservice?.id,
  () => {
    selectedServers.value = [];
    fullServerCache.value = new Map();
  }
);

watch(selectedServers, (newIds, oldIds) => {
  const addedIds = newIds.filter((id) => !oldIds?.includes(id));
  if (addedIds.length) preloadFullServers(addedIds);
});

const onBatchOrderCompleteDone = () => {
  selectedServers.value = [];
};

const allSelectedDataLoaded = () =>
  selectedServers.value.length > 0 &&
  Array.from(fullServerCache.value.keys()).length >=
    selectedServers.value.length;

const allSelectedPass = (check: (s: any, id: number) => boolean) => {
  if (selectedServers.value.length === 0) return false;
  if (!allSelectedDataLoaded()) return false;
  return selectedServers.value.every((id) => {
    const s =
      fullServerCache.value.get(Number(id)) ||
      (props.selectedAppservice?.servers || []).find(
        (ss: any) => Number(ss.id) === Number(id)
      );
    if (!s) return false;
    return check(s, Number(id));
  });
};

const allSelectedServersEligibleForSnapshot = computed(() =>
  allSelectedPass((s: any, id: number) => {
    if (String((s as any).serverKind).toUpperCase() !== "VIRTUAL") return false;
    if (!(s as any).canEdit) return false;
    const cached = snapshotCountCache.value.get(Number(id));
    if (cached == null) return false;
    return cached === 0;
  })
);

const allSelectedServersEligibleForRoot = computed(() =>
  allSelectedPass((s: any) => {
    if ((s as any).roleLinux) {
      return (s.canEdit || (isOperator.value && !s.locked)) && s.managed;
    }
    if ((s as any).roleWindows) return s.canEdit && s.managed;
    return false;
  })
);

const allSelectedServersEligibleToStart = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      ((s as any).cloud?.cloudType === "VMWARE" ||
        (s as any).cloud?.cloudType === "PROXMOX") &&
      (s as any).powerState === "poweredOff"
  )
);

const allSelectedServersEligibleToStop = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      ((s as any).cloud?.cloudType === "VMWARE" ||
        (s as any).cloud?.cloudType === "PROXMOX") &&
      (s as any).powerState === "poweredOn"
  )
);

const allSelectedServersEligibleToPause = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      ((s as any).cloud?.cloudType === "VMWARE" ||
        (s as any).cloud?.cloudType === "PROXMOX") &&
      (s as any).powerState === "poweredOn"
  )
);

// Nur noch "poweredOn" erlaubt:
const allSelectedServersEligibleToRestart = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      ((s as any).cloud?.cloudType === "VMWARE" ||
        (s as any).cloud?.cloudType === "PROXMOX") &&
      (s as any).powerState === "poweredOn"
  )
);

const allSelectedServersEligibleForDowntime = computed(() =>
  allSelectedPass((s: any) => !!s.canEdit)
);

const allSelectedServersEligibleForWindowsMaintenance = computed(() =>
  allSelectedPass(
    (s: any) => !!(s as any).roleWindows && s.canEdit && s.managed
  )
);

const noSelectionTooltip = "Keine Server ausgewählt.";

const snapshotDisabledTooltip = computed(() => {
  if (allSelectedServersEligibleForSnapshot.value) return "";
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (
    Array.from(fullServerCache.value.keys()).length <
    selectedServers.value.length
  ) {
    return "Serverdaten werden geladen. Bitte warten.";
  }
  return "Nur auf virtuellen Servern mit Bestellberechtigung und ohne bestehenden Snapshot können Snapshots erstellt werden.";
});

const downtimeDisabledTooltip = computed(() => {
  if (allSelectedServersEligibleForDowntime.value) return "";
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (
    Array.from(fullServerCache.value.keys()).length <
    selectedServers.value.length
  ) {
    return "Serverdaten werden geladen. Bitte warten.";
  }
  return "Nur auf Servern mit Bestellberechtigung kann eine Downtime gesetzt werden.";
});

const windowsMaintenanceDisabledTooltip = computed(() => {
  if (allSelectedServersEligibleForWindowsMaintenance.value) return "";
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (
    Array.from(fullServerCache.value.keys()).length <
    selectedServers.value.length
  ) {
    return "Serverdaten werden geladen. Bitte warten.";
  }
  return "Nur auf verwalteten Windows-Servern mit Bestellberechtigung kann der Wartungsmodus gesetzt werden.";
});

const powerStartTooltip = computed(() => {
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (!allSelectedDataLoaded()) return "Wird geladen...";
  return allSelectedServersEligibleToStart.value ? "Start" : "Nicht möglich";
});

const powerStopTooltip = computed(() => {
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (!allSelectedDataLoaded()) return "Wird geladen...";
  return allSelectedServersEligibleToStop.value ? "Stop" : "Nicht möglich";
});

const powerPauseTooltip = computed(() => {
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (!allSelectedDataLoaded()) return "Wird geladen...";
  return allSelectedServersEligibleToPause.value
    ? "Pausieren / Geplante Downtime"
    : "Nicht möglich";
});

const anyServerHasWarnings = computed(() => {
  return (props.selectedAppservice?.servers || []).some(
    (server: any) => server.hasWarnings
  );
});

const powerRestartTooltip = computed(() => {
  if (selectedServers.value.length === 0) return noSelectionTooltip;
  if (!allSelectedDataLoaded()) return "Wird geladen...";
  return allSelectedServersEligibleToRestart.value
    ? "Restart"
    : "Nicht möglich";
});

const rootAdminTooltip = computed(() => {
  if (selectedServers.value.length === 0) return noSelectionTooltip;

  if (
    Array.from(fullServerCache.value.keys()).length <
    selectedServers.value.length
  ) {
    return "Serverdaten werden geladen. Bitte warten.";
  }

  if (allSelectedServersEligibleForRoot.value) {
    return `Root/Admin-Rechte für ${selectedServers.value.length} Server bestellen`;
  }

  const notManaged: string[] = [];
  const noPermission: string[] = [];
  const unknownOs: string[] = [];

  selectedServers.value.forEach((id) => {
    const s = fullServerCache.value.get(Number(id));
    const name = (s && (s as any).name) || `Server ${id}`;
    if (!s) return;
    if (!(s as any).managed) {
      notManaged.push(name);
      return;
    }
    if ((s as any).roleLinux) {
      if (!((s as any).canEdit || (isOperator.value && !(s as any).locked))) {
        noPermission.push(name);
      }
      return;
    }
    if ((s as any).roleWindows) {
      if (!(s as any).canEdit) {
        noPermission.push(name);
      }
      return;
    }
    unknownOs.push(name);
  });

  if (notManaged.length)
    return `Deaktiviert: ${notManaged.length} ausgewählte Server sind nicht verwaltet.`;
  if (noPermission.length)
    return `Deaktiviert: ${noPermission.length} ausgewählte Server haben keine Bestellberechtigung.`;
  if (unknownOs.length)
    return `Deaktiviert: ${unknownOs.length} ausgewählte Server haben unbekanntes Betriebssystem.`;

  return "Aktion deaktiviert.";
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}

.server-table {
  table-layout: fixed;
  width: 100%;
}

.server-table th,
.server-table td {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-table th:first-child,
.server-table td:first-child {
  min-width: 64px;
  max-width: 64px;
  width: 64px;
  padding-left: 6px;
  padding-right: 6px;
  box-sizing: border-box;
  white-space: normal;
  text-align: center;
}

:deep(.server-table td:first-child .v-input--selection-controls),
:deep(.server-table th:first-child .v-input--selection-controls) {
  margin: 0 !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.power-state-icon-inline {
  display: flex;
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

:deep(.os-icon-inline img),
:deep(.os-icon-inline .os-icon) {
  width: 24px !important;
  height: 24px !important;
}

.action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
}

.app-services-cell {
  max-width: 200px;
}
</style>
