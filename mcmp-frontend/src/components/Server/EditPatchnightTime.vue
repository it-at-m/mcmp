<template>
  <common-dialog
    v-model="dialog"
    title="Zeit für zukünftige Patchnight anpassen"
    max-width="800"
    :icon="mdiPencil"
    show-actions
    submit-activated
    :check-for-enabled-actions="['LINUX_PATCHNIGHT_TIME_CHANGE']"
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        icon
        variant="flat"
        aria-label="Zeit für zukünftige Patchnight anpassen"
      >
        <v-icon>{{ mdiPencil }}</v-icon>
      </v-btn>
    </template>
    <v-form ref="form">
      <strong>Neue Zeit</strong>
      <v-radio-group
        v-model="selectedTime"
        aria-label="neue Zeit für Patchnight auswählen"
        inline
      >
        <v-radio
          v-for="time in Array.from({ length: 5 }, (_, i) => {
            const hour = startTime + i * 2;

            if (hour === 24) {
              return '24:00';
            }

            return (hour % 24).toString().padStart(2, '0') + ':00';
          })"
          :key="time"
          :label="time"
          :value="time"
        />
      </v-radio-group>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Server from "@/types/Server";

import { mdiPencil } from "@mdi/js";
import { inject, ref, watch } from "vue";

import CommonDialog from "@/components/common/CommonDialog.vue";
import { useFormatter } from "@/composables/formatter.js";

const formatter = useFormatter();

const props = defineProps<{
  selectedServer: Server;
}>();

const emit = defineEmits<(e: "save", time: string) => void>();

const selectedTime = ref(props.selectedServer.patchnightTime);
const kPatchnightStartTime = 15;
const pPatchnightStartTime = 20;
const startTime = ref<number>(
  props.selectedServer.patchnightEnvironment == "K"
    ? kPatchnightStartTime
    : pPatchnightStartTime
);

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const form = ref<HTMLFormElement>();
const dialog = ref(false);

// Dialog-Status überwachen
watch(dialog, (newValue) => {
  if (newValue) {
    registerOpenDialog?.();
  } else {
    unregisterOpenDialog?.();
  }
});

function close() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  selectedTime.value = props.selectedServer.patchnightTime;
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emit("save", selectedTime.value);
      dialog.value = false;
      resetForm();
    }
  });
}
</script>
