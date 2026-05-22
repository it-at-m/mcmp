<template>
  <common-card title="Snapshots">
    <template #toolbar-actions>
      <storage-add-snapshot
        v-if="canManageSnapshots"
        :selected-storage-item="selectedStorageItem"
        @save="(description: string) => createSnapshot(description)"
      />
    </template>
    <v-data-table
      :items="snapshots"
      :headers="headers"
      :loading="loading"
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
  </common-card>
  <CommonDialog
    :model-value="confirmDeleteDialog"
    max-width="650"
    title="Snapshot löschen"
    :icon="mdiDelete"
    show-actions
    submitActivated
    @dialog-cancel="closeDeleteDialog"
    @dialog-confirm="confirmDeleteSnapshot"
    :check-for-enabled-actions="['STORAGE_DELETE_SNAPSHOT_NFS', 'STORAGE_DELETE_SNAPSHOT_CIFS']"
  >
    Diese Aktion ist endgültig und kann nicht rückgängig gemacht werden.
    Möchten Sie "{{ snapshotToDelete }}" wirklich löschen?
  </CommonDialog>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";
import type { UnifiedStorageSnapshotItem } from "@/types/UnifiedStorageSnapshotItem";

import { mdiDelete } from "@mdi/js";
import { computed, ref } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import StorageAddSnapshot from "@/components/Storage/StorageAddSnapshot.vue";
import jobService from "@/api/jobService.ts";
import { useSnackbarStore } from "@/stores/snackbar.ts";
import { STATUS_INDICATORS } from "@/constants.ts";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
  snapshots: UnifiedStorageSnapshotItem[];
  loading: boolean;
}>();
const loading = ref(false);
const confirmDeleteDialog = ref(false);
const snapshotToDelete = ref<string | null>(null);

const headers: any[] = [
  { title: "Name", key: "name" },
  { title: "Erstellt am", key: "createTime" },
  { title: "Aktion", key: "actions", sortable: false },
];

const NFS_SNAPSHOT_REGEX =
  /^svm[pkc][0-9]{2}dcn\.srv\.muenchen\.de:\/(sn3|sn3c|wn3)_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}$/;
const CIFS_SNAPSHOT_REGEX =
  /^\\\\svm[pkc][0-9]{2}dcc\.srv\.muenchen\.de\\(sc|scc|wc)_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}$/;

const canManageSnapshots = computed(() => {
  if (props.selectedStorageItem.type === "NFS") {
    return NFS_SNAPSHOT_REGEX.test(
      props.selectedStorageItem.nfs_mount_path ?? ""
    );
  }

  if (props.selectedStorageItem.type === "CIFS") {
    return CIFS_SNAPSHOT_REGEX.test(
      props.selectedStorageItem.cifs_mount_path ?? ""
    );
  }

  return false;
});

function createSnapshot(description: string) {
  let identifier
  switch (props.selectedStorageItem.type) {
    case "NFS":
      identifier = "STORAGE_CREATE_SNAPSHOT_NFS";
      break;
    case "CIFS":
      identifier = "STORAGE_CREATE_SNAPSHOT_CIFS";
      break;
    default:
      useSnackbarStore().showMessage({
        message: "Snapshot-Erstellung für diesen Speichertyp nicht unterstützt.",
        level: STATUS_INDICATORS.ERROR
      });
      return;
  }
  jobService.startJob(
    loading,
    identifier,
    -1,
    {
      uuid: props.selectedStorageItem.uuid,
      description: description,
    },
  );
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
        message: "Snapshot-Loeschung fuer diesen Speichertyp nicht unterstuetzt.",
        level: STATUS_INDICATORS.ERROR,
      });
      return;
  }

  jobService.startJob(loading, identifier, -1, {
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
