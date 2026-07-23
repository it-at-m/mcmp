<template>
  <v-container
    fluid
    class="split-container"
  >
    <div
      class="split-view"
      :class="{ resizing: isResizing }"
    >
      <div
        class="left-panel"
        :style="{ width: leftPanelWidth + 'px' }"
      >
        <appservice-list
          :selected="selectedAppserviceRows"
          :url-params-id="route.params.appId"
          @update:selected="onAppserviceSelected"
        />
      </div>

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

      <div class="right-panel">
        <div v-if="notFound">
          <v-row class="pa-4">
            <v-col cols="12">
              <common-alert
                type="warning"
                prominent
              >
                <div class="text-subtitle-1">
                  Der Appservice existiert nicht (mehr) oder Sie haben keine
                  Berechtigung.
                </div>
                <div class="text-body-2 mt-2">
                  Bitte aktualisieren Sie Ihr Lesezeichen oder wählen Sie einen
                  Appservice aus der Liste links aus.
                  <span v-if="notFoundId !== null">
                    (ID: {{ notFoundId }})</span
                  >
                </div>
              </common-alert>
            </v-col>
          </v-row>
        </div>
        <div
          v-else-if="selectedAppservice"
          class="right-panel-inner"
        >
          <div class="right-panel-sticky">
            <appservice-status :selected-appservice="selectedAppservice" />
            <div class="d-flex align-center">
              <v-tabs
                v-model="tabAppservices"
                align-tabs="start"
                slider-color="primary"
                class="flex-grow-1"
              >
                <v-tab value="Allgemeines">
                  Allgemeines
                  <template #prepend>
                    <v-icon size="x-large">{{ mdiHome }}</v-icon>
                  </template>
                </v-tab>
                <v-tab value="History">
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
            </div>
          </div>
          <div
            class="right-panel-scroll"
            tabindex="-1"
          >
            <v-tabs-window v-model="tabAppservices">
              <v-tabs-window-item value="Allgemeines">
                <appservice-details-allgemein
                  :selected-appservice="selectedAppservice"
                />
                <appservice-details-server
                  :selected-appservice="selectedAppservice"
                />
                <appservice-details-storage
                  :selected-appservice="selectedAppservice"
                />
                <appservice-details-loadbalancer
                  :selected-appservice="selectedAppservice"
                />
              </v-tabs-window-item>

              <v-tabs-window-item value="History">
                <app-service-detail-history
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
import type Appservice from "@/types/Appservice";
import type AppserviceListItem from "@/types/AppserviceList";
import type JobList from "@/types/JobList";
import type { Page } from "@/types/Page";

