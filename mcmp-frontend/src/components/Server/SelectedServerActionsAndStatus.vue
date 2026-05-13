<template>
  <v-container
    v-if="selectedServer"
    fluid
  >
    <v-row>
      <!-- LINKS: Icon + Servername -->
      <v-col
        cols="auto"
        class="d-flex align-center"
      >
        <div class="status-circle">
          <v-icon
            size="30"
            :color="
              props.selectedServer.powerState === 'poweredOn'
                ? 'btn_green'
                : props.selectedServer.powerState === 'poweredOff'
                  ? 'btn_red'
                  : 'accent'
            "
            :aria-label="
              props.selectedServer.powerState === 'poweredOn'
                ? 'Eingeschaltet'
                : props.selectedServer.powerState === 'poweredOff'
                  ? 'Ausgeschaltet'
                  : 'Suspended'
            "
          >
            {{
              props.selectedServer.powerState === "poweredOn"
                ? mdiPlayCircle
                : props.selectedServer.powerState === "poweredOff"
                  ? mdiStopCircle
                  : mdiPauseCircle
            }}
          </v-icon>
        </div>

        <h2 class="ml-2 text-truncate">
          {{ selectedServer == null ? "" : selectedServer.name }}
        </h2>
      </v-col>

      <!-- RECHTS: Buttons -->
      <v-col
        v-if="hasActions"
        class="d-flex justify-end"
      >
        <div class="action-button-group">
          <div
            v-if="
              selectedServer.canEdit &&
              selectedServer.cloud?.cloudType == 'VCENTER'
            "
          >
            <ActionButton
              color="btn_green"
              :icon="mdiPlay"
              :disabled="isRunning"
              tooltip="Server starten"
              :server="selectedServer"
              jobToCall="VMWARE_START_SERVER"
              showConfirmDialog
              confirmDialogTitle="VM Starten"
              confirmDialogText="Wollen Sie diese VM wirklich starten?"
              @change="change"
            />
            <ActionButton
              color="btn_red"
              :icon="mdiStop"
              :disabled="!isRunning"
              tooltip="Server stoppen"
              :server="selectedServer"
              jobToCall="VMWARE_STOP_SERVER"
              showConfirmDialog
              confirmDialogTitle="VM Stoppen"
              confirmDialogText="Wollen Sie diese VM wirklich stoppen?"
              useExtraSureDialog
              extraSureCheckboxText="Mir ist bewusst, dass durch das Stoppen der VM eine Serviceunterbrechung entsteht."
              @change="change"
            />
            <ActionButton
              color="btn_red"
              :icon="mdiRestart"
              tooltip="Server neustarten"
              :server="selectedServer"
              jobToCall="VMWARE_RESTART_SERVER"
              showConfirmDialog
              confirmDialogTitle="VM Neustarten"
              confirmDialogText="Wollen Sie diese VM wirklich neustarten?"
              useExtraSureDialog
              extraSureCheckboxText="Mir ist bewusst, dass durch das Neustarten der VM eine Serviceunterbrechung entsteht."
              @change="change"
            />
          </div>

          <WinWartungsModusMenu
            :server="selectedServer"
            v-if="selectedServer.canEdit && isWindows && selectedServer.managed"
          />

          <RootAdminRechteBtn
            :color="
              selectedServer.hasTempAdminPrivileges &&
              selectedServer.tempPrivilegesExpiresAt
                ? 'btn_green'
                : 'btn_red'
            "
            :icon="mdiKeyChain"
            :tooltip="Admin72hTooltipText"
            :server="selectedServer"
            jobToCall="WINDOWS_TEMP_ADMIN"
            showConfirmDialog
            confirmDialogTitle="72h Adminrechte beantragen"
            confirmDialogText="Wollen Sie für 72 Stunden Administratorrechte für diese Windows VM beantragen?"
            v-if="selectedServer.canEdit && isWindows && selectedServer.managed"
            @change="change"
          />

          <RootAdminRechteBtn
            :color="
              selectedServer.hasTempRootPrivileges &&
              selectedServer.tempPrivilegesExpiresAt
                ? 'btn_green'
                : 'btn_red'
            "
            :icon="mdiKeyChain"
            :tooltip="Root72hTooltipText"
            :server="selectedServer"
            jobToCall="LINUX_TEMP_ROOT"
            showConfirmDialog
            confirmDialogTitle="72h Rootrechte beantragen"
            confirmDialogText="Wollen Sie für 72 Stunden Root-Rechte für diese Linux VM beantragen?"
            confirm-dialog-link="https://go.muenchen.de/sp/KB0019842"
            confirm-dialog-link-text="Lesen Sie bitte die Hinweise zur Benutzung der Root-Rechte."
            v-if="
              (selectedServer.canEdit ||
                (canExecuteOperatorActions && !selectedServer.locked)) &&
              isLinux &&
              selectedServer.managed
            "
            @change="change"
          />

          <CheckmkMenu
            :server="selectedServer"
            v-if="selectedServer.canEdit"
          />

          <ActionButton
            color="btn_red"
            :icon="mdiDelete"
            tooltip="Server abbauen"
            :server="selectedServer"
            jobToCall="LINUX_DELETE_SERVER"
            showConfirmDialog
            confirmDialogTitle="Server abbauen"
            confirmDialogText="Wollen Sie diesen Server wirklich abbauen?"
            useExtraSureDialog
            extraSureCheckboxText="Ich bin mir sicher, dass ich diesen Server abbauen möchte."
            @change="change"
            v-if="
              selectedServer.canEdit &&
              isLinux &&
              selectedServer.managed &&
              selectedServer.cloud?.cloudType == 'VCENTER'
            "
          />

          <ActionButton
            color="btn_red"
            :icon="mdiDelete"
            tooltip="Server abbauen"
            :server="selectedServer"
            jobToCall="WINDOWS_DELETE_SERVER"
            showConfirmDialog
            confirmDialogTitle="Server abbauen"
            confirmDialogText="Wollen Sie diesen Server wirklich löschen?"
            useExtraSureDialog
            extraSureCheckboxText="Ich bin mir sicher, dass ich diesen Server abbauen möchte."
            @change="change"
            v-if="
              selectedServer.canEdit &&
              isWindows &&
              selectedServer.managed &&
              selectedServer.cloud?.cloudType == 'VCENTER'
            "
          />
        </div>
      </v-col>
    </v-row>
    <v-row v-if="selectedServer.runningJobsCount > 0">
      <v-col
        cols="12"
        class="pt-0 pb-0"
      >
        <div
          class="job-banner"
          @click="$emit('navigateToHistory')"
          aria-label="Zur History"
        >
          <strong>{{ jobBannerText }}</strong>
        </div>
      </v-col>
    </v-row>
    <v-row
      v-if="
        selectedServer.runningJobsCount > 0 &&
        selectedServer.runningGreenItCount > 0
      "
    >
      <v-col
        cols="12"
        class="pt-0 pb-0"
        ><div></div
      ></v-col>
    </v-row>
    <v-row v-if="selectedServer.runningGreenItCount > 0">
      <v-col
        cols="12"
        class="pt-0 pb-0"
      >
        <div
          class="green-it-banner"
          :class="{ 'dark-mode': isDark }"
          @click="$emit('navigateToHistory')"
          aria-label="Zur History"
        >
          <p>{{ greenItBannerIntroText }}</p>
          <ul>
            <li
              v-for="job in selectedServer.activeGreenItJobs"
              :key="job.changeNumber"
            >
              {{ toDateAndTimeString(job.changeStartDate) }} Uhr :
              <a
                :href="job.changeLink"
                target="_blank"
                rel="noopener noreferrer"
              >
                {{ job.changeNumber }}
              </a>
              "{{ job.actionTitle }}"
            </li>
          </ul>
        </div>
      </v-col>
    </v-row>
    <v-row>
      <v-col class="ml-4 pa-0">
        <v-chip-group
          :show-arrows="true"
          tabindex="-1"
        >
          <v-skeleton-loader
            type="chip"
            v-if="loadingServerDetails"
            class="pa-0 ma-0"
          />
          <StatusChip
            v-if="!selectedServer.canEdit && !loadingServerDetails"
            :value="selectedServer.canEdit"
            :checkValue="false"
            matchText="Nur Lesezugriff"
            notMatchText=""
            matchMode="equal"
          />
          <StatusChip
            v-if="
              !selectedServer.canEdit &&
              !loadingServerDetails &&
              selectedServer.numberOfAssignedAppservices === 0
            "
            :value="selectedServer.numberOfAssignedAppservices"
            :checkValue="1"
            matchText=""
            notMatchText="Bearbeitung ist gesperrt."
            matchMode="greaterEquals"
            tooltip="Server ist keinem Anwendungsservice zugewiesen"
            href="https://go.muenchen.de/sp/KB0023236"
          />
          <StatusChip
            v-if="
              !selectedServer.canEdit &&
              !loadingServerDetails &&
              selectedServer.numberOfAssignedAppservices > 1
            "
            :value="selectedServer.numberOfAssignedAppservices"
            :checkValue="1"
            matchText=""
            notMatchText="Bearbeitung ist gesperrt."
            matchMode="lessEquals"
            tooltip="Server ist mehreren Anwendungsservices zugewiesen"
            href="https://go.muenchen.de/sp/KB0023236"
          />
          <StatusChip
            v-if="
              !loadingServerDetails &&
              selectedServer.hasTempAdminPrivileges &&
              selectedServer.tempPrivilegesExpiresAt
            "
            :value="false"
            :checkValue="true"
            matchText=""
            :notMatchText="tempAdminText"
            matchMode="equal"
          />
          <StatusChip
            v-if="
              !loadingServerDetails &&
              selectedServer.hasTempRootPrivileges &&
              selectedServer.tempPrivilegesExpiresAt
            "
            :value="false"
            :checkValue="true"
            matchText=""
            :notMatchText="tempRootText"
            matchMode="equal"
          />
          <StatusChip
            v-if="
              !loadingServerDetails &&
              selectedServer.maintenanceMode &&
              selectedServer.maintenanceModeExpiresAt
            "
            :value="false"
            :checkValue="true"
            matchText=""
            :notMatchText="maintenanceText"
            matchMode="equal"
          />
          <StatusChip
            v-if="
              !loadingServerDetails &&
              Number(selectedServer.patchnightExitcode) != 0
            "
            :value="Number(selectedServer.patchnightExitcode) === 0"
            :checkValue="true"
            matchText=""
            notMatchText="Fehler bei Patchnight"
            matchMode="equal"
            @click="$emit('navigateToPatchnight')"
          />
          <StatusChip
            v-if="
              !loadingServerDetails &&
              selectedServer.canEdit &&
              !selectedServer.managed
            "
            :value="!selectedServer.managed"
            :checkValue="false"
            matchText=""
            notMatchText="Nicht verwaltet"
            matchMode="equal"
            tooltip="Bearbeitung nur eingeschränkt möglich."
            href="https://mcmp.muenchen.de/#/help/9"
          />
        </v-chip-group>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import type Server from "@/types/Server";

