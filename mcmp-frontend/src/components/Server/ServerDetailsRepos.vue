<template>
  <common-card title="Repositories">
    <template #toolbar-actions>
      <!-- Repo actions are only available in test environments for now -->
      <repo-attach-dialog
        v-if="isTestEnv && server"
        :server="server"
        :disabled-reason="editDisabledReason"
        @order-done="emit('changed')"
      />
    </template>

    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="repos"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.lockStatus="{ item }">
        <repo-lock-status-icon :status="item.lockStatus" />
      </template>
      <template #item.actions="{ item }">
        <repo-detach-dialog
          v-if="isTestEnv && server"
          :repositories="[item]"
          :server="server"
          :disabled-reason="detachDisabledReason(item)"
          small
          @order-done="emit('changed')"
        />
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository";

import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import RepoAttachDialog from "@/components/Paketshop/RepoAttachDialog.vue";
import RepoDetachDialog from "@/components/Paketshop/RepoDetachDialog.vue";
import RepoLockStatusIcon from "@/components/Paketshop/RepoLockStatusIcon.vue";
import { useTestEnv } from "@/composables/useTestEnv";

const props = defineProps<{
  repos: Repository[];
  loading: boolean;
  server?: {
    id: number;
    name: string;
    fqdn: string;
    canEdit: boolean;
    powerState: string;
    cloud: { cloudType: string };
  } | null;
}>();

const emit = defineEmits<(e: "changed") => void>();

const { isTestEnv } = useTestEnv();

const REPO_CLOUD_TYPES = ["VMWARE", "PROXMOX"];

const editDisabledReason = computed(() => {
  if (props.server && !REPO_CLOUD_TYPES.includes(props.server.cloud.cloudType))
    return "Repositories können nur auf virtuellen Maschinen (VMware, Proxmox) verwaltet werden.";
  if (!props.server?.canEdit)
    return "Keine Bestellberechtigung für diesen Server.";
  if (props.server.powerState !== "poweredOn")
    return "Der Server muss eingeschaltet sein.";
  return "";
});

function detachDisabledReason(repo: Repository): string {
  if (editDisabledReason.value) return editDisabledReason.value;
  if (repo.lockStatus === "LOCKED")
    return "Gesperrte Repositories können nicht bearbeitet werden.";
  if (repo.appserviceCount !== 1)
    return "Das Repository muss genau einem Anwendungsservice zugeordnet sein.";
  return "";
}

const headers = computed(() => [
  { title: "Name", key: "name" },
  { title: "Status", key: "lockStatus" },
  // detach column only where the repo actions are available
  ...(isTestEnv.value
    ? [{ title: "", key: "actions", align: "end" as const, width: 60 }]
    : []),
]);
</script>
