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
            job-to-call="VMWARE_START_SERVER"
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
            job-to-call="VMWARE_STOP_SERVER"
            show-confirm-dialog
            confirm-dialog-title="VM Stoppen"
            confirm-dialog-text="Wollen Sie diese VMs wirklich stoppen?"
            use-extra-sure-dialog
            extra-sure-checkbox-text="Mir ist bewusst, dass durch das Stoppen der VM eine Serviceunterbrechung entsteht."
            @change="onBatchOrderCompleteDone"
          />
          <pause-server-btn
            :icon="mdiPause"
            :tooltip="powerPauseTooltip"
            :is-batch-operation="true"
            :selected-server-ids="selectedServers"
            :selected-servers="serversForBatch"
            :parent-all-selected-servers-eligible="
              allSelectedServersEligibleToPause
            "
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
            job-to-call="VMWARE_RESTART_SERVER"
            show-confirm-dialog
            confirm-dialog-title="VM Neustarten"
            confirm-dialog-text="Wollen Sie diese VMs wirklich neustarten?"
            use-extra-sure-dialog
            extra-sure-checkbox-text="Mir ist bewusst, dass durch das Neustarten der VM eine Serviceunterbrechung entsteht."
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
        </div>
      </template>
      <v-table
        density="comfortable"
        hover
        class="server-table"
      >
        <thead>
          <tr>
            <th
              class="text-center"
              style="width: 50px"
            >
              <v-checkbox
                :model-value="allServersSelected"
                :indeterminate="someServersSelected && !allServersSelected"
                hide-details
                @update:model-value="toggleAllServers"
              />
            </th>
            <th class="text-left">Typ</th>
            <th class="text-left">Servername</th>
            <th class="text-left">Status</th>
            <th class="text-left">Betriebssystem</th>
            <th class="text-left">Anwendungsservice</th>
            <th class="text-left">CPUs</th>
            <th class="text-left">RAM</th>
            <th class="text-left">Disks</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="server in props.selectedAppservice.servers"
            :key="server.id"
          >
            <td class="text-center">
              <v-checkbox
                :model-value="selectedServers.includes(Number(server.id))"
                hide-details
                @update:model-value="
                  (value) => toggleServerSelection(server.id, value)
                "
              />
            </td>
            <td>
              <v-tooltip
                v-if="serverKindText(server.serverKind)"
                :text="serverKindText(server.serverKind)"
              >
                <template #activator="{ props }">
                  <v-icon
                    v-bind="props"
                    size="small"
                    class="server-kind-icon"
                  >
                    {{ serverKindIcon(server.serverKind) }}
                  </v-icon>
                </template>
              </v-tooltip>
            </td>
            <td>
              <div class="d-flex align-center">
                <span class="font-weight-bold">
                  <router-link :to="`/server/${server.id}`">
                    {{ server.name }}
                  </router-link>
                </span>
                <v-tooltip
                  v-if="server.hasWarnings"
                  location="top"
                  text="Handlung erforderlich"
                >
                  <template #activator="{ props: tooltipProps }">
                    <router-link
                      :to="`/server/${server.id}`"
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
            </td>
            <td>
              <div class="d-flex align-center">
                <div class="power-state-icon-inline mr-2">
                  <v-icon
                    :color="
                      server.powerState === 'poweredOn'
                        ? 'btn_green'
                        : server.powerState === 'poweredOff'
                          ? 'btn_red'
                          : 'accent'
                    "
                    size="20"
                  >
                    {{
                      server.powerState === "poweredOn"
                        ? mdiPlayCircle
                        : server.powerState === "poweredOff"
                          ? mdiStopCircle
                          : mdiPauseCircle
                    }}
                  </v-icon>
                </div>
                <span>
                  {{
                    server.powerState === "poweredOn"
                      ? "Ein"
                      : server.powerState === "poweredOff"
                        ? "Aus"
                        : "Standby"
                  }}
                </span>
              </div>
            </td>
            <td>
              <div class="d-flex align-center">
                <os-cell
                  :os-full-name="server.os || ''"
                  size="small"
                  class="os-icon-inline mr-2"
                />
                <span class="text-caption">
                  {{ server.os }}
                </span>
              </div>
            </td>
            <td>
              <div class="app-services-cell">
                <template v-if="server.appserviceNames">
                  <ul class="pl-4">
                    <li
                      v-for="(service, index) in server.appserviceNames.split(
                        '|'
                      )"
                      :key="index"
                    >
                      {{ service.trim() }}
                    </li>
                  </ul>
                </template>
                <span v-else>-</span>
              </div>
            </td>
            <td>
              <span>
                {{ server.numCpu }}
              </span>
            </td>
            <td>
              <span>
                {{ formatter.formatMBtoGB(server.memoryMb ?? 0) }} GB
              </span>
            </td>
            <td>
              <span>
                {{ formatter.formatBtoGB(server.vdisksCapacityInBytes ?? 0) }}
                GB
              </span>
            </td>
          </tr>
        </tbody>
      </v-table>
    </common-card>
  </div>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";
