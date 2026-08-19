<template>
  <v-tooltip
    :text="tooltip"
    location="bottom"
    :aria-label="tooltip"
  >
    <template #activator="{ props: tooltipProps }">
      <span
        v-bind="tooltipProps"
        class="tooltip-activator"
        style="display: inline-flex"
      >
        <v-btn
          :disabled="computedDisabled || loading"
          :color="color"
          :loading="loading"
          class="material-action-btn"
          variant="flat"
          :href="link"
          target="_blank"
          icon
          size="small"
          :alt="tooltip"
          :aria-label="tooltip"
          v-on="server || props.isBatchOperation ? { click: onBtnClick } : {}"
        >
          <v-icon
            :size="tooltip == 'KVM Konsole öffnen' ? 'large' : 'x-large'"
            role="img"
            :style="
              tooltip == 'Server neustarten' ? 'transform: rotate(45deg);' : ''
            "
            >{{ icon }}
          </v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>

  <common-dialog
    :model-value="dialog"
    :title="confirmDialogTitle"
    :icon="icon"
    max-width="600"
    show-actions
    :submit-activated="validated"
    :show-change-warning="true"
    :check-for-enabled-actions="action"
    @dialog-confirm="onDialogConfirm"
    @dialog-cancel="onDialogCancel"
  >
    <common-alert
      v-if="
        server || (props.isBatchOperation && props.selectedServerIds?.length)
      "
      color="accent"
    >
      <div
        v-if="server"
        class="server-info-label"
      >
        Ausgewählter Server:
      </div>
      <div
        v-if="server"
        class="server-name"
      >
        {{ server.name }}
      </div>

      <div
        v-else-if="props.isBatchOperation"
        class="server-info-label"
      >
        Ausgewählte Server:
      </div>
      <div
        v-else-if="props.isBatchOperation"
        class="server-name"
      >
        {{ props.selectedServerIds?.length }} Server
      </div>

      <div
        v-if="
          props.isBatchOperation &&
          props.selectedServers &&
          props.selectedServers.length
        "
        class="mt-2"
      >
        <ul class="pl-4">
          <li
            v-for="s in props.selectedServers.filter((x) =>
              props.selectedServerIds?.includes(x.id)
            )"
            :key="s.id"
          >
            {{ s.name }}
          </li>
        </ul>
      </div>
    </common-alert>

    <br />
    {{ confirmDialogText || "Wollen sie die Aktion ausführen?" }}
    <br />
    <v-form ref="form">
      <v-checkbox
        v-model="schedule"
        label="Durchführungszeitpunkt anpassen"
        @change="changeToSchedule"
      />
      <common-time-picker
        v-if="schedule"
        v-model:raw-date-in="rawDate"
        lable-text="Durchführungs"
        :time-rules="[
          validationRules.notEmptyRule('Endzeitpunkt darf nicht leer sein.'),
          validationRules.isNotPastTime(
            new Date(),
            rawDate,
            'Endzeitpunkt darf nicht in der Vergangenheit liegen.'
          ),
        ]"
      />
    </v-form>
    <a
      v-if="confirmDialogLink"
      :href="confirmDialogLink"
      target="_blank"
      >hier</a
    >
  </common-dialog>

  <dialog-extra-sure
    v-if="props.useExtraSureDialog && extraSureDialog"
    v-model="extraSureDialog"
    :title="confirmDialogTitle || 'Bestätigen sie die Aktion'"
    :text="confirmDialogText || 'Wollen sie die Aktion ausführen?'"
    :checkbox-text="extraSureCheckboxText || 'Ich bin mir sicher'"
    :icon="icon"
    @do="onExtraSureDialogConfirm"
    @cancel="onDialogCancel"
  />
</template>

<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";

import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import DialogExtraSure from "@/components/common/dialogExtraSure.vue";
import { useRules } from "@/composables/rules";
import Server from "@/types/Server";

const props = defineProps<{
  color?: string;
  icon: string;
  disabled?: boolean;
  tooltip: string;
  link?: string;
  server?: Server;
  jobToCall?: string;
  showConfirmDialog?: boolean;
  confirmDialogTitle?: string;
  confirmDialogText?: string;
  confirmDialogLink?: string;
  useExtraSureDialog?: boolean;
  extraSureCheckboxText?: string;
  // batch support
  isBatchOperation?: boolean;
  selectedServerIds?: number[];
  selectedServers?: Server[];
  parentAllSelectedServersEligible?: boolean;
}>();