import { mdiHistory, mdiHome } from "@mdi/js";
import { onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import appserviceService from "@/api/appserviceService";
import jobService from "@/api/jobService";
import AppserviceDetailsAllgemein from "@/components/Appservice/AppserviceDetailsAllgemein.vue";

import AppserviceDetailsLoadbalancer from "@/components/Appservice/AppserviceDetailsLoadbalancer.vue";
import AppserviceDetailsServer from "@/components/Appservice/AppserviceDetailsServer.vue";
import AppserviceDetailsStorage from "@/components/Appservice/AppserviceDetailsStorage.vue";
import AppServiceDetailHistory from "@/components/Appservice/AppServiceDetailHistory.vue";
import AppserviceList from "@/components/Appservice/AppserviceList.vue";
import AppserviceStatus from "@/components/Appservice/AppserviceStatus.vue";
import CollapseAllCardsButton from "@/components/common/CollapseAllCardsButton.vue";
import CommonAlert from "@/components/common/CommonAlert.vue";
import { useCollapsibleCards } from "@/composables/useCollapsibleCards";

const selectedAppserviceRows = ref<AppserviceListItem[]>([]);
const selectedAppservice = ref<Appservice | null>(null);
const tabAppservices = ref("Allgemeines");
const loadingDetails = ref(false);

// History State
const history = ref<Page<JobList> | null>(null);
const loadingHistory = ref(false);
const currentPage = ref(1);
const currentItemsPerPage = ref(10);
const currentSortBy = ref<string | null>(null);
const currentSortDesc = ref(false);

const { allCardsExpanded, toggleAllCards } =
  useCollapsibleCards(tabAppservices);

const notFound = ref(false);
const notFoundId = ref<number | null>(null);

const route = useRoute();
const router = useRouter();

const leftPanelWidth = ref(400);
const isResizing = ref(false);
const minWidthPercent = 0.07;
const maxWidthPercent = 0.35;

function onAppserviceSelected(rows: AppserviceListItem[]) {
  const id = rows[0]?.id;
  if (!id) {
    void router.push("/appservice");
    return;
  }

  const targetPath = `/appservice/${id}`;
  if (route.path !== targetPath) {
    void router.push(targetPath);
  }
}

async function loadSelectedAppservice(id: number) {
  try {
    const res = await appserviceService.getAppservice(loadingDetails, id);
    if (!res || !res.id) {
      selectedAppservice.value = null;
      notFound.value = true;
      notFoundId.value = id;
      return;
    }

    selectedAppservice.value = res;
    selectedAppserviceRows.value = [
      { id: res.id, name: res.name } as AppserviceListItem,
    ];
    notFound.value = false;
    notFoundId.value = null;

    if (tabAppservices.value === "History") {
      void fetchHistory();
    }
  } catch {
    selectedAppservice.value = null;
    notFound.value = true;
    notFoundId.value = id;
  }
}

async function syncSelectionFromRoute(
  appIdParam: string | string[] | undefined
) {
  const routeAppId = typeof appIdParam === "string" ? Number(appIdParam) : NaN;
  if (Number.isNaN(routeAppId)) {
    selectedAppservice.value = null;
    selectedAppserviceRows.value = [];
    notFound.value = false;
    notFoundId.value = null;
    return;
  }

  notFound.value = false;
  notFoundId.value = null;

  if (selectedAppservice.value?.id === routeAppId) {
    selectedAppserviceRows.value = [
      {
        id: selectedAppservice.value.id,
        name: selectedAppservice.value.name,
      } as AppserviceListItem,
    ];
    return;
  }

  selectedAppserviceRows.value = [{ id: routeAppId } as AppserviceListItem];
  await loadSelectedAppservice(routeAppId);
}

// History API Handler & Pagination
function fetchHistory(silent = false) {
  const appId = selectedAppservice.value?.id;
  if (appId) {
    const loadingRef = silent ? ref(false) : loadingHistory;
    return jobService
      .getJobsByAppServiceId(
        loadingRef,
        appId,
        currentPage.value,
        currentItemsPerPage.value,
        currentSortBy.value,
        currentSortDesc.value
      )
      .then((res) => {
        history.value = res;
      });
  } else {
    history.value = null;
    return Promise.resolve();
  }
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

function startResize(event: MouseEvent | TouchEvent) {
  isResizing.value = true;
  const splitView = document.querySelector(".split-view");
  splitView?.classList.add("resizing");
  document.addEventListener("mousemove", handleResize);
  document.addEventListener("mouseup", stopResize);
  document.addEventListener("touchmove", handleResize);
  document.addEventListener("touchend", stopResize);
  event.preventDefault();
}

function resizeLeft() {
  const container = document.querySelector(".split-container") as HTMLElement;
  if (!container) return;
  const minWidth = container.offsetWidth * minWidthPercent;
  leftPanelWidth.value = Math.max(leftPanelWidth.value - 20, minWidth);
}

function resizeRight() {
  const container = document.querySelector(".split-container") as HTMLElement;
  if (!container) return;
  const maxWidth = container.offsetWidth * maxWidthPercent;
  leftPanelWidth.value = Math.min(leftPanelWidth.value + 20, maxWidth);
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
    const containerWidth = containerRect.width;
    const minWidth = containerWidth * minWidthPercent;
    const maxWidth = containerWidth * maxWidthPercent;
    const newWidth = clientX - containerRect.left;
    leftPanelWidth.value = Math.min(Math.max(newWidth, minWidth), maxWidth);
  }
}

function stopResize() {
  isResizing.value = false;
  const splitView = document.querySelector(".split-view");
  splitView?.classList.remove("resizing");
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

watch(
  () => route.params.appId,
  (newId) => {
    void syncSelectionFromRoute(newId);
  },
  { immediate: true }
);

watch(tabAppservices, (newTab) => {
  if (selectedAppservice.value?.id && newTab === "History") {
    currentPage.value = 1;
    currentItemsPerPage.value = 10;
    void fetchHistory();
  }
});
</script>

<style scoped>
.split-container {
  height: calc(100dvh - var(--v-layout-top));
  min-height: 0;
  padding: 0;
  margin: 0;
  overflow: hidden;
}

.split-view {
  display: flex;
  height: 100%;
  min-height: 0;
  width: 100%;
  overflow: hidden;
}

.left-panel {
  flex-shrink: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
  /* noinspection CssUnresolvedCustomProperty */
  background-color: var(--v-theme-surface);
  /* noinspection CssUnresolvedCustomProperty */
  border-right: 1px solid var(--v-theme-on-surface-variant);
  min-width: 200px;
  max-width: 800px;
}

.right-panel {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  /* noinspection CssUnresolvedCustomProperty */
  background-color: var(--v-theme-surface);
}

.right-panel-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.right-panel-sticky {
  flex-shrink: 0;
}

.right-panel-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.split-handle {
  width: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: col-resize;
  user-select: none;
  border: 0;
  z-index: 10;
  opacity: 0.8;
  margin-left: 0.2em;
  margin-right: 0.2em;
}

.split-handle-bar {
  position: absolute;
  top: 50%;
  width: 2px;
  height: 2%;
  /* noinspection CssUnresolvedCustomProperty */
  background: rgb(var(--v-theme-text));
  opacity: 0.8;
  border-radius: 24px;
  z-index: 1;
}

.split-handle-bar.left {
  left: 2px;
}

.split-handle-bar.right {
  right: 2px;
}

.split-handle::after {
  content: "";
  position: absolute;
  left: 50%;
  top: 15%;
  width: 2px;
  height: 70%;
  /* noinspection CssUnresolvedCustomProperty */
  background: rgb(var(--v-theme-text));
  transform: translateX(-50%);
  z-index: 0;
  border-radius: 1px;
  opacity: 0.6;
}

@media print {
  .left-panel,
  .split-handle {
    display: none !important;
  }

  .right-panel {
    width: 100% !important;
    height: auto !important;
    overflow: visible !important;
  }

  .split-container,
  .split-view {
    display: block !important;
    height: auto !important;
    overflow: visible !important;
  }
}

@media (max-width: 768px) {
  .split-handle {
    width: 18px;

    &:hover {
      width: 22px;
    }

    &:active {
      width: 26px !important;
    }
  }

  .split-view.resizing .split-handle {
    width: 30px !important;
  }
}

@media (hover: none) {
  .split-handle {
    width: 20px;
    background: linear-gradient(
      to right,
      #f0f0f0 0%,
      #d8d8d8 50%,
      #f0f0f0 100%
    );
    border-color: #999;

    &::before {
      color: #555;
    }

    &::after {
      background: #666;
      width: 2px;
      opacity: 0.8;
    }
  }
}
</style>