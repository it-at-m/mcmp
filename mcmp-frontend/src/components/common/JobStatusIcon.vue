<template>
  <v-tooltip
    :text="status ?? ''"
    :disabled="hideTooltip"
  >
    <template #activator="{ props }">
      <div
        :class="[
          'status-icon',
          isSquare ? 'square-icon' : 'round-icon',
          backgroundClass !== '' ? 'transparent-bg' : '',
        ]"
      >
        <v-icon
          v-bind="props"
          :icon="icon"
          :color="color"
          :class="backgroundClass"
        />
      </div>
    </template>
  </v-tooltip>
</template>

<script setup lang="ts">
import {
  mdiAutorenew,
  mdiCancel,
  mdiCheckCircle,
  mdiClockOutline,
  mdiCloseCircle,
  mdiCogSync,
  mdiMinusCircle,
  mdiNewBox,
  mdiProgressWrench,
  mdiRedo,
} from "@mdi/js";
import { computed } from "vue";

const props = defineProps<{
  status: string | null | undefined;
  hideTooltip?: boolean;
}>();

const upperStatus = computed(() => props.status?.toUpperCase() ?? "");

const isSquare = computed(() => upperStatus.value === "NEW");

const icon = computed(() => {
  switch (upperStatus.value) {
    case "SUCCESSFUL":
    case "AWX_COMPLETED":
    case "QUICKDISCOVERY_COMPLETED":
    case "TAGGING_COMPLETED":
    case "APPROVED":
    case "INCIDENT_SUCCESSFUL":
      return mdiCheckCircle;
    case "FAILED":
    case "ERROR":
    case "QUICKDISCOVERY_FAILED":
    case "TAGGING_FAILED":
    case "INCIDENT_FAILED":
    case "LOGICAL_FAILED":
      return mdiCloseCircle;
    case "NEW":
      return mdiNewBox;
    case "CANCELED":
    case "REJECTED":
      return mdiCancel;
    case "WAITING_FOR_APPROVAL":
    case "WAITING_FOR_INCIDENT_RESOLUTION":
      return mdiProgressWrench;
    case "NOT EXECUTED":
      return mdiMinusCircle;
    case "SKIPPED":
      return mdiRedo;
    case "AWX_RUNNING":
      return mdiCogSync;
    case "WAITING_FOR_QUICKDISCOVERY":
    case "WAITING_FOR_TAGGING":
    case "WAITING_FOR_AWX_ENABLEMENT":
    case "WAITING_FOR_AWX_CONFIGURATION":
    case "WAITING_FOR_SERVICE_NOW_ENABLEMENT":
    case "WAITING_FOR_SERVICE_NOW_CONFIGURATION":
      return mdiClockOutline;
    default:
      return mdiAutorenew;
  }
});

const color = computed(() => {
  switch (upperStatus.value) {
    case "SUCCESSFUL":
    case "AWX_COMPLETED":
    case "QUICKDISCOVERY_COMPLETED":
    case "TAGGING_COMPLETED":
    case "APPROVED":
    case "INCIDENT_SUCCESSFUL":
    case "SKIPPED":
      return "success";
    case "FAILED":
    case "ERROR":
    case "QUICKDISCOVERY_FAILED":
    case "TAGGING_FAILED":
    case "INCIDENT_FAILED":
    case "CANCELED":
    case "REJECTED":
    case "LOGICAL_FAILED":
      return "error";
    case "NEW":
      return "accent";
    case "WAITING_FOR_INCIDENT_RESOLUTION":
    case "WAITING_FOR_APPROVAL":
    case "WAITING_FOR_QUICKDISCOVERY":
    case "WAITING_FOR_TAGGING":
    case "WAITING_FOR_SERVICE_NOW_ENABLEMENT":
    case "WAITING_FOR_SERVICE_NOW_CONFIGURATION":
    case "WAITING_FOR_AWX_ENABLEMENT":
    case "WAITING_FOR_AWX_CONFIGURATION":
    case "AWX_RUNNING":
    case "WAITING":
      return "warning";
    default:
      return "grey";
  }
});

const backgroundClass = computed(() => {
  switch (upperStatus.value) {
    case "SUCCESSFUL":
    case "AWX_COMPLETED":
    case "QUICKDISCOVERY_COMPLETED":
    case "TAGGING_COMPLETED":
    case "FAILED":
    case "ERROR":
    case "INCIDENT_FAILED":
    case "TAGGING_FAILED":
    case "QUICKDISCOVERY_FAILED":
    case "NEW":
    case "APPROVED":
    case "CANCELED":
    case "REJECTED":
    case "LOGICAL_FAILED":
    case "INCIDENT_SUCCESSFUL":
      return "";
    case "NOT EXECUTED":
      return "icon-grey-bg";
    case "WAITING_FOR_INCIDENT_RESOLUTION":
    case "WAITING_FOR_APPROVAL":
    case "WAITING_FOR_QUICKDISCOVERY":
    case "WAITING_FOR_TAGGING":
    case "WAITING_FOR_SERVICE_NOW_ENABLEMENT":
    case "WAITING_FOR_SERVICE_NOW_CONFIGURATION":
    case "WAITING_FOR_AWX_ENABLEMENT":
    case "WAITING_FOR_AWX_CONFIGURATION":
    case "AWX_RUNNING":
    case "WAITING":
      return "icon-yellow-bg";
    case "SKIPPED":
      return "icon-green-bg";
    default:
      return "icon-blue-bg";
  }
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.status-icon {
  display: flex;
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 15px !important;
  flex-shrink: 0;
}

.transparent-bg {
  background-color: transparent !important;
}

.round-icon {
  height: 15px !important;
  border-radius: 50%;
}

.square-icon {
  height: 12px !important;
}

.icon-grey-bg {
  background-color: grey;
  color: rgb(var(--v-theme-bg_icon)) !important;
  border-radius: 50%;
  font-size: 18px;
  text-align: center;
}

.icon-blue-bg {
  background-color: rgb(var(--v-theme-accent));
  color: rgb(var(--v-theme-bg_icon)) !important;
  border-radius: 50%;
  font-size: 18px;
  text-align: center;
}

.icon-yellow-bg {
  background-color: rgb(var(--v-theme-warning));
  color: rgb(var(--v-theme-bg_icon)) !important;
  border-radius: 50%;
  font-size: 18px;
  text-align: center;
}

.icon-green-bg {
  background-color: rgb(var(--v-theme-success));
  color: #ffffff !important;
  border-radius: 50%;
  font-size: 18px;
  text-align: center;
}
</style>
