<template>
  <common-card title="Snapshots">
    <template #toolbar-actions>
      <storage-change-snapshot-policy
        v-if="canManageSnapshots"
        :selected-storage-item="selectedStorageItem"
        :policies="policies"
        @save="(policy: string) => changeSnapshotPolicy(policy)"
      />
      <storage-add-snapshot
        v-if="canManageSnapshots"
        :selected-storage-item="selectedStorageItem"
        @save="(description: string) => createSnapshot(description)"
      />
    </template>
    <v-row>
      <v-col cols="4">
        <h3>Snapshotpolicy</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="4"
        class="pt-0"
      >
        <p>
          {{ getPolicyTitle(selectedStorageItem.snapshotPolicy) }}
        </p>
      </v-col>
    </v-row>
    <v-row>
    <v-data-table
      :items="snapshots"
      :headers="headers"
      :loading="loading || backupLoading"
      :sort-by="[{ key: 'createTime', order: 'asc' }]"
      density="compact"
    >
      <template #[`item.createTime`]="{ item }">
        {{ new Date(item.createTime).toLocaleString() }}
      </template>
      <template #[`item.actions`]="{ item }">
        <v-tooltip
          v-if="canManageSnapshots"
          location="bottom"
          text="Snapshot löschen"
        >
          <template #activator="{ props: tooltipProps }">
            <v-btn
              v-bind="tooltipProps"
              icon
              variant="flat"
              aria-label="Snapshot löschen"
              @click="requestDeleteSnapshot(item.name)"
            >
              <v-icon>{{ mdiDelete }}</v-icon>
            </v-btn>
          </template>
        </v-tooltip>
      </template>
    </v-data-table>
    </v-row>
  </common-card>
  <common-dialog
    :model-value="confirmDeleteDialog"
    max-width="650"
    title="Snapshot löschen"
    :icon="mdiDelete"
    show-actions
    submit-activated
    :check-for-enabled-actions="[
      'STORAGE_DELETE_SNAPSHOT_NFS',
      'STORAGE_DELETE_SNAPSHOT_CIFS',
    ]"
    @dialog-cancel="closeDeleteDialog"
    @dialog-confirm="confirmDeleteSnapshot"
  >
    Diese Aktion ist endgültig und kann nicht rückgängig gemacht werden. Möchten
    Sie "{{ snapshotToDelete }}" wirklich löschen?
  </common-dialog>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";
import type { UnifiedStorageSnapshotItem } from "@/types/UnifiedStorageSnapshotItem";

import { mdiDelete } from "@mdi/js";
import { computed, ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import StorageAddSnapshot from "@/components/Storage/StorageAddSnapshot.vue";
import StorageChangeSnapshotPolicy from "@/components/Storage/StorageChangeSnapshotPolicy.vue";
import { STATUS_INDICATORS } from "@/constants.ts";
import { useSnackbarStore } from "@/stores/snackbar.ts";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
  snapshots: UnifiedStorageSnapshotItem[];
  loading: boolean;
}>();
const backupLoading = ref(false);
const confirmDeleteDialog = ref(false);
const snapshotToDelete = ref<string | null>(null);

const headers: any[] = [
  { title: "Name", key: "name" },
  { title: "Erstellt am", key: "createTime" },
  { title: "Aktion", key: "actions", sortable: false },
];

const policies = [
  { value: "dcc-6h", title: "Ein Snapshot pro Stunde der letzten 6h" },
  { value: "dcc-24h", title: "Ein Snapshot pro Stunde der letzten 24h" },
  { value: "dcc-24h4d", title: "Ein Snapshot pro Stunde der letzten 24h + 4 Snapshots der letzten 4 Tage um 22 Uhr" },
  { value: "dcc-24h7d", title: "Ein Snapshot pro Stunde der letzten 24h + 7 Snapshots der letzten 7 Tage um 22 Uhr" },
  { value: "none", title: "keine automatischen Snapshots" }
];

function getPolicyTitle(policyValue: string | undefined): string {
  if (!policyValue) return "Unbekannt";
  const policy = policies.find(p => p.value === policyValue);
  return policy ? policy.title : policyValue;
}

const canManageSnapshots = computed(() => {
  return (
    props.selectedStorageItem.storageCategory == "NFS_STANDARD_SHARE" ||
    props.selectedStorageItem.storageCategory == "NFS_CLONE" ||
    props.selectedStorageItem.storageCategory == "NFS_WORM" ||
    props.selectedStorageItem.storageCategory == "CIFS_STANDARD_SHARE" ||
    props.selectedStorageItem.storageCategory == "CIFS_CLONE" ||
    props.selectedStorageItem.storageCategory == "CIFS_WORM"
  );
});

function createSnapshot(description: string) {
  let identifier;
  switch (props.selectedStorageItem.type) {
    case "NFS":
      identifier = "STORAGE_CREATE_SNAPSHOT_NFS";
      break;
    case "CIFS":
      identifier = "STORAGE_CREATE_SNAPSHOT_CIFS";
      break;
    default:
      useSnackbarStore().showMessage({
        message:
          "Snapshot-Erstellung für diesen Speichertyp nicht unterstützt.",
        level: STATUS_INDICATORS.ERROR,
      });
      return;
  }
  jobService.startJob(backupLoading, identifier, -1, {
    uuid: props.selectedStorageItem.uuid,
    description: description,
  });
}

function changeSnapshotPolicy(policy: string) {
  let identifier;
  switch (props.selectedStorageItem.type) {
    case "NFS":
      identifier = "STORAGE_CHANGE_SNAPSHOT_POLICY_NFS";
      break;
    case "CIFS":
      identifier = "STORAGE_CHANGE_SNAPSHOT_POLICY_CIFS";
      break;
    default:
      useSnackbarStore().showMessage({
        message:
          "Snapshot-Policy-Änderung für diesen Speichertyp nicht unterstützt.",
        level: STATUS_INDICATORS.ERROR,
      });
      return;
  }
  jobService.startJob(backupLoading, identifier, -1, {
    uuid: props.selectedStorageItem.uuid,
    newPolicy: policy,
  });
}

function deleteSnapshot(snapshotName: string) {
  let identifier;
  switch (props.selectedStorageItem.type) {
    case "NFS":
      identifier = "STORAGE_DELETE_SNAPSHOT_NFS";
      break;
    case "CIFS":
      identifier = "STORAGE_DELETE_SNAPSHOT_CIFS";
      break;
    default:
      useSnackbarStore().showMessage({
        message:
          "Snapshot-Loeschung fuer diesen Speichertyp nicht unterstuetzt.",
        level: STATUS_INDICATORS.ERROR,
      });
      return;
  }

  jobService.startJob(backupLoading, identifier, -1, {
    uuid: props.selectedStorageItem.uuid,
    snapshotName: snapshotName,
  });
}

function requestDeleteSnapshot(snapshotName: string) {
  snapshotToDelete.value = snapshotName;
  confirmDeleteDialog.value = true;
}

function closeDeleteDialog() {
  confirmDeleteDialog.value = false;
  snapshotToDelete.value = null;
}

function confirmDeleteSnapshot() {
  if (!snapshotToDelete.value) {
    closeDeleteDialog();
    return;
  }

  deleteSnapshot(snapshotToDelete.value);
  closeDeleteDialog();
}
</script>