import {
  mdiDelete,
  mdiKeyChain,
  mdiPauseCircle,
  mdiPlay,
  mdiPlayCircle,
  mdiRestart,
  mdiStop,
  mdiStopCircle,
} from "@mdi/js";
import { computed } from "vue";
import { useTheme } from "vuetify";

import StatusChip from "@/components/common/StatusChip.vue";
import ActionButton from "@/components/Server/ActionButtons/ActionButton.vue";
import CheckmkMenu from "@/components/Server/ActionButtons/CheckmkMenu.vue";
import RootAdminRechteBtn from "@/components/Server/ActionButtons/RootAdminRechteBtn.vue";
import WinWartungsModusMenu from "@/components/Server/ActionButtons/WinWartungsModusMenu.vue";
import { useAppStore } from "@/stores/app";
import { useUserStore } from "@/stores/user.ts";
import { toDateAndTimeString } from "@/util/formatter";

const props = defineProps<{
  selectedServer: Server;
  loadingServerDetails: boolean;
}>();
const appStore = useAppStore();
const userStore = useUserStore();
const isOperator = computed(() =>
  userStore.getUser?.authorities.includes("ROLE_OPERATOR")
);

const canExecuteOperatorActions = computed(() => {
  return isOperator.value && !appStore.isReadOnly;
});

