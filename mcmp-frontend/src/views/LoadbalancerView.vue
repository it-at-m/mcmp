<template>
  <v-container
    v-if="testing"
    fluid
    class="split-container"
  >
    <v-banner
      v-if="showBanner"
      bg-color="red"
      rounded
    >
      <h2>Diese Ansicht ist nur in der Testumgebung verfügbar.</h2>
      <template #actions>
        <v-btn
          icon
          variant="outlined"
          aria-label="Schließen"
          class="mb-7"
          @click="showBanner = false"
        >
          <v-icon>{{ mdiClose }}</v-icon>
        </v-btn>
      </template>
    </v-banner>

    <div
      class="split-view"
      :class="{ resizing: isResizing }"
    >
      <!-- Left Panel -->
      <div
        class="left-panel"
        :style="{ width: leftPanelWidth + 'px' }"
      >
        <loadbalancer-list
          :model-value="selectedItems"
          :url-params-id="route.params.id"
          @update:selected="onLoadbalancerSelected"
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
            <v-row>
              <v-col>
                <h2 class="ml-2 mt-6">{{ selectedDetail.name }}</h2>
              </v-col>
            </v-row>
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
                    value="Pool"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    Pool
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiCallSplit }}</v-icon>
                    </template>
                  </v-tab>
                  <v-tab
                    value="IRules"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    IRules
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiScriptText }}</v-icon>
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
            class="right-panel-scroll"
            tabindex="-1"
          >
            <v-row>
              <v-col>
                <v-tabs-window v-model="tab">
                  <v-tabs-window-item value="Allgemeines">
                    <loadbalancer-details-general :lb="selectedDetail" />
                  </v-tabs-window-item>
                  <v-tabs-window-item value="Pool">
                    <loadbalancer-details-pool :lb="selectedDetail" />
                  </v-tabs-window-item>
                  <v-tabs-window-item value="IRules">
                    <loadbalancer-details-irules :lb="selectedDetail" />
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
  <v-container v-else>
    <v-row>
      <v-col
        cols="12"
        class="d-flex align-center justify-center"
      >
        <img
          :src="commingSoon"
          alt="Comming Soon"
          height="400"
        />
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem";

import { mdiCallSplit, mdiClose, mdiHome, mdiScriptText } from "@mdi/js";
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import loadbalancerService from "@/api/loadbalancerService";
import testenvService from "@/api/testenvService.ts";
import commingSoon from "@/assets/commingSoon.png";
import CollapseAllCardsButton from "@/components/common/CollapseAllCardsButton.vue";
import LoadbalancerDetailsGeneral from "@/components/Loadbalancer/LoadbalancerDetailsGeneral.vue";
import LoadbalancerDetailsIrules from "@/components/Loadbalancer/LoadbalancerDetailsIrules.vue";
import LoadbalancerDetailsPool from "@/components/Loadbalancer/LoadbalancerDetailsPool.vue";
import LoadbalancerList from "@/components/Loadbalancer/LoadbalancerList.vue";
import { useCollapsibleCards } from "@/composables/useCollapsibleCards";

const leftPanelWidth = ref(400);
const isResizing = ref(false);
const minWidthPercent = 0.07;
const maxWidthPercent = 0.35;

const selectedItems = ref<LoadbalancerListItem[]>([]);
const selectedDetail = ref<LoadbalancerDetail | null>(null);
const tab = ref("Allgemeines");
const loadingDetails = ref(false);

const { allCardsExpanded, toggleAllCards } = useCollapsibleCards(tab);
const testing = ref(false);
const showBanner = ref(true);
const loadingTestEnv = ref(false);

const route = useRoute();
const router = useRouter();

async function onLoadbalancerSelected(item: LoadbalancerListItem | null) {
  if (!item) {
    selectedItems.value = [];
    selectedDetail.value = null;
    return;
  }
  selectedItems.value = [item];
  const targetPath = `/loadbalancer/${item.id}`;
  if (route.path !== targetPath) {
    router.push(targetPath);
  }
}

async function syncSelectionFromRoute(idParam: string | string[] | undefined) {
  const id = typeof idParam === "string" ? Number(idParam) : undefined;
  if (!id || isNaN(id)) return;
  if (selectedDetail.value?.id === id) return;
  try {
    selectedDetail.value = await loadbalancerService.getLoadbalancerById(
      loadingDetails,
      id
    );
    selectedItems.value = [
      {
        id: selectedDetail.value.id,
        name: selectedDetail.value.name,
        listen: selectedDetail.value.listen,
        domain: selectedDetail.value.domains[0] ?? null,
        port: selectedDetail.value.port,
        appserviceName: selectedDetail.value.appservices[0]?.name ?? null,
      },
    ];
  } catch {
    selectedDetail.value = null;
  }
}

watch(selectedItems, async (newVal) => {
  if (!newVal?.length) {
    selectedDetail.value = null;
    return;
  }
  const item = newVal[0];
  if (!item || selectedDetail.value?.id === item.id) return;
  try {
    selectedDetail.value = await loadbalancerService.getLoadbalancerById(
      loadingDetails,
      item.id
    );
  } catch {
    selectedDetail.value = null;
  }
});

watch(
  () => route.params.id,
  (newId, oldId) => {
    if (newId === oldId) return;
    syncSelectionFromRoute(newId);
  }
);

onMounted(() => {
  testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
    testing.value = enabled;
  });
  syncSelectionFromRoute(route.params.id);
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
