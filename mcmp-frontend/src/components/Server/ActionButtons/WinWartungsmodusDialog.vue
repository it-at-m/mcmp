<template>
  <CommonDialog
    v-model="dialog"
    :title="title"
    :max-width="title === 'Wartungsmodus setzen' ? 800 : 600"
    :icon="mdiTools"
    show-actions
    :submitActivated="validated"
    @dialog-cancel="close"
    @dialog-confirm="save"
    showChangeWarning
    :checkForEnabledActions="
      title === 'Wartungsmodus setzen'
        ? ['WINDOWS_MAINTENANCE_MODE']
        : ['WINDOWS_MAINTENANCE_MODE_END']
    "
  >
    <template #activator="{ props }">
      <v-list-item-title
        v-bind="props"
        style="cursor: pointer"
        >{{ title }}
      </v-list-item-title>
    </template>
    <v-form
      ref="form"
      v-if="title === 'Wartungsmodus setzen'"
    >
      <CommonWarning
        color="notice_red"
        class="mb-3"
      >
        <h4>Hinweis:</h4>
        Der Server wird sowohl beim versetzen in den Wartungsmodus als auch beim
        beenden des Wartungsmodus neugestartet.<br />
        Die automatische Beendigung des Wartungsmodus läuft zur vollen und zur
        halben Stunde.
      </CommonWarning>
      <v-row justify="center">
        <v-col cols="6">
          <CommonTimePicker
            lableText="End"
            :timeRules="[
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
            v-model:rawDateIn="rawEndDate"
            round
            withButtons
          />
        </v-col>
      </v-row>
    </v-form>

    <v-form
      ref="form"
      v-if="title === 'Wartungsmodus vorzeitig beenden'"
    >
      <CommonWarning color="notice_red">
        <h4>Hinweis:</h4>
        Der Server wird beim Beenden des Wartungsmodus neugestartet.
      </CommonWarning>
    </v-form>
  </CommonDialog>
</template>

<script setup lang="ts">
import type Server from "@/types/Server.ts";

import { mdiTools } from "@mdi/js";
import { inject, ref, watch } from "vue";

import jobService from "@/api/jobService";
import CommonWarning from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import { useRules } from "@/composables/rules";

const props = defineProps<{
  server: Server;
  title: string;
}>();

const emit = defineEmits<{
  (e: "save", save: boolean): boolean;
}>();

const validationRules = useRules();

const dialog = ref(false);
const form = ref<HTMLFormElement>();
const loading = ref(false);
const validated =
  props.title === "Wartungsmodus vorzeitig beenden" ? ref(true) : ref(false);

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

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
      if (props.title === "Wartungsmodus setzen") {
        jobService.startJob(
          loading,
          "WINDOWS_MAINTENANCE_MODE",
          props.server.id,
          {
            wartungsmodus_ende: `${rawEndDate.value.toLocaleDateString('de-DE', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
            })} ${rawEndDate.value.toLocaleTimeString('de-DE')}`,
          }
        );
      }

      if (props.title == "Wartungsmodus vorzeitig beenden") {
        jobService.startJob(
          loading,
          "WINDOWS_MAINTENANCE_MODE_END",
          props.server.id
        );
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
