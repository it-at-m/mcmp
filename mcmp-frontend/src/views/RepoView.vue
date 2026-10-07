<template>
  <v-container
    fluid
    class="split-container"
  >
    <div
      class="split-view"
      :class="{ resizing: isResizing }"
    >
      <!-- Left Panel -->
      <div
        class="left-panel"
        :style="{ width: leftPanelWidth + 'px' }"
      >
        <repo-list
          :initial-search="repoSearch"
          @update:selected="onRepositorySelected"
          @update:search="repoSearch = $event"
        />
      </div>

      <!-- Split Handle -->
      <div
        class="split-handle"
        tabindex="0"
        @mousedown="startResize"
        @touchstart="startResize"
        @keyup.left.prevent="resizeLeft"
        @keyup.right.prevent="resizeRight"
      >
        <span class="split-handle-bar left"></span>
        <span class="split-handle-bar right"></span>
      </div>

      <!-- Right Panel -->
      <div class="right-panel">
        <div
          v-if="selectedDetail"
          class="right-panel-inner"
        >
          <div class="right-panel-sticky">
            <detail-page-header
              :appservice-id="selectedDetail.appservices?.[0]?.id ?? null"
              :appservice-name="selectedDetail.appservices?.[0]?.name ?? null"
              :appservice-count="selectedDetail.appservices?.length ?? 0"
              :current-icon="mdiPackageVariant"
              :current-label="selectedDetail.name"
            >
              <template
                v-if="isTestEnv && selectedDetail.canEdit"
                #actions
              >
                <repo-delete-btn :repositories="[selectedDetail]" />
              </template>
              <template #statusChips>
                <appservice-assignment-status-chips
                  :can-edit="selectedDetail.canEdit"
                  :assigned-count="selectedDetail.appservices?.length ?? 0"
                  entity-label="Repository"
                />
              </template>
            </detail-page-header>
            <v-row>
              <v-col class="d-flex align-center">
                <v-tabs
                  v-model="tab"
                  align-tabs="start"
                  slider-color="primary"
                  show-arrows
                  density="compact"
                  class="flex-grow-1"
                >
                  <v-tab
                    value="Allgemeines"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    Allgemeines
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiHome }}</v-icon>
                    </template>
                  </v-tab>
                  <v-tab
                    value="Servers"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    Servers
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiServer }}</v-icon>
                    </template>
                  </v-tab>
                  <v-tab
                    value="History"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    History
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiHistory }}</v-icon>
                    </template>
                  </v-tab>
                </v-tabs>

                <collapse-all-cards-button
                  :expanded="allCardsExpanded"
                  @toggle="toggleAllCards"
                />
              </v-col>
            </v-row>
          </div>
          <div
            ref="scrollContainer"
            class="right-panel-scroll"
            tabindex="-1"
          >
            <v-row>
              <v-col>
                <v-tabs-window v-model="tab">
                  <v-tabs-window-item value="Allgemeines">
                    <repo-details-general :repository="selectedDetail" />
                  </v-tabs-window-item>
                  <v-tabs-window-item value="Servers">
                    <repo-details-servers :repository="selectedDetail" />
                  </v-tabs-window-item>
                  <v-tabs-window-item value="History">
                    <repo-details-history
                      :history="history"
                      :loading="loadingHistory"
                      :page="currentPage"
                      :items-per-page="currentItemsPerPage"
                      @refresh-jobs="fetchHistory"
                      @update:page="handlePageUpdate($event)"
                      @update:items-per-page="handleItemsPerPageUpdate($event)"
                      @update:sort="onSort"
                    />
                  </v-tabs-window-item>
                </v-tabs-window>
              </v-col>
            </v-row>
          </div>
        </div>
        <div
          v-else
          class="d-flex justify-center align-center h-100 text-grey"
        ></div>
      </div>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import type JobList from "@/types/JobList";
import type { Page } from "@/types/Page";
import type Repository from "@/types/Repository.ts";
import type { RepositoryDetail } from "@/types/RepositoryDetail";

