<template>
  <div class="pt-4 links">
    <v-col class="pl-0">
      <v-card
        outlined
        border
        elevation="2"
        class="mb-4"
        rounded="lg"
        color="backgroundLight"
      >
        <v-card-title>Informationen</v-card-title>
        <v-divider></v-divider>
        <v-card-text>
          <v-row>
            <v-col cols="3">
              <h3>Name</h3>
            </v-col>
            <v-col cols="3">
              <h3>Nummer</h3>
            </v-col>
            <v-col cols="3">
              <h3>Eigentum von</h3>
            </v-col>
            <v-col cols="3">
              <h3>Delegierter</h3>
            </v-col>
          </v-row>
          <v-row>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(props.selectedAppservice?.name)
                }}
              </p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(props.selectedAppservice?.number)
                }}
              </p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(
                    props.selectedAppservice!.ownedByName
                  )
                }}
              </p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(
                    props.selectedAppservice!.serviceOwnerDelegateName
                  )
                }}
              </p>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="6">
              <h3>CMDB</h3>
            </v-col>
            <v-col cols="6">
              <h3>Change-Gruppe</h3>
            </v-col>
          </v-row>
          <v-row>
            <v-col
              cols="6"
              class="pt-0"
            >
              <p>
                <a
                  v-if="props.selectedAppservice?.sysId"
                  :href="`https://it-services.muenchen.de/nav_to.do?uri=cmdb_ci_service_discovered.do?sys_id=${props.selectedAppservice.sysId}%26sysparm_view=EAM`"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ props.selectedAppservice?.name }}
                </a>
                <span v-else>-</span>
              </p>
            </v-col>
            <v-col
              cols="6"
              class="pt-0"
            >
              <p>
                <a
                  v-if="props.selectedAppservice?.sysId"
                  :href="`https://it-services.muenchen.de/now/sgw/record/sys_user_group/${props.selectedAppservice.changeGroupSysId}/`"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ props.selectedAppservice?.changeGroupName }}
                </a>
                <span v-else>-</span>
              </p>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="3">
              <h3>Mikrosegmentiert</h3>
            </v-col>
          </v-row>
          <v-row>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.formatBooleanToGerman(
                    props.selectedAppservice?.cswEnforced
                  )
                }}
              </p>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>
    </v-col>
    <v-col
      class="pl-0"
      v-if="props.selectedAppservice?.servers?.length"
    >
      <v-card
        outlined
        border
        elevation="2"
        rounded="lg"
        color="backgroundLight"
      >
        <v-card-title class="d-flex justify-space-between align-center">
          <span>Zugeordnete Server</span>
          <div
            v-if="selectedServers.length > 0"
            class="action-buttons"
          >
            <AddSnapshot
              :isBatchOperation="true"
              :selectedServerIds="selectedServers"
              :selectedServers="serversForBatch"
              :parentAllSelectedServersEligible="allSelectedServersEligibleForSnapshot"
              :parentDisabledTooltip="!allSelectedServersEligibleForSnapshot ? (Array.from(fullServerCache.keys()).length < selectedServers.length ? 'Serverdaten werden geladen. Bitte warten.' : 'Nur auf virtuellen Servern mit Bestellberechtigung und ohne bestehenden Snapshot können Snapshots erstellt werden.') : ''"
              @save="onBatchOrderCompleteDone"
            />
            <ActionButton
              color="btn_green"
              :icon="mdiPlay"
              :tooltip="powerStartTooltip"
              :isBatchOperation="true"
              :selectedServerIds="selectedServers"
              :selectedServers="serversForBatch"
              :parentAllSelectedServersEligible="allSelectedServersEligibleToStart"
              jobToCall="VMWARE_START_SERVER"
              showConfirmDialog
              confirmDialogTitle="VM Starten"
              confirmDialogText="Wollen Sie diese VMs wirklich starten?"
              @change="onBatchOrderCompleteDone"
            />
            <ActionButton
              color="btn_red"
              :icon="mdiStop"
              :tooltip="powerStopTooltip"
              :isBatchOperation="true"
              :selectedServerIds="selectedServers"
              :selectedServers="serversForBatch"
              :parentAllSelectedServersEligible="allSelectedServersEligibleToStop"
              jobToCall="VMWARE_STOP_SERVER"
              showConfirmDialog
              confirmDialogTitle="VM Stoppen"
              confirmDialogText="Wollen Sie diese VMs wirklich stoppen?"
              useExtraSureDialog
              extraSureCheckboxText="Mir ist bewusst, dass durch das Stoppen der VM eine Serviceunterbrechung entsteht."
              @change="onBatchOrderCompleteDone"
            />
            <ActionButton
              color="btn_red"
              :icon="mdiRestart"
              :tooltip="powerRestartTooltip"
              :isBatchOperation="true"
              :selectedServerIds="selectedServers"
              :selectedServers="serversForBatch"
              :parentAllSelectedServersEligible="allSelectedServersEligibleToRestart"
              jobToCall="VMWARE_RESTART_SERVER"
              showConfirmDialog
              confirmDialogTitle="VM Neustarten"
              confirmDialogText="Wollen Sie diese VMs wirklich neustarten?"
              useExtraSureDialog
              extraSureCheckboxText="Mir ist bewusst, dass durch das Neustarten der VM eine Serviceunterbrechung entsteht."
              @change="onBatchOrderCompleteDone"
            />
            <RootAdminRechteBtn
              :icon="mdiKeyChain"
              :isBatchOperation="true"
              :selectedServerIds="selectedServers"
              :selectedServers="serversForBatch"
              :disabled="!allSelectedServersEligibleForRoot"
              :tooltip="rootAdminTooltip"
              :showConfirmDialog="true"
              :confirmDialogTitle="`Root/Admin-Rechte für ${selectedServers.length} Server bestellen`"
              :confirmDialogText="`Sind Sie sicher, dass Sie Root/Admin-Rechte für die ${selectedServers.length} ausgewählten Server bestellen möchten?`"
              @change="onBatchOrderCompleteDone"
            />
          </div>
        </v-card-title>
        <v-divider></v-divider>
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
                  @update:model-value="toggleAllServers"
                  hide-details
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
                  @update:model-value=
                  "(value) => toggleServerSelection(server.id, value)"
                  hide-details
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
                <span class="font-weight-bold">
                  <router-link :to="`/server/${server.id}`">
                    {{ server.name }}
                  </router-link>
                </span>
              </td>
              <td>
                <div class="d-flex align-center">
                  <div class="power-state-icon-inline mr-2">
                    <v-icon
                      :color =
                        "server.powerState === 'poweredOn'
                          ? 'btn_green'
                          : server.powerState === 'poweredOff'
                            ? 'btn_red'
                            : 'accent'"
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
                    <OsCell
                      :osFullName="server.os || ''"
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
      </v-card>
    </v-col>
  </div>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";

