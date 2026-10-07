<template>
  <common-card
    title="Repositories"
    top-margin="0"
    :is-default-expanded="false"
  >
    <template #append-title>
      <count-badge :count="repos.length" />
    </template>

    <template #toolbar-actions>
      <!-- Repo actions are only available in test environments for now -->
      <div
        v-if="isTestEnv && props.selectedAppservice"
        class="action-buttons"
      >
        <repo-attach-dialog
          :repositories="selectedRepos"
          :disabled-reason="attachDisabledReason"
          @order-done="onBatchDone"
        />
        <repo-detach-dialog
          :repositories="selectedRepos"
          :disabled-reason="batchDisabledReason"
          @order-done="onBatchDone"
        />
        <repo-delete-btn
          :repositories="selectedRepos"
          @deleted="onBatchDone"
        />
        <repo-copy-dialog
          :source-options="repos"
          @order-done="loadRepos(props.selectedAppservice)"
        >
          <template #activator="{ props: activatorProps }">
            <v-tooltip
              location="top"
              text="Repository kopieren"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  v-bind="{ ...activatorProps, ...tooltipProps }"
                  icon
                  flat
                  :disabled="
                    !repos.some((r) => r.lockStatus !== 'LOCKED' && r.canEdit)
                  "
                  aria-label="Repository kopieren"
                >
                  <v-icon :icon="mdiContentCopy" />
                </v-btn>
              </template>
            </v-tooltip>
          </template>
        </repo-copy-dialog>
        <repo-create-dialog
          :key="props.selectedAppservice.id"
          :appservice="props.selectedAppservice"
          @order-done="loadRepos(props.selectedAppservice)"
        >
          <template #activator="{ props: activatorProps }">
            <v-tooltip
              location="top"
              text="Repository erstellen"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  v-bind="{ ...activatorProps, ...tooltipProps }"
                  icon
                  flat
                  aria-label="Repository erstellen"
                >
                  <v-icon :icon="mdiPlus" />
                </v-btn>
              </template>
            </v-tooltip>
          </template>
        </repo-create-dialog>
      </div>
    </template>

    <v-data-table
      v-model="selectedRepoIds"
      :headers="headers"
      :items="repos"
      :loading="loading"
      :items-per-page="-1"
      item-value="id"
      density="compact"
      class="elevation-1"
      :show-select="isTestEnv"
      hide-default-footer
    >
      <template #no-data>
        <div class="py-4 text-center text-medium-emphasis">
          Keine Repositories vorhanden
        </div>
      </template>
      <template #item.name="{ item }">
        <div class="links">
          <router-link :to="`/repo/${item.id}`">
            {{ item.name }}
          </router-link>
        </div>
      </template>
      <template #item.lockStatus="{ item }">
        <repo-lock-status-icon :status="item.lockStatus" />
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice";
import type Repository from "@/types/Repository";

import { mdiContentCopy, mdiPlus } from "@mdi/js";
import { computed, ref, watch } from "vue";

import repositoryService from "@/api/repositoryService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import RepoAttachDialog from "@/components/Paketshop/RepoAttachDialog.vue";
import RepoCopyDialog from "@/components/Paketshop/RepoCopyDialog.vue";
import RepoCreateDialog from "@/components/Paketshop/RepoCreateDialog.vue";
import RepoDeleteBtn from "@/components/Paketshop/RepoDeleteBtn.vue";
import RepoDetachDialog from "@/components/Paketshop/RepoDetachDialog.vue";
import RepoLockStatusIcon from "@/components/Paketshop/RepoLockStatusIcon.vue";
import { repoAttachDisabledReason } from "@/composables/repoLockStatus";
import { useTestEnv } from "@/composables/useTestEnv";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const { isTestEnv } = useTestEnv();
const repos = ref<Repository[]>([]);
const loading = ref(false);
const selectedRepoIds = ref<number[]>([]);

const headers = [
  { title: "Name", key: "name" },
  { title: "Status", key: "lockStatus" },
];

const selectedRepos = computed(() =>
  repos.value.filter((r) => selectedRepoIds.value.includes(r.id))
);

const batchDisabledReason = computed(() => {
  if (selectedRepos.value.length === 0) return "Keine Repositories ausgewählt.";
  if (selectedRepos.value.some((r) => r.lockStatus === "LOCKED"))
    return "Gesperrte Repositories können nicht bearbeitet werden.";
  return "";
});

// SELF_ONLY repositories can only be attached by their owners
const attachDisabledReason = computed(
  () =>
    batchDisabledReason.value ||
    selectedRepos.value.map(repoAttachDisabledReason).find((r) => r) ||
    ""
);

function onBatchDone() {
  selectedRepoIds.value = [];
}

async function loadRepos(appservice: Appservice | null) {
  repos.value = [];
  if (!appservice) return;
  const result = await repositoryService.getRepositoriesByAppserviceId(
    loading,
    appservice.id
  );
  // ignore stale responses if the selection changed while loading
  if (props.selectedAppservice?.id === appservice.id) {
    repos.value = result;
  }
}

watch(
  () => props.selectedAppservice,
  (appservice) => {
    selectedRepoIds.value = [];
    void loadRepos(appservice);
  },
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