import { mdiHistory, mdiHome, mdiPackageVariant, mdiServer } from "@mdi/js";
import { onMounted, onUnmounted, provide, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import jobService from "@/api/jobService";
import repositoryService from "@/api/repositoryService.ts";
import AppserviceAssignmentStatusChips from "@/components/common/AppserviceAssignmentStatusChips.vue";
import CollapseAllCardsButton from "@/components/common/CollapseAllCardsButton.vue";
import DetailPageHeader from "@/components/common/DetailPageHeader.vue";
import RepoDeleteBtn from "@/components/Paketshop/RepoDeleteBtn.vue";
import RepoDetailsGeneral from "@/components/Paketshop/RepoDetailsGeneral.vue";
import RepoDetailsHistory from "@/components/Paketshop/RepoDetailsHistory.vue";
import RepoDetailsServers from "@/components/Paketshop/RepoDetailsServers.vue";
import RepoList from "@/components/Paketshop/RepoList.vue";
import { useCollapsibleCards } from "@/composables/useCollapsibleCards";
import { useScrollRestoration } from "@/composables/useScrollRestoration";
import { useTabQuerySync } from "@/composables/useTabQuerySync";
import { useTestEnv } from "@/composables/useTestEnv";

const leftPanelWidth = ref(400);
const isResizing = ref(false);
const minWidthPercent = 0.07;
const maxWidthPercent = 0.35;

const selectedItems = ref<Repository[]>([]);
const selectedDetail = ref<RepositoryDetail | null>(null);
const tab = ref("Allgemeines");
const repoSearch = ref("");
const loadingDetails = ref(false);
const scrollContainer = ref<HTMLElement | null>(null);

// History State
const history = ref<Page<JobList> | null>(null);
const loadingHistory = ref(false);
const currentPage = ref(1);
const currentItemsPerPage = ref(10);
const currentSortBy = ref<string | null>(null);
const currentSortDesc = ref(false);

const hasOpenDialog = ref(false);
provide("registerOpenDialog", () => {
  hasOpenDialog.value = true;
});
provide("unregisterOpenDialog", () => {
  hasOpenDialog.value = false;
});

const { allCardsExpanded, toggleAllCards } = useCollapsibleCards(tab);
useTabQuerySync(tab);
useTabQuerySync(repoSearch, "search");
useScrollRestoration(scrollContainer);

// Repo actions are only available in test environments for now
const { isTestEnv } = useTestEnv();
const route = useRoute();
const router = useRouter();

function onRepositorySelected(item: Repository | null) {
  if (!item) {
    selectedItems.value = [];
    selectedDetail.value = null;
    return;
  }
  selectedItems.value = [item];
  const targetPath = `/repo/${item.id}`;
  if (route.path !== targetPath) {
    router.push({ path: targetPath, query: route.query });
  }
}

async function syncSelectionFromRoute(idParam: string | string[] | undefined) {
  const id = typeof idParam === "string" ? Number(idParam) : undefined;
  if (!id || isNaN(id)) return;
  if (selectedDetail.value?.id === id) return;
  try {
    const detail = await repositoryService.getRepositoryById(
      loadingDetails,
      id
    );
    // ignore stale responses if the route changed while loading
    if (route.params.id !== String(id)) return;
    selectedDetail.value = detail;
    selectedItems.value = [detail];
  } catch {
    selectedDetail.value = null;
  }
}

watch(
  () => route.params.id,
  (newId, oldId) => {
    if (newId === oldId) return;
    if (!newId) {
      selectedItems.value = [];
      selectedDetail.value = null;
      return;
    }
    syncSelectionFromRoute(newId);
  }
);

onMounted(() => {
  syncSelectionFromRoute(route.params.id);
});

// History API Handler & Pagination
function fetchHistory() {
  const repositoryId = selectedDetail.value?.id;
  if (!repositoryId) {
    history.value = null;
    return Promise.resolve();
  }
  return jobService
    .getJobsByRepositoryId(
      loadingHistory,
      repositoryId,
      currentPage.value,
      currentItemsPerPage.value,
      currentSortBy.value,
      currentSortDesc.value
    )
    .then((res) => {
      history.value = res;
    });
}

function handlePageUpdate(page: number) {
  currentPage.value = page;
  void fetchHistory();
}

function handleItemsPerPageUpdate(items: number) {
  currentItemsPerPage.value = items;
  currentPage.value = 1;
  void fetchHistory();
}

function onSort(sort: { by: string; desc: boolean }) {
  currentSortBy.value = sort.by;
  currentSortDesc.value = sort.desc;
  void fetchHistory();
}

watch(tab, (newTab) => {
  if (selectedDetail.value?.id && newTab === "History") {
    currentPage.value = 1;
    currentItemsPerPage.value = 10;
    void fetchHistory();
  }
});

watch(selectedDetail, () => {
  history.value = null;
  if (tab.value === "History") {
    currentPage.value = 1;
    currentItemsPerPage.value = 10;
    void fetchHistory();
  }
});

// Resize logic
function startResize(event: MouseEvent | TouchEvent) {
  isResizing.value = true;
  document.addEventListener("mousemove", handleResize);
  document.addEventListener("mouseup", stopResize);
  document.addEventListener("touchmove", handleResize);
  document.addEventListener("touchend", stopResize);
  event.preventDefault();
}

function resizeLeft() {
  const container = document.querySelector(".split-container") as HTMLElement;
  if (!container) return;
  leftPanelWidth.value = Math.max(
    leftPanelWidth.value - 20,
    container.offsetWidth * minWidthPercent
  );
}

function resizeRight() {
  const container = document.querySelector(".split-container") as HTMLElement;
  if (!container) return;
  leftPanelWidth.value = Math.min(
    leftPanelWidth.value + 20,
    container.offsetWidth * maxWidthPercent
  );
}

function handleResize(event: MouseEvent | TouchEvent) {
  if (!isResizing.value) return;
  const clientX =
    "touches" in event
      ? (event.touches[0]?.clientX ?? event.changedTouches[0]?.clientX)
      : event.clientX;
  if (clientX === undefined) return;
  const containerRect = (event.target as HTMLElement)
    .closest(".split-container")
    ?.getBoundingClientRect();
  if (containerRect) {
    const minWidth = containerRect.width * minWidthPercent;
    const maxWidth = containerRect.width * maxWidthPercent;
    leftPanelWidth.value = Math.min(
      Math.max(clientX - containerRect.left, minWidth),
      maxWidth
    );
  }
}

function stopResize() {
  isResizing.value = false;
  document.removeEventListener("mousemove", handleResize);
  document.removeEventListener("mouseup", stopResize);
  document.removeEventListener("touchmove", handleResize);
  document.removeEventListener("touchend", stopResize);
}

onUnmounted(() => {
  document.removeEventListener("mousemove", handleResize);
  document.removeEventListener("mouseup", stopResize);
  document.removeEventListener("touchmove", handleResize);
  document.removeEventListener("touchend", stopResize);
});
</script>
