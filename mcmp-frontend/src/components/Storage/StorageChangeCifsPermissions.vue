<template>
  <template v-if="isAllowedShare">
    <v-tooltip
      location="bottom"
      text="Berechtigung bearbeiten"
      :open-on-hover="true"
    >
      <template #activator="{ props: tooltipProps }">
        <span v-bind="tooltipProps">
          <v-btn
            icon
            variant="flat"
            aria-label="Berechtigung bearbeiten"
            title="Berechtigung bearbeiten"
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
      :submit-activated="canSubmit"
      :icon="mdiPencil"
      show-change-warning
      :check-for-enabled-actions="['STORAGE_CHANGE_CIFS_PERMISSIONS']"
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
              v-model="selectedPermission"
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
import { computed, ref, watch } from "vue";

import jobService from "@/api/jobService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const props = defineProps<{
  selectedStorage: UnifiedStorageItem;
  selectedAD: string;
}>();
const dialog = ref(false);
const loading = ref(false);
const rules = useRules(); // Selected
const selectedADObject = ref<string>("");
const selectedPermission = ref<string>("");
const searchText = ref(""); // Derive available AD entries from cifs_share_acl_list
const availableAD = computed(
  () => props.selectedStorage.cifs_share_acl_list ?? []
);
watch(selectedADObject, (newAd) => {
  const entry = availableAD.value.find((e) => e.userOrGroup === newAd);
  selectedPermission.value = entry?.permission ?? "";
});
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
const canSubmit = computed(() =>
  Boolean(selectedADObject.value && selectedPermission.value)
);
function openDialog() {
  dialog.value = true;
}
function reset() {
  selectedADObject.value = "";
  selectedPermission.value = "";
  searchText.value = "";
}
function close() {
  reset();
  dialog.value = false;
}
function save() {
  if (!canSubmit.value) return;
  jobService
    .startJob(loading, "STORAGE_CHANGE_CIFS_PERMISSIONS", -1, {
      uuid: props.selectedStorage.uuid,
      ad: selectedADObject.value,
      permission: selectedPermission.value,
    })
    .then(() => {
      close();
    });
}
</script>
