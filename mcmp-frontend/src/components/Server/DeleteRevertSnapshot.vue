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
  <CommonDialog
    :model-value="dialog"
    :maxWidth="650"
    :title="snapshotActions[props.action].title"
    :icon="snapshotActions[props.action].icon"
    :showActions="true"
    submitActivated
    @dialog-cancel="close"
    @dialog-confirm="save"
    :checkForEnabledActions="
      action == 'revert'
        ? ['VMWARE_REVERT_SNAPSHOT']
        : ['VMWARE_DELETE_SNAPSHOT']
    "
  >
    <v-row
      class="mb-2"
      v-if="action == 'revert'"
    >
      <CommonAlert isSnowChange />
    </v-row>

    {{ snapshotActions[props.action].infoText(props.snapshot) }}
  </CommonDialog>
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

const emit = defineEmits<{
  (e: "save", save: boolean): void;
}>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const form = ref<HTMLFormElement>();
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
