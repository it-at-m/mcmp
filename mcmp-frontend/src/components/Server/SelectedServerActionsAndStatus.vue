<template>
  <detail-page-header
    v-if="selectedServer"
    :appservice-id="selectedServer.appservices?.[0]?.id ?? null"
    :appservice-name="selectedServer.appservices?.[0]?.name ?? null"
    :appservice-count="selectedServer.appservices?.length ?? 0"
    :current-icon="powerStateIcon"
    :current-icon-color="powerStateIconColor"
    :current-label="
      selectedServer.name
        ? selectedServer.name.split('.')[0]
        : selectedServer.fqdn
    "
  >
    <template
      v-if="hasActions"
      #actions
    >
      <div
        v-if="
          selectedServer.canEdit &&
          (selectedServer.cloud?.cloudType == 'VMWARE' ||
            selectedServer.cloud?.cloudType == 'PROXMOX')
        "
      >
        <action-button
          color="btn_green"
          :icon="mdiPlay"
          :disabled="isRunning"
          tooltip="Server starten"
          :server="selectedServer"
          job-to-call="START_SERVER"
          show-confirm-dialog
          confirm-dialog-title="VM Starten"
          confirm-dialog-text="Wollen Sie diese VM wirklich starten?"
        />
        <action-button
          color="btn_red"
          :icon="mdiStop"
          :disabled="!isRunning"
          tooltip="Server stoppen"
          :server="selectedServer"
          job-to-call="STOP_SERVER"
          show-confirm-dialog
          confirm-dialog-title="VM Stoppen"
          confirm-dialog-text="Wollen Sie diese VM wirklich stoppen?"
          use-extra-sure-dialog
          extra-sure-checkbox-text="Mir ist bewusst, dass durch das Stoppen der VM eine Serviceunterbrechung entsteht."
        />
        <pause-server-btn
          :server="selectedServer"
          :disabled="!isRunning"
          :icon="mdiPause"
          @change="$emit('change')"
        />
        <action-button
          color="btn_red"
          :icon="mdiRestart"
          tooltip="Server neustarten"
          :server="selectedServer"
          :disabled="!isRunning"
          job-to-call="RESTART_SERVER"
          show-confirm-dialog
          confirm-dialog-title="VM Neustarten"
          confirm-dialog-text="Wollen Sie diese VM wirklich neustarten?"
          use-extra-sure-dialog
          extra-sure-checkbox-text="Mir ist bewusst, dass durch das Neustarten der VM eine Serviceunterbrechung entsteht."
        />
      </div>

      <win-wartungs-modus-menu
        v-if="selectedServer.canEdit && isWindows && selectedServer.managed"
        :server="selectedServer"
      />

      <root-admin-rechte-btn
        v-if="selectedServer.canEdit && isWindows && selectedServer.managed"
        :color="
          selectedServer.hasTempAdminPrivileges &&
          selectedServer.tempPrivilegesExpiresAt
            ? 'btn_green'
            : 'btn_red'
        "
        :icon="mdiKeyChain"
        :tooltip="Admin72hTooltipText"
        :server="selectedServer"
        job-to-call="WINDOWS_TEMP_ADMIN"
        show-confirm-dialog
        confirm-dialog-title="72h Adminrechte beantragen"
        confirm-dialog-text="Wollen Sie für 72 Stunden Administratorrechte für diese Windows VM beantragen?"
      />

      <root-admin-rechte-btn
        v-if="
          (selectedServer.canEdit ||
            (canExecuteOperatorActions && !selectedServer.locked)) &&
          isLinux &&
          selectedServer.managed
        "
        :color="
          selectedServer.hasTempRootPrivileges &&
          selectedServer.tempPrivilegesExpiresAt
            ? 'btn_green'
            : 'btn_red'
        "
        :icon="mdiKeyChain"
        :tooltip="Root72hTooltipText"
        :server="selectedServer"
        job-to-call="LINUX_TEMP_ROOT"
        show-confirm-dialog
        confirm-dialog-title="72h Rootrechte beantragen"
        confirm-dialog-text="Wollen Sie für 72 Stunden Root-Rechte für diese Linux VM beantragen?"
        confirm-dialog-link="https://go.muenchen.de/sp/KB0019842"
        confirm-dialog-link-text="Lesen Sie bitte die Hinweise zur Benutzung der Root-Rechte."
      />

      <checkmk-menu
        v-if="selectedServer.canEdit"
        :server="selectedServer"
      />

      <action-button
        v-if="
          selectedServer.canEdit &&
          isLinux &&
          selectedServer.managed &&
          (selectedServer.cloud?.cloudType == 'VMWARE' ||
            selectedServer.cloud?.cloudType == 'PROXMOX')
        "
        color="btn_red"
        :icon="mdiDelete"
        tooltip="Server abbauen"
        :server="selectedServer"
        job-to-call="LINUX_DELETE_SERVER"
        show-confirm-dialog
        confirm-dialog-title="Server abbauen"
        confirm-dialog-text="Wollen Sie diesen Server wirklich abbauen?"
        use-extra-sure-dialog
        extra-sure-checkbox-text="Ich bin mir sicher, dass ich diesen Server abbauen möchte."
      />

      <action-button
        v-if="
          selectedServer.canEdit &&
          isWindows &&
          selectedServer.managed &&
          (selectedServer.cloud?.cloudType == 'VMWARE' ||
            selectedServer.cloud?.cloudType == 'PROXMOX')
        "
        color="btn_red"
        :icon="mdiDelete"
        tooltip="Server abbauen"
        :server="selectedServer"
        job-to-call="WINDOWS_DELETE_SERVER"
        show-confirm-dialog
        confirm-dialog-title="Server abbauen"
        confirm-dialog-text="Wollen Sie diesen Server wirklich löschen?"
        use-extra-sure-dialog
        extra-sure-checkbox-text="Ich bin mir sicher, dass ich diesen Server abbauen möchte."
      />
    </template>

    <template #banners>
      <v-row v-if="selectedServer.runningJobsCount > 0">
        <v-col
          cols="12"
          class="pt-0 pb-0"
        >
          <div
            class="job-banner"
            aria-label="Zur History"
            @click="$emit('navigateToHistory')"
          >
            <strong>{{ jobBannerText }}</strong>
          </div>
        </v-col>
      </v-row>

      <v-row v-if="selectedServer.runningGreenItCount > 0">
        <v-col
          cols="12"
          class="pt-0 pb-0"
        >
          <div
            class="green-it-banner"
            :class="{ 'dark-mode': isDark }"
            aria-label="Zur History"
            @click="$emit('navigateToHistory')"
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
    </template>

    <template #statusChips>
      <appservice-assignment-status-chips
        :can-edit="selectedServer.canEdit"
        :assigned-count="selectedServer.numberOfAssignedAppservices"
        :loading="loadingServerDetails"
        entity-label="Server"
      />
      <status-chip
        v-if="
          !loadingServerDetails &&
          selectedServer.hasTempAdminPrivileges &&
          selectedServer.tempPrivilegesExpiresAt
        "
        :value="false"
        :check-value="true"
        match-text=""
        :not-match-text="tempAdminText"
        match-mode="equal"
      />
      <status-chip
        v-if="
          !loadingServerDetails &&
          selectedServer.hasTempRootPrivileges &&
          selectedServer.tempPrivilegesExpiresAt
        "
        :value="false"
        :check-value="true"
        match-text=""
        :not-match-text="tempRootText"
        match-mode="equal"
      />
      <status-chip
        v-if="
          !loadingServerDetails &&
          selectedServer.maintenanceMode &&
          selectedServer.maintenanceModeExpiresAt
        "
        :value="false"
        :check-value="true"
        match-text=""
        :not-match-text="maintenanceText"
        match-mode="equal"
      />
      <status-chip
        v-if="
          !loadingServerDetails &&
          Number(selectedServer.patchnightExitcode) != 0
        "
        :value="Number(selectedServer.patchnightExitcode) === 0"
        :check-value="true"
        match-text=""
        not-match-text="Fehler bei Patchnight"
        match-mode="equal"
        @click="$emit('navigateToPatchnight')"
      />
      <status-chip
        v-if="
          !loadingServerDetails &&
          selectedServer.canEdit &&
          !selectedServer.managed
        "
        :value="!selectedServer.managed"
        :check-value="false"
        match-text=""
        not-match-text="Nicht verwaltet"
        match-mode="equal"
        tooltip="Bearbeitung nur eingeschränkt möglich."
        href="https://mcmp.muenchen.de/#/help/9"
      />
    </template>
  </detail-page-header>
