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
          :disabled-reason="
            editDisabledReason ||
            (item.lockStatus === 'LOCKED'
              ? 'Gesperrte Repositories können nicht entfernt werden.'
              : '')
          "
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
  server?: { name: string; fqdn: string; canEdit: boolean } | null;
}>();

const emit = defineEmits<(e: "changed") => void>();

const { isTestEnv } = useTestEnv();

const editDisabledReason = computed(() =>
  props.server?.canEdit ? "" : "Keine Bestellberechtigung für diesen Server."
);

const headers = computed(() => [
  { title: "Name", key: "name" },
  { title: "Status", key: "lockStatus" },
  // detach column only where the repo actions are available
  ...(isTestEnv.value
    ? [{ title: "", key: "actions", align: "end" as const, width: 60 }]
    : []),
]);
</script>
