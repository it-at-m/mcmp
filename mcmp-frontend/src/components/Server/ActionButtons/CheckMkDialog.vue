<template>
  <v-tooltip
    v-if="isBatchOperation"
    location="bottom"
    :text="tooltip"
    :open-on-hover="true"
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          icon
          :disabled="isBatchDisabled"
          variant="flat"
          aria-label="Downtime setzen"
          @click="openDialog"
        >
          <img
            :src="checkmkIcon"
            alt="Checkmk"
            width="24"
            height="24"
          />
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    v-model="dialog"
    :title="title"
    :max-width="title == 'Downtime setzen' ? 1000 : 600"
    show-actions
    :submit-activated="validated"
    show-change-warning
    :check-for-enabled-actions="
      title === 'Downtime setzen'
        ? ['CHECKMK_SET_DOWNTIME']
        : ['CHECKMK_SERVICE_DISCOVERY']
    "
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <template
      v-if="!isBatchOperation"
      #activator="{ props }"
    >
      <v-list-item-title
        v-bind="props"
        style="cursor: pointer"
        >{{ title }}
      </v-list-item-title>
    </template>
    <v-form
      v-if="title === 'Downtime setzen'"
      ref="form"
    >
      <v-row>
        <!-- Startzeitpunkt -->
        <v-col cols="6">
          <common-time-picker
            v-model:raw-date-in="rawStartDate"
            lable-text="Start"
            :time-rules="[
              validationRules.notEmptyRule(
                'Eindzeitpunkt darf nicht leer sein.'
              ),
              isAfter(
                rawStartDate,
                rawEndDate,
                'Startzeitpunkt darf nicht nach dem Endzeitpunkt liegen.'
              ),
            ]"
          />
        </v-col>

        <!-- Endzeitpunkt -->
        <v-col cols="6">
          <common-time-picker
            v-model:raw-date-in="rawEndDate"
            lable-text="End"
            :time-rules="[
              validationRules.notEmptyRule(
                'Endzeitpunkt darf nicht leer sein.'
              ),
              isAfter(
                rawStartDate,
                rawEndDate,
                'Startzeitpunkt darf nicht nach dem Endzeitpunkt liegen.'
              ),
            ]"
          />
        </v-col>
      </v-row>
    </v-form>

    <v-form
      v-if="title === 'Service Discovery'"
      ref="form"
    >
      <v-row class="mb-1">
        <common-alert color="notice_red">
          <h4>Hinweis:</h4>
          <u>Neu:</u> Neue bzw. zusätzliche erkannte Service(s) in das
          Monitoring aufnehmen - "add unmonitored Services".<br />
          <u>Entfernen:</u> Entfernen von Service(s) aus dem Monitoring, die
          keine Daten mehr vom Host erhalten - "remove vanished Services".<br />
          <u>Bereinigen:</u> Entfernt alte und fügt neuer Service(s) hinzu -
          alle erkannten Änderungen übernehmen
        </common-alert>
      </v-row>
      <br />
      <v-row>
        <v-radio-group
          v-model="action"
          aria-label="Aktion auswählen"
        >
          <v-radio
            label="Neu"
            value="new"
          />
          <v-radio
            label="Entfernen"
            value="remove"
          />
          <v-radio
            label="Bereinigen (Neu + Entfernen)"
            value="fix_all"
          />
        </v-radio-group>
      </v-row>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Server from "@/types/Server.ts";

import { computed, inject, ref, watch } from "vue";

import jobService from "@/api/jobService";
import checkmkIcon from "@/assets/checkmk.svg";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import { useRules } from "@/composables/rules";

const validationRules = useRules();

const dialog = ref(false);
const form = ref<HTMLFormElement>();
const loading = ref(false);
const action = ref("new");
const validated = ref(false);

const props = defineProps<{
  server?: Server;
  title: string;
  isBatchOperation?: boolean;
  selectedServerIds?: number[];
  parentAllSelectedServersEligible?: boolean;
  parentDisabledTooltip?: string;
}>();

const emit = defineEmits<(e: "save", save: boolean) => boolean>();

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
    return props.parentDisabledTooltip || "Nicht berechtigt oder Server nicht verwaltet.";
  }
  return props.title;
});

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

// Startzeit
const rawStartDate = ref<Date>(new Date());
// Endzeit
const rawEndDate = ref<Date>(new Date());

function formatDate(date: Date) {
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = date.getFullYear();
  return `${day}.${month}.${year}`;
}

function formatTime(date: Date) {
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${hours}:${minutes}`;
}

function isAfter(toCompareDate: Date, toValidateDate: Date, message = "Error") {
  return toValidateDate > toCompareDate || message;
}

function getDifferenceInMinutes(date1: Date, date2: Date): number {
  const diffMs = date2.getTime() - date1.getTime();
  return Math.floor(diffMs / (1000 * 60));
}

function resetForm() {
  rawStartDate.value = new Date();
  rawEndDate.value = new Date();
}

function close() {
  dialog.value = false;
  resetForm();
}

function openDialog() {
  if (props.isBatchOperation && isBatchDisabled.value) return;
  dialog.value = true;
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      if (props.title === "Downtime setzen") {
        const downtimePayload = {
          startDate: `${formatDate(rawStartDate.value)} ${formatTime(rawStartDate.value)}:00`,
          duration: `${getDifferenceInMinutes(rawStartDate.value, rawEndDate.value)}`,
        };
        if (props.isBatchOperation) {
          (props.selectedServerIds ?? []).forEach((id) => {
            jobService.startJob(loading, "CHECKMK_SET_DOWNTIME", id, downtimePayload);
          });
        } else {
          jobService.startJob(loading, "CHECKMK_SET_DOWNTIME", props.server!.id, downtimePayload);
        }
      }

      if (props.title == "Service Discovery") {
        jobService.startJob(
          loading,
          "CHECKMK_SERVICE_DISCOVERY",
          props.server!.id,
          {
            action: action.value,
          }
        );
      }

      dialog.value = false;
      emit("save", true);
      resetForm();
    }
  });
}

watch([rawStartDate, rawEndDate], async () => {
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
});

watch(dialog, (newValue) => {
  if (newValue) {
    registerOpenDialog?.();
    if (props.title === "Service Discovery") {
      validated.value = true;
    }
  } else unregisterOpenDialog?.();
});
</script>
