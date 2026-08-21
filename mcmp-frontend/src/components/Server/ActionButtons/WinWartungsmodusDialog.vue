<template>
  <v-tooltip
    v-if="isBatchOperation && !asMenuItem"
    location="bottom"
    :text="tooltip"
    :open-on-hover="true"
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          class="material-action-btn"
          variant="flat"
          :icon="mdiTools"
          size="small"
          :disabled="isBatchDisabled"
          :aria-label="title"
          @click="openDialog"
        />
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    v-model="dialog"
    :title="title"
    :max-width="title === 'Wartungsmodus setzen' ? 800 : 600"
    :icon="mdiTools"
    show-actions
    :submit-activated="validated"
    show-change-warning
    :check-for-enabled-actions="
      title === 'Wartungsmodus setzen'
        ? ['WINDOWS_MAINTENANCE_MODE']
        : ['WINDOWS_MAINTENANCE_MODE_END']
    "
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <template
      v-if="!isBatchOperation || asMenuItem"
      #activator="{ props: activatorProps }"
    >
      <v-list-item
        v-bind="activatorProps"
        :disabled="isBatchOperation && isBatchDisabled"
        :title="title"
      />
    </template>
    <v-form
      v-if="title === 'Wartungsmodus setzen'"
      ref="form"
    >
      <common-warning
        color="notice_red"
        class="mb-3"
      >
        <h4>Hinweis:</h4>
        Der Server wird sowohl beim versetzen in den Wartungsmodus als auch beim
        beenden des Wartungsmodus neugestartet.<br />
        Die automatische Beendigung des Wartungsmodus läuft zur vollen und zur
        halben Stunde.
      </common-warning>
      <v-row justify="center">
        <v-col cols="6">
          <common-time-picker
            v-model:raw-date-in="rawEndDate"
            lable-text="End"
            :time-rules="[
              validationRules.notEmptyRule(
                'Endzeitpunkt darf nicht leer sein.'
              ),
              validationRules.isNotPastTime(
                new Date(),
                rawEndDate,
                'Endzeitpunkt darf nicht in der Vergangenheit liegen.'
              ),
              getDifferenceInMinutes(new Date(), rawEndDate) < 20160 ||
                'Wartungsmodus darf nicht länger als 2 Wochen dauern.',
            ]"
            round
            with-buttons
          />
        </v-col>
      </v-row>
    </v-form>

    <v-form
      v-if="title === 'Wartungsmodus vorzeitig beenden'"
      ref="form"
    >
      <common-warning color="notice_red">
        <h4>Hinweis:</h4>
        Der Server wird beim Beenden des Wartungsmodus neugestartet.
      </common-warning>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Server from "@/types/Server.ts";

import { mdiTools } from "@mdi/js";
import { computed, inject, ref, watch } from "vue";

import jobService from "@/api/jobService";
import CommonWarning from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import { useRules } from "@/composables/rules";

const props = defineProps<{
  server?: Server;
  title: string;
  isBatchOperation?: boolean;
  asMenuItem?: boolean;
  selectedServerIds?: number[];
  parentAllSelectedServersEligible?: boolean;
  parentDisabledTooltip?: string;
}>();

const emit = defineEmits<(e: "save", save: boolean) => boolean>();

const validationRules = useRules();

const dialog = ref(false);
const form = ref<HTMLFormElement>();
const loading = ref(false);
const validated =
  props.title === "Wartungsmodus vorzeitig beenden" ? ref(true) : ref(false);

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const isBatchDisabled = computed(() => {
  if (!props.isBatchOperation) return false;
  if (!props.parentAllSelectedServersEligible) return true;
  return (props.selectedServerIds ?? []).length === 0;
});

const tooltip = computed(() => {
  if (!props.isBatchOperation) return props.title;
  if ((props.selectedServerIds ?? []).length === 0) {
    return "Keine Server ausgewählt.";
  }
  if (!props.parentAllSelectedServersEligible) {
    return (
      props.parentDisabledTooltip ||
      "Nicht berechtigt oder Server nicht verwaltet."
    );
  }
  return props.title;
});

function openDialog() {
  if (props.isBatchOperation && isBatchDisabled.value) return;
  dialog.value = true;
}

// Endzeit
const rawEndDate = ref<Date>(new Date());

function getDifferenceInMinutes(date1: Date, date2: Date): number {
  const diffMs = date2.getTime() - date1.getTime();
  return Math.floor(diffMs / (1000 * 60));
}

function resetForm() {
  rawEndDate.value = new Date();
}

function close() {
  dialog.value = false;
  resetForm();
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      const targetIds = props.isBatchOperation
        ? (props.selectedServerIds ?? [])
        : props.server
          ? [props.server.id]
          : [];

      if (props.title === "Wartungsmodus setzen") {
        const wartungsmodusEnde = `${rawEndDate.value.toLocaleDateString(
          "de-DE",
          {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
          }
        )} ${rawEndDate.value.toLocaleTimeString("de-DE")}`;
        targetIds.forEach((id) => {
          jobService.startJob(loading, "WINDOWS_MAINTENANCE_MODE", id, {
            wartungsmodus_ende: wartungsmodusEnde,
          });
        });
      }

      if (props.title == "Wartungsmodus vorzeitig beenden") {
        targetIds.forEach((id) => {
          jobService.startJob(loading, "WINDOWS_MAINTENANCE_MODE_END", id);
        });
      }

      dialog.value = false;
      emit("save", true);
      resetForm();
    }
  });
}

watch([rawEndDate], async () => {
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
});

watch(dialog, (newValue) => {
  if (newValue) registerOpenDialog?.();
  else unregisterOpenDialog?.();
});
</script>