</template>

<script setup lang="ts">
import type Server from "@/types/Server";

import {
  mdiDelete,
  mdiKeyChain,
  mdiPause,
  mdiPauseCircle,
  mdiPlay,
  mdiPlayCircle,
  mdiRestart,
  mdiStop,
  mdiStopCircle,
} from "@mdi/js";
import { computed } from "vue";
import { useTheme } from "vuetify";

import AppserviceAssignmentStatusChips from "@/components/common/AppserviceAssignmentStatusChips.vue";
import DetailPageHeader from "@/components/common/DetailPageHeader.vue";
import StatusChip from "@/components/common/StatusChip.vue";
import ActionButton from "@/components/Server/ActionButtons/ActionButton.vue";
import CheckmkMenu from "@/components/Server/ActionButtons/CheckmkMenu.vue";
import PauseServerBtn from "@/components/Server/ActionButtons/PauseServerBtn.vue";
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

const powerStateIcon = computed(() => {
  switch (props.selectedServer.powerState) {
    case "poweredOn":
      return mdiPlayCircle;
    case "poweredOff":
      return mdiStopCircle;
    default:
      return mdiPauseCircle;
  }
});

const powerStateIconColor = computed(() => {
  switch (props.selectedServer.powerState) {
    case "poweredOn":
      return "btn_green";
    case "poweredOff":
      return "btn_red";
    default:
      return "accent";
  }
});

const isOperator = computed(() =>
  userStore.getUser?.authorities.includes("ROLE_OPERATOR")
);

const canExecuteOperatorActions = computed(() => {
  return isOperator.value && !appStore.isReadOnly;
});

const emit = defineEmits<{
  (e: "navigateToHistory"): void;
  (e: "navigateToPatchnight"): void;
  (e: "change"): void;
}>();

const theme = useTheme();
const isDark = computed(() => theme.global.current.value.dark);

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
  const isVirtual =
    canEdit && (cloudType == "VMWARE" || cloudType == "PROXMOX");
  const hasWinManaged = canEdit && isWindows.value && managed;
  const hasLinuxManaged =
    (canEdit || (isOperator.value && !locked)) && isLinux.value && managed;
  const hasCheckmk = canEdit;
  const hasDelete =
    canEdit &&
    managed &&
    (cloudType == "VMWARE" || cloudType == "PROXMOX") &&
    (isWindows.value || isLinux.value);
  return (
    isVirtual || hasWinManaged || hasLinuxManaged || hasCheckmk || hasDelete
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
  .job-banner {
    display: none !important;
  }
}
</style>
