<template>
  <template v-if="isAllowedShare">
    <v-tooltip
      location="bottom"
      :text="canEdit ? 'Berechtigung bearbeiten' : disabledReason"
      :open-on-hover="true"
    >
      <template #activator="{ props: tooltipProps }">
        <span v-bind="tooltipProps">
          <v-btn
            icon
            variant="flat"
            aria-label="Berechtigung bearbeiten"
            :disabled="!canEdit"
            :title="canEdit ? 'Berechtigung bearbeiten' : disabledReason"
            @click="openDialog"
          >
            <v-icon>{{ mdiPencil }}</v-icon>
          </v-btn>
        </span>
      </template>
    </v-tooltip>
    <common-dialog
      v-model="dialog"
      :loading="loading"
      title="Berechtigung bearbeiten"
      max-width="600"
      show-actions
      :icon="mdiPencil"
      show-change-warning
      :check-for-enabled-actions="['STORAGE_CHANGE_CIFS_PERMISSIONS']"
      submit-activated
      @dialog-cancel="close"
      @dialog-confirm="save"
    >
      <v-form>
        <v-row class="mb-4">
          <v-col cols="12">
            {{ selectedAD }}
          </v-col>
        </v-row>

        <v-row>
          <v-col cols="12">
            <v-select
              v-model="localPermission"
              label="Berechtigungen"
              variant="outlined"
              :items="permissionOptions"
              item-title="label"
              item-value="value"
              :rules="[
                rules.notEmptySelectRule(
                  'Eine Berechtigung muss ausgewählt werden'
                ),
              ]"
            />
          </v-col>
        </v-row>
      </v-form>
    </common-dialog>
  </template>
</template>

<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage.ts";

import { mdiPencil } from "@mdi/js";
import { computed, ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const props = defineProps<{
  selectedStorage: UnifiedStorageItem;
  selectedAD: string;
  selectedPermission: string;
}>();
const dialog = ref(false);
const loading = ref(false);
const localPermission = ref(props.selectedPermission);
const rules = useRules(); // Selected

const permissionOptions = [
  { label: "Vollzugriff", value: "full_control" },
  { label: "Changezugriff", value: "change" },
  { label: "Readzugriff", value: "read" },
  { label: "Kein Zugriff", value: "no_access" },
];
const isAllowedShare = computed(
  () =>
    props.selectedStorage.storageCategory == "CIFS_STANDARD_SHARE" ||
    props.selectedStorage.storageCategory == "CIFS_CLONE" ||
    props.selectedStorage.storageCategory == "CIFS_WORM"
);
const canEdit = computed(() => props.selectedStorage.canEdit);
const disabledReason =
  "Bearbeitung nur möglich, wenn genau ein Anwendungsservice zugeordnet ist und Sie berechtigt sind.";

function openDialog() {
  if (!canEdit.value) return;
  localPermission.value = props.selectedPermission;
  dialog.value = true;
}

function close() {
  dialog.value = false;
}
function save() {
  jobService
    .startJob(loading, "STORAGE_CHANGE_CIFS_PERMISSIONS", -1, {
      uuid: props.selectedStorage.uuid,
      ad: props.selectedAD,
      permission: localPermission.value,
    })
    .then(() => {
      close();
    });
}
</script>