import {
  mdiCloud,
  mdiKeyChain,
  mdiPauseCircle,
  mdiPlayCircle,
  mdiServer,
  mdiStopCircle,
  mdiPlay,
  mdiRestart,
  mdiStop,
} from "@mdi/js";
import { computed, ref, watch } from "vue";

import RootAdminRechteBtn from "@/components/Server/ActionButtons/RootAdminRechteBtn.vue";
import OsCell from "@/components/Server/OsCell.vue";
import AddSnapshot from "@/components/Server/AddSnapshot.vue";
import ActionButton from "@/components/Server/ActionButtons/ActionButton.vue";
import snapshotService from "@/api/snapshotService";
import type Server from "@/types/Server.ts";
import { useFormatter } from "@/composables/formatter.ts";
import { useRules } from "@/composables/rules.ts";
import { useUserStore } from "@/stores/user.ts";
import serverService from "@/api/serverService";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();
const rules = useRules();
const userStore = useUserStore();
const isOperator = computed(() =>
  userStore.getUser?.authorities.includes("ROLE_OPERATOR")
);

// helper: map serverKind to tooltip text and icon (used by template)
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

// store numeric server IDs to match Server.id type
const selectedServers = ref<number[]>([]);

// cache for loaded full server details: Map<id, Server|null> (null = failed to load)
const fullServerCache = ref<Map<number, Server | null>>(new Map());
// cache for snapshot counts: Map<id, number|null> (null = failed to load)
const snapshotCountCache = ref<Map<number, number | null>>(new Map());

// helper to load full server details and cache them
const loadFullServer = async (id: number) => {
  const numId = Number(id);
  if (fullServerCache.value.has(numId)) return; // already loaded or attempted
  const loading = ref(false);
  try {
    const server = await serverService.getServerById(loading, numId);
    fullServerCache.value.set(numId, server as Server);
    // also load snapshot count for this server asynchronously
    loadSnapshotCount(numId);
  } catch (e) {
    // mark as null so we don't retry immediately; could implement retry logic
    fullServerCache.value.set(numId, null);
    // still attempt to load snapshot count
    loadSnapshotCount(numId);
  }
};

// preload full servers for an array of ids (used for select-all)
const preloadFullServers = (ids: number[]) => {
  ids.forEach((id) => {
    loadFullServer(id);
    loadSnapshotCount(id);
  });
};

// load snapshot count for server and cache result
const loadSnapshotCount = async (id: number) => {
  const numId = Number(id);
  if (snapshotCountCache.value.has(numId)) return; // already loaded or attempted
  const loading = ref(false);
  try {
    const snaps = await snapshotService.getSnapshotsByServerId(loading, numId);
    snapshotCountCache.value.set(numId, snaps?.length ?? 0);
  } catch (e) {
    // mark as null to indicate failure
    snapshotCountCache.value.set(numId, null);
  }
};