const emit = defineEmits<{
  (e: "change"): void;
  (e: "navigateToHistory"): void;
  (e: "navigateToPatchnight"): void;
}>();

const theme = useTheme();
const isDark = computed(() => theme.global.current.value.dark);

function change() {
  emit("change");
}

const isRunning = computed(() => {
  return props.selectedServer?.powerState === "poweredOn";
});

const Admin72hTooltipText = "Adminrechte für 72h beantragen";
const Root72hTooltipText = "Rootrechte für 72h beantragen";

const isWindows = computed(() => {
  return props.selectedServer?.roleWindows;
});

const isLinux = computed(() => {
  return props.selectedServer?.roleLinux;
});

const hasActions = computed(() => {
  const s = props.selectedServer;
  if (!s) return false;
  const canEdit = s.canEdit;
  const cloudType = s.cloud?.cloudType;
  const managed = s.managed;
  const locked = s.locked;
  const hasVMWare = canEdit && cloudType === "VCENTER";
  const hasWinManaged = canEdit && isWindows.value && managed;
  const hasLinuxManaged =
    (canEdit || (isOperator.value && !locked)) && isLinux.value && managed;
  const hasCheckmk = canEdit;
  const hasDelete =
    canEdit &&
    managed &&
    cloudType === "VCENTER" &&
    (isWindows.value || isLinux.value);
  return (
    hasVMWare || hasWinManaged || hasLinuxManaged || hasCheckmk || hasDelete
  );
});

