<template>
  <v-tooltip
    :text="snapshotActions[props.action].title"
    location="bottom"
  >
    <template #activator="{ props: tooltipProps }">
      <v-btn
        v-bind="tooltipProps"
        text
        class="mr-2"
        rounded
        @click="dialog = true"
      >
        <v-icon size="x-large">{{ snapshotActions[props.action].icon }}</v-icon>
      </v-btn>
    </template>
  </v-tooltip>
  <common-dialog
    :model-value="dialog"
    :max-width="650"
    :title="snapshotActions[props.action].title"
    :icon="snapshotActions[props.action].icon"
    :show-actions="true"
    submit-activated
    :check-for-enabled-actions="
      action == 'revert'
        ? ['VMWARE_REVERT_SNAPSHOT']
        : ['VMWARE_DELETE_SNAPSHOT']
    "
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <v-row
      v-if="action == 'revert'"
      class="mb-2"
    >
      <common-alert is-snow-change />
    </v-row>

    {{ snapshotActions[props.action].infoText(props.snapshot) }}
  </common-dialog>
</template>

<script setup lang="ts">
import type Snapshot from "@/types/Snapshot";

import { mdiArrowULeftTop, mdiDelete } from "@mdi/js";
import { inject, ref, watch } from "vue";

import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";

const props = defineProps<{
  snapshot: Snapshot;
  action: string;
}>();

const emit = defineEmits<(e: "save", save: boolean) => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const dialog = ref(false);
const days = ref(2);
const description = ref("");

// Dialog-Status überwachen
watch(dialog, (newValue) => {
  if (newValue) {
    registerOpenDialog?.();
  } else {
    unregisterOpenDialog?.();
  }
});

const snapshotActions = {
  delete: {
    icon: mdiDelete,
    title: "Snapshot löschen",
    infoText: (snapshot: Snapshot) =>
      `Diese Aktion ist endgültig und kann nicht rückgängig gemacht werden.\nMöchten Sie "${snapshot.name}" wirklich löschen?`,
    buttonText: "löschen",
  },
  revert: {
    icon: mdiArrowULeftTop,
    title: "Auf ausgewählten Snapshot zurücksetzen",
    infoText: (snapshot: Snapshot) =>
      `Der aktuelle Zustand dieser virtuellen Maschine geht verloren.\nMöchten Sie den aktuellen Zustand der virtuellen Maschine wirklich auf den Snapshot "${snapshot.name}" zurücksetzen?`,
    buttonText: "zurücksetzen",
  },
};

function close() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  days.value = 2;
  description.value = "";
}

function save() {
  dialog.value = false;
  emit("save", true);
  resetForm();
}
</script>