// serversForBatch: return selected servers using partial data (from selectedAppservice.servers)
// overlaid with full server objects from fullServerCache when available.
const serversForBatch = computed(() => {
  return selectedServers.value
    .map((id) => {
      // try full cache first
      const full = fullServerCache.value.get(Number(id));
      if (full) return full;
      // fallback: lookup in parent-provided partial server list
      const partial = (props.selectedAppservice?.servers || []).find((s: any) => Number(s.id) === Number(id));
      return partial || null;
    })
    .filter((s): s is Server => !!s);
});

// reset selection when the appservice changes to avoid stale selection/dash state
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
    const allIds = (props.selectedAppservice?.servers || []).map(
      (s: any) => Number(s.id)
    );
    selectedServers.value = allIds;
    // preload full servers for eligibility checks
    preloadFullServers(allIds);
  } else {
    selectedServers.value = [];
  }
};

// value indicates checkbox state; serverId is numeric or string - coerce to Number
const toggleServerSelection = (serverId: any, value: boolean | null) => {
  const id = Number(serverId);
  const shouldSelect = !!value;
  const index = selectedServers.value.indexOf(id);
  if (shouldSelect) {
    if (index === -1) selectedServers.value.push(id);
    // load full server details when selected so eligibility can be evaluated
    loadFullServer(id);
  } else {
    if (index > -1) selectedServers.value.splice(index, 1);
  }
};

const onBatchOrderCompleteDone = () => {
  // callback wenn RootAdminRechteBtn die batch-Operation abgeschlossen hat
  selectedServers.value = [];
};

// helper: ensure all selected servers loaded
const allSelectedDataLoaded = () =>
  selectedServers.value.length > 0 &&
  Array.from(fullServerCache.value.keys()).length >= selectedServers.value.length;

// generic all-selected checker
const allSelectedPass = (check: (s: any, id: number) => boolean) => {
  if (selectedServers.value.length === 0) return false;
  if (!allSelectedDataLoaded()) return false;
  return selectedServers.value.every((id) => {
    const s = fullServerCache.value.get(Number(id)) ||
      (props.selectedAppservice?.servers || []).find((ss: any) => Number(ss.id) === Number(id));
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
  allSelectedPass((s: any) =>
    (s as any).canEdit && (s as any).cloud?.cloudType === 'VCENTER' && (s as any).powerState === 'poweredOff'
  )
);

const allSelectedServersEligibleToStop = computed(() =>
  allSelectedPass((s: any) =>
    (s as any).canEdit && (s as any).cloud?.cloudType === 'VCENTER' && (s as any).powerState === 'poweredOn'
  )
);

const allSelectedServersEligibleToRestart = computed(() =>
  allSelectedPass((s: any) => {
    const ps = (s as any).powerState;
    return (s as any).canEdit && (s as any).cloud?.cloudType === 'VCENTER' && (ps === 'poweredOn' || ps === 'poweredOff');
  })
);

const powerStartTooltip = computed(() => {
  if (!allSelectedDataLoaded()) return 'Wird geladen...';
  return allSelectedServersEligibleToStart.value ? 'Start' : 'Nicht möglich';
});

const powerStopTooltip = computed(() => {
  if (!allSelectedDataLoaded()) return 'Wird geladen...';
  return allSelectedServersEligibleToStop.value ? 'Stop' : 'Nicht möglich';
});

const powerRestartTooltip = computed(() => {
  if (!allSelectedDataLoaded()) return 'Wird geladen...';
  return allSelectedServersEligibleToRestart.value ? 'Restart' : 'Nicht möglich';
});

// tooltip text for RootAdminRechteBtn to explain disabled reasons (similar style as AddSnapshot)
const rootAdminTooltip = computed(() => {
  if (selectedServers.value.length === 0) return 'Keine Server ausgewählt.';

  // if not all full servers are loaded yet -> inform user
  if (Array.from(fullServerCache.value.keys()).length < selectedServers.value.length) {
    return 'Serverdaten werden geladen. Bitte warten.';
  }

  if (allSelectedServersEligibleForRoot.value) {
    return `Root/Admin-Rechte für ${selectedServers.value.length} Server bestellen`;
  }

  // compute specific failure reasons
  const notManaged: string[] = [];
  const noPermission: string[] = [];
  const unknownOs: string[] = [];

  selectedServers.value.forEach((id) => {
    const s = fullServerCache.value.get(Number(id));
    const name = (s && (s as any).name) || `Server ${id}`;
    if (!s) return; // already handled by loading message above
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

  if (notManaged.length) return `Deaktiviert: ${notManaged.length} ausgewählte Server sind nicht verwaltet.`;
  if (noPermission.length) return `Deaktiviert: ${noPermission.length} ausgewählte Server haben keine Bestellberechtigung.`;
  if (unknownOs.length) return `Deaktiviert: ${unknownOs.length} ausgewählte Server haben unbekanntes Betriebssystem.`;

  return 'Aktion deaktiviert.';
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

:deep(.v-card-title) {
  padding-right: 16px;
}

.app-services-cell {
  max-width: 200px;
}
</style>