const emit = defineEmits<(e: "change") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const loading = ref(false);
const dialog = ref(false);
const form = ref<HTMLFormElement>();
const extraSureDialog = ref(false);

const schedule = ref(false);
const validated = ref(true);
const rawDate = ref<Date>(new Date());
const validationRules = useRules();

const action = computed(() => {
  if (!props.jobToCall?.includes("DELETE")) {
    if (props.isBatchOperation) {
      return [
        ...new Set(
          props.selectedServers?.map((server) => server.cloud.cloudType)
        ),
      ].map((type) => `${type}_${props.jobToCall}`);
    }
    return [props.server?.cloud.cloudType + "_" + props.jobToCall];
  } else {
    return [props.jobToCall];
  }
});

function changeToSchedule() {
  rawDate.value = new Date();
}

function onBtnClick() {
  if (props.showConfirmDialog) {
    dialog.value = true;
    registerOpenDialog?.();
  } else {
    makeJobCall();
  }
}

function onDialogConfirm() {
  dialog.value = false;
  if (props.useExtraSureDialog) {
    extraSureDialog.value = true;
  } else {
    unregisterOpenDialog?.();
    makeJobCall();
  }
}

function onExtraSureDialogConfirm() {
  extraSureDialog.value = false;
  unregisterOpenDialog?.();
  makeJobCall();
}

function onDialogCancel() {
  dialog.value = false;
  extraSureDialog.value = false;
  validated.value = true;
  schedule.value = false;
  rawDate.value = new Date();
  unregisterOpenDialog?.();
}

function makeJobCall() {
  // batch operation: iterate over selectedServerIds
  if (props.isBatchOperation) {
    const servers = props.selectedServers || [];
    if (servers.length === 0) {
      return;
    }

    // parent-driven disabled reason takes precedence
    if (!props.parentAllSelectedServersEligible) {
      return;
    }

    loading.value = true;
    const promises = servers.map((server) =>
      jobService.startJob(
        loading,
        props.jobToCall,
        server.id,
        schedule.value ? { scheduleTime: rawDate.value.toISOString() } : {}
      )
    );

    Promise.all(promises)
      .then(() => emit("change"))
      .catch((err) => console.error("Batch job error", err))
      .finally(() => {
        loading.value = false;
        validated.value = true;
        schedule.value = false;
        rawDate.value = new Date();
      });

    return;
  }

  if (!props.jobToCall) {
    return;
  }

  jobService
    .startJob(
      loading,
      props.jobToCall,
      props.server!.id,
      schedule.value ? { scheduleTime: rawDate.value.toISOString() } : {}
    )
    .then(() => {
      emit("change");
    });
  validated.value = true;
  schedule.value = false;
  rawDate.value = new Date();
}

// computed disabled: respect parent batch eligibility only when used as batch operation
const computedDisabled = computed(() => {
  if (props.isBatchOperation) {
    // if parent explicitly flagged selection as not eligible, disable
    if (!props.parentAllSelectedServersEligible) return true;
    const ids = props.selectedServerIds ?? [];
    return ids.length === 0;
  }
  // single-server usage: use explicit disabled prop only
  return props.disabled ?? false;
});

watch([rawDate], async () => {
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
});
</script>

<style scoped>
.material-action-btn {
  border-radius: 50% !important;
  margin: 0 4px;
  width: 33.35px !important;
  height: 33.35px !important;
  box-shadow:
    0 3px 1px -2px rgba(0, 0, 0, 0.2),
    0 2px 2px 0 rgba(0, 0, 0, 0.14),
    0 1px 5px 0 rgba(0, 0, 0, 0.12);
  transition: box-shadow 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.material-action-btn:hover {
  box-shadow:
    0 2px 4px -1px rgba(0, 0, 0, 0.2),
    0 4px 5px 0 rgba(0, 0, 0, 0.14),
    0 1px 10px 0 rgba(0, 0, 0, 0.12);
}

.server-info-label {
  font-size: 0.875rem;
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-accent));
  font-weight: 500;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.server-name {
  font-size: 1.25rem;
  font-weight: 700;
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-text));
  padding: 4px 0;
  word-break: break-word;
}
</style>
