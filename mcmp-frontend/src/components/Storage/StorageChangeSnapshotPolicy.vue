<template>
  <v-tooltip
    location="bottom"
    text="Snapshot Policy ändern"
    :open-on-hover="true"
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          icon
          variant="flat"
          aria-label="Snapshot Policy ändern"
          @click="openDialog"
        >
          <v-icon>{{ mdiClockEditOutline }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    :model-value="dialog"
    max-width="1100"
    :title="title"
    :icon="mdiClockEditOutline"
    show-actions
    :submit-activated="validated"
    show-change-warning
    :check-for-enabled-actions="[
      'STORAGE_CHANGE_SNAPSHOT_POLICY_NFS',
      'STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS',
    ]"
    @dialog-cancel="close()"
    @dialog-confirm="save()"
  >
    <v-form v-model="validated">
      <common-alert
        color="notice_red"
        class="mb-3"
      >
        <h4>Achtung:</h4>
        Bestehende per Policy erstellte Snapshots, die nicht zur neuen
        Snapshotpolicy passen werden gelöscht.
      </common-alert>
      <h4>Neue Snapshot Policy:</h4>
      <v-select
        v-model="selectedPolicy"
        :items="policies"
        item-title="title"
        item-value="value"
        label="Wähle eine neue Policy"
        :rules="[
          useRules().notEmptyRule('Eine Policy muss ausgewählt werden.'),
        ]"
      ></v-select>
    </v-form>
  </common-dialog>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";

import { mdiClockEditOutline } from "@mdi/js";
import { computed, ref } from "vue";

import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
  policies: { title: string; value: string }[];
  initialPolicy?: string;
}>();
const emits = defineEmits<(e: "save", newPolicy: string) => void>();

const dialog = ref(false);
const validated = ref(false);

const selectedPolicy = ref<string | null>(null);

const title = computed(() => {
  return `Snapshot Policy für ${props.selectedStorageItem.type === "NFS" ? props.selectedStorageItem.nfs_mount_path : props.selectedStorageItem.cifs_mount_path} ändern`;
});

function openDialog() {
  selectedPolicy.value = props.initialPolicy;
  validated.value = false;
  dialog.value = true;
}

function close() {
  dialog.value = false;
}

function save() {
  if (validated.value && selectedPolicy.value) {
    emits("save", selectedPolicy.value);
    close();
  }
}
</script>