import type Server from "@/types/Server.ts";

import {
  mdiAlert,
  mdiCloud,
  mdiKeyChain,
  mdiPause,
  mdiPauseCircle,
  mdiPlay,
  mdiPlayCircle,
  mdiRestart,
  mdiServer,
  mdiStop,
  mdiStopCircle,
} from "@mdi/js";
import { computed, ref, watch } from "vue";

import serverService from "@/api/serverService";
import snapshotService from "@/api/snapshotService";
import CommonCard from "@/components/common/CommonCard.vue";
import ActionButton from "@/components/Server/ActionButtons/ActionButton.vue";
import CheckMkDialog from "@/components/Server/ActionButtons/CheckMkDialog.vue";
import PauseServerBtn from "@/components/Server/ActionButtons/PauseServerBtn.vue";
import RootAdminRechteBtn from "@/components/Server/ActionButtons/RootAdminRechteBtn.vue";
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

const cardTitle = computed(
  () => `Zugeordnete Server (${props.selectedAppservice?.servers?.length ?? 0})`
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
    .map((id) => {
      const full = fullServerCache.value.get(Number(id));
      if (full) return full;
      const partial = (props.selectedAppservice?.servers || []).find(
        (s: any) => Number(s.id) === Number(id)
      );
      return partial || null;
    })
    .filter((s): s is Server => !!s);
});

watch(
  () => props.selectedAppservice?.id,
  () => {
    selectedServers.value = [];
    fullServerCache.value = new Map();
  }
);

const allServersSelected = computed(() => {
  if (!props.selectedAppservice?.servers?.length) return false;
  const totalCount = (props.selectedAppservice.servers || []).length;
  return totalCount > 0 && selectedServers.value.length === totalCount;
});

const someServersSelected = computed(() => {
  const totalCount = (props.selectedAppservice?.servers || []).length;
  return (
    selectedServers.value.length > 0 &&
    selectedServers.value.length < totalCount
  );
});

const toggleAllServers = (value: boolean | null) => {
  const selected = !!value;
  if (selected) {
    const allIds = (props.selectedAppservice?.servers || []).map((s: any) =>
      Number(s.id)
    );
    selectedServers.value = allIds;
    preloadFullServers(allIds);
  } else {
    selectedServers.value = [];
  }
};

const toggleServerSelection = (serverId: any, value: boolean | null) => {
  const id = Number(serverId);
  const shouldSelect = !!value;
  const index = selectedServers.value.indexOf(id);
  if (shouldSelect) {
    if (index === -1) selectedServers.value.push(id);
    loadFullServer(id);
  } else {
    if (index > -1) selectedServers.value.splice(index, 1);
  }
};

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
      (s as any).cloud?.cloudType === "VCENTER" &&
      (s as any).powerState === "poweredOff"
  )
);

const allSelectedServersEligibleToStop = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      (s as any).cloud?.cloudType === "VCENTER" &&
      (s as any).powerState === "poweredOn"
  )
);

const allSelectedServersEligibleToPause = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      (s as any).cloud?.cloudType === "VCENTER" &&
      (s as any).powerState === "poweredOn"
  )
);

// Nur noch "poweredOn" erlaubt:
const allSelectedServersEligibleToRestart = computed(() =>
  allSelectedPass(
    (s: any) =>
      (s as any).canEdit &&
      (s as any).cloud?.cloudType === "VCENTER" &&
      (s as any).powerState === "poweredOn"
  )
);

const allSelectedServersEligibleForDowntime = computed(() =>
  allSelectedPass((s: any) => !!s.canEdit)
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