const tempAdminText = computed(() => {
  const date = props.selectedServer?.tempPrivilegesExpiresAt;
  return date
    ? `temp. 72h Adminrechte bis ${toDateAndTimeString(date)} Uhr`
    : "";
});

const tempRootText = computed(() => {
  const date = props.selectedServer?.tempPrivilegesExpiresAt;
  return date
    ? `temp. 72h Rootrechte bis ${toDateAndTimeString(date)} Uhr`
    : "";
});

const maintenanceText = computed(() => {
  const date = props.selectedServer?.maintenanceModeExpiresAt;
  return date ? `Wartungsmodus aktiv bis ${toDateAndTimeString(date)} Uhr` : "";
});

const jobBannerText = computed(() => {
  const count = props.selectedServer?.runningJobsCount || 0;

  if (count === 1) {
    return "Es läuft gerade ein Job. Details sind in der History verfügbar.";
  } else if (count > 1) {
    return `Es laufen gerade ${count} Jobs. Details sind in der History verfügbar.`;
  }

  return "";
});

const greenItBannerIntroText = computed(() => {
  const count = props.selectedServer?.activeGreenItJobs.length || 0;

  return count === 1
    ? "Für den Server ist folgende Green-IT Maßnahme geplant:"
    : "Für den Server sind folgende Green-IT Maßnahmen geplant:";
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.action-button-group {
  display: flex;
  align-items: center;
  background: rgb(var(--v-theme-bg_light));
  border-radius: 28px;
  padding: 4px 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.12),
    0 1px 2px rgba(0, 0, 0, 0.24);
  transition: box-shadow 0.3s ease;
}

.action-button-group:hover {
  box-shadow:
    0 3px 6px rgba(0, 0, 0, 0.16),
    0 3px 6px rgba(0, 0, 0, 0.23);
}

.job-banner {
  cursor: pointer;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(244, 67, 54, 0.3);
  transition: all 0.2s ease;
  background-color: rgb(var(--v-theme-light_red));
  border: 1px solid rgb(var(--v-theme-_red));
  padding: 8px 16px;
  margin-left: 5px;
  min-width: 200px;
  display: flex;
  align-items: center;
}

.job-banner:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(244, 67, 54, 0.4);
  background-color: rgb(var(--v-theme-light_red));
}

.job-banner strong {
  font-weight: 600;
  font-size: 14px;
  color: black;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
}

.green-it-banner {
  cursor: pointer;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(67, 244, 54, 0.3);
  transition: all 0.2s ease;
  background-color: #e8f5e9;
  border: 1px solid rgb(var(--v-theme-light_green));
  padding: 8px 16px;
  margin-left: 5px;
  min-width: 200px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.green-it-banner:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(67, 244, 54, 0.4);
  background-color: #e8f5e9;
}

.green-it-banner.dark-mode {
  background-color: #1b5e20;
  border: 1px solid #2e7d32;
}

.green-it-banner.dark-mode:hover {
  background-color: #1b5e20;
}

.green-it-banner strong,
.green-it-banner span,
.green-it-banner p,
.green-it-banner li {
  font-weight: 600;
  font-size: 14px;
  color: black;
}

.green-it-banner.dark-mode strong,
.green-it-banner.dark-mode span,
.green-it-banner.dark-mode p,
.green-it-banner.dark-mode li {
  color: white;
}

.green-it-banner a,
.green-it-banner a:visited,
.green-it-banner a:hover,
.green-it-banner a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}

.green-it-banner ul {
  list-style: none;
  padding: 0;
  margin: 0;
  width: 100%;
}

.green-it-banner li {
  font-weight: 400;
  margin-bottom: 4px;
}

.green-it-banner li:last-child {
  margin-bottom: 0;
}

@media print {
  .action-button-group,
  .job-banner,
  :global(.v-chip-group) {
    display: none !important;
  }

  :global(.v-container) {
    padding-top: 20px !important; /* Schafft Platz zum oberen Seitenrand */
    margin-top: 0 !important;
  }

  .v-row {
    margin: 0 !important;
  }

  .v-col {
    padding-top: 10px !important; /* Verhindert das Abschneiden der Überschrift */
    padding-bottom: 10px !important;
  }

  h2 {
    margin-top: 0 !important;
    line-height: 1.2 !important;
  }
}

.status-circle {
  display: flex;
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 20px !important;
  height: 20px !important;
  border-radius: 50%;
  flex-shrink: 0;
}

.text-truncate {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
</style>
