<template>
  <common-card title="Snapshots">
    <template #toolbar-actions>
      <storage-add-snapshot
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
    </v-data-table>
  </common-card>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";
import type { UnifiedStorageSnapshotItem } from "@/types/UnifiedStorageSnapshotItem";

import CommonCard from "@/components/common/CommonCard.vue";
import StorageAddSnapshot from "@/components/Storage/StorageAddSnapshot.vue";
import jobService from "@/api/jobService.ts";
import { ref } from "vue";
import { useSnackbarStore } from "@/stores/snackbar.ts";
import { STATUS_INDICATORS } from "@/constants.ts";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
  snapshots: UnifiedStorageSnapshotItem[];
  loading: boolean;
}>();
const loading = ref(false);

const headers = [
  { title: "Name", key: "name" },
  { title: "Erstellt am", key: "createTime" },
];

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
</script>
