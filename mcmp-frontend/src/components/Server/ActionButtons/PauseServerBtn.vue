<template>
  <v-tooltip
    :text="tooltipText"
    location="bottom"
  >
    <template #activator="{ props: tooltipProps }">
      <span
        v-bind="tooltipProps"
        class="tooltip-activator"
        style="display: inline-flex"
      >
        <v-btn
          :disabled="computedDisabled || loading"
          color="accent"
          :loading="loading"
          class="material-action-btn"
          variant="flat"
          icon
          size="small"
          @click="onBtnClick"
        >
          <v-icon size="x-large">{{ icon || mdiPause }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>

  <common-dialog
    :model-value="dialog"
    title="Geplante Downtime einrichten"
    :icon="icon || mdiPause"
    max-width="600"
    show-actions
    :submit-activated="validated"
    :check-for-enabled-actions="['VMWARE_STOP_SERVER', 'VMWARE_START_SERVER']"
    @dialog-confirm="onDialogConfirm"
    @dialog-cancel="onDialogCancel"
  >
    <common-alert
      v-if="server || (props.isBatchOperation && props.selectedServerIds?.length)"
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
        v-if="props.isBatchOperation && props.selectedServers?.length"
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

    <div class="mt-3 mb-2">
      Legen Sie fest, wann die VM(s) heruntergefahren und wann sie automatisch wieder gestartet werden soll(en):
    </div>

    <v-form ref="form">
      <!-- 1. Stopp-Zeitpunkt -->
      <common-time-picker
        v-model:raw-date-in="stopDate"
        lable-text="Ausschalt"
        :time-rules="[
          validationRules.notEmptyRule('Ausschaltzeitpunkt darf nicht leer sein.'),
          validationRules.isNotPastTime(
            new Date(),
            stopDate,
            'Ausschaltzeitpunkt darf nicht in der Vergangenheit liegen.'
          ),
        ]"
      />

      <div class="my-3"></div>

      <!-- 2. Start-Zeitpunkt -->
      <common-time-picker
        v-model:raw-date-in="startDate"
        lable-text="Einschalt"
        :time-rules="[
          validationRules.notEmptyRule('Einschaltzeitpunkt darf nicht leer sein.'),
          validationRules.isNotPastTime(
            new Date(),
            startDate,
            'Einschaltzeitpunkt darf nicht in der Vergangenheit liegen.'
          ),
          () => isStartAfterStop || 'Der Einschaltzeitpunkt muss nach dem Ausschaltzeitpunkt liegen.',
        ]"
      />
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import { mdiPause } from "@mdi/js";
import { computed, inject, ref, watch } from "vue";

import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import { useRules } from "@/composables/rules";
import type Server from "@/types/Server";

const props = defineProps<{
  server?: Server;
  disabled?: boolean;
  icon?: string;
  tooltip?: string;
  // Batch Support Props
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
const validated = ref(true);
const form = ref<HTMLFormElement>();

const stopDate = ref<Date>(new Date(Date.now() + 10 * 60 * 1000));
const startDate = ref<Date>(new Date(Date.now() + 2 * 60 * 60 * 1000));

const validationRules = useRules();

const isStartAfterStop = computed(() => {
  if (!startDate.value || !stopDate.value) return false;
  return startDate.value.getTime() > stopDate.value.getTime();
});

const computedDisabled = computed(() => {
  if (props.isBatchOperation) {
    if (!props.parentAllSelectedServersEligible) return true;
    const ids = props.selectedServerIds ?? [];
    return ids.length === 0;
  }
  return props.disabled ?? false;
});

const tooltipText = computed(() => {
  return props.tooltip || "Geplante Downtime (Ausschalten & Wiederhochfahren)";
});

function onBtnClick() {
  stopDate.value = new Date(Date.now() + 10 * 60 * 1000);
  startDate.value = new Date(Date.now() + 2 * 60 * 60 * 1000);
  dialog.value = true;
  registerOpenDialog?.();
}

function onDialogCancel() {
  dialog.value = false;
  validated.value = true;
  unregisterOpenDialog?.();
}

async function onDialogConfirm() {
  dialog.value = false;
  unregisterOpenDialog?.();

  loading.value = true;

  try {
    if (props.isBatchOperation) {
      const ids =
        props.selectedServerIds ?? props.selectedServers?.map((s) => s.id) ?? [];
      if (ids.length === 0 || !props.parentAllSelectedServersEligible) return;

      const promises = ids.flatMap((id) => [
        jobService.startJob(loading, "VMWARE_STOP_SERVER", id, {
          scheduleTime: stopDate.value.toISOString(),
        }),
        jobService.startJob(loading, "VMWARE_START_SERVER", id, {
          scheduleTime: startDate.value.toISOString(),
        }),
      ]);

      await Promise.all(promises);
    } else if (props.server) {
      await jobService.startJob(loading, "VMWARE_STOP_SERVER", props.server.id, {
        scheduleTime: stopDate.value.toISOString(),
      });
      await jobService.startJob(loading, "VMWARE_START_SERVER", props.server.id, {
        scheduleTime: startDate.value.toISOString(),
      });
    }

    emit("change");
  } catch (err) {
    console.error("Fehler beim Erstellen der geplanten Downtime:", err);
  } finally {
    loading.value = false;
  }
}

watch([stopDate, startDate], () => {
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
  color: rgb(var(--v-theme-accent));
  font-weight: 500;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.server-name {
  font-size: 1.25rem;
  font-weight: 700;
  color: rgb(var(--v-theme-text));
  padding: 4px 0;
  word-break: break-word;
}
</style>