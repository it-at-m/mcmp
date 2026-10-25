<template>
  <common-card title="Server">
    <template #append-title>
      <count-badge :count="servers.length" />
    </template>

    <template #toolbar-actions>
      <!-- Repo actions are only available in test environments for now -->
      <div
        v-if="isTestEnv"
        class="action-buttons"
      >
        <repo-attach-dialog
          :repositories="[repository]"
          :disabled-reason="attachDisabledReason"
          @order-done="loadServers(repository.id)"
        />
        <repo-detach-dialog
          :repositories="[repository]"
          :disabled-reason="detachDisabledReason"
          @order-done="loadServers(repository.id)"
        />
      </div>
    </template>

    <v-data-table
      :headers="headers"
      :items="servers"
      :loading="loading"
      :items-per-page="-1"
      density="compact"
      class="elevation-1"
      hide-default-footer
    >
      <template #no-data>
        <div class="py-4 text-center text-medium-emphasis">
          Das Repository ist an keinen Server angebunden
        </div>
      </template>
      <template #item.name="{ item }">
        <div class="links">
          <router-link :to="`/server/${item.id}`">
            {{ item.name }}
          </router-link>
        </div>
      </template>
      <template #item.powerState="{ item }">
        <power-state-label :power-state="item.powerState" />
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type { RepositoryDetail } from "@/types/RepositoryDetail";
import type { ServerFqdn } from "@/types/ServerFqdn";

import { computed, onUnmounted, ref, watch } from "vue";

import repositoryService from "@/api/repositoryService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import PowerStateLabel from "@/components/common/PowerStateLabel.vue";
import RepoAttachDialog from "@/components/Paketshop/RepoAttachDialog.vue";
import RepoDetachDialog from "@/components/Paketshop/RepoDetachDialog.vue";
import { repoAttachDisabledReason } from "@/composables/repoLockStatus";
import { useTestEnv } from "@/composables/useTestEnv";

const props = defineProps<{
  repository: RepositoryDetail;
}>();

const { isTestEnv } = useTestEnv();
const servers = ref<ServerFqdn[]>([]);
const loading = ref(false);

const headers = [
  { title: "Servername", key: "name" },
  { title: "Status", key: "powerState" },
];

const lockedReason = computed(() =>
  props.repository.lockStatus === "LOCKED"
    ? "Gesperrte Repositories können nicht bearbeitet werden."
    : ""
);

// Attaching is also possible for repositories the user does not own (PAKETSHOP_REPO_ATTACH_NOT_OWNED_REPO),
// unless the repository is SELF_ONLY. The repository must be assigned to exactly one appservice.
const attachDisabledReason = computed(() => {
  if (lockedReason.value) return lockedReason.value;
  if (props.repository.appservices?.length !== 1)
    return "Das Repository muss genau einem Anwendungsservice zugeordnet sein.";
  return repoAttachDisabledReason(props.repository);
});

// Detaching requires edit permission on the repository
const detachDisabledReason = computed(() => {
  if (lockedReason.value) return lockedReason.value;
  if (!props.repository.canEdit)
    return "Keine Berechtigung, dieses Repository zu bearbeiten.";
  if (servers.value.length === 0)
    return "Das Repository ist an keinen Server angebunden.";
  return "";
});

const LOADING_DELAY_MS = 300;
const requestLoading = ref(false);
let loadingTimeout: ReturnType<typeof setTimeout> | null = null;

async function loadServers(repositoryId: number) {
  if (loadingTimeout) clearTimeout(loadingTimeout);
  loadingTimeout = setTimeout(() => {
    servers.value = [];
    loading.value = true;
  }, LOADING_DELAY_MS);
  try {
    const result = await repositoryService.getServersByRepositoryId(
      requestLoading,
      repositoryId
    );
    if (props.repository.id === repositoryId) {
      servers.value = result;
    }
  } finally {
    if (props.repository.id === repositoryId) {
      if (loadingTimeout) clearTimeout(loadingTimeout);
      loadingTimeout = null;
      loading.value = false;
    }
  }
}

onUnmounted(() => {
  if (loadingTimeout) clearTimeout(loadingTimeout);
});

watch(
  () => props.repository.id,
  (repositoryId) => void loadServers(repositoryId),
  { immediate: true }
);
</script>

<style scoped>
.action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
