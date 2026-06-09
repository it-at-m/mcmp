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
        <div class="d-flex align-center" style="height:100%">
          <v-btn
            icon
            variant="elevated"
            aria-label="Schließen"
            class="mb-7"
            @click="showBanner = false"
          >
            <v-icon>{{ mdiClose }}</v-icon>
          </v-btn>
        </div>
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
        <storage-list
          :model-value="selectedStorage"
          :url-params-id="route.params.id"
          @update:selected="onStorageSelected"
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

      <!-- Right Panel (Detail View) -->
      <div class="right-panel">
        <div
          v-if="selectedStorageDetail"
          class="right-panel-inner"
        >
          <div class="right-panel-sticky">
            <v-row>
              <v-col>
                <h2 class="ml-2">
                  {{ getTitle() }}
                </h2>
              </v-col>
            </v-row>
            <v-row>
              <v-col>
                <v-tabs
                  v-model="tab"
                  align-tabs="start"
                  slider-color="primary"
                  show-arrows
                  density="compact"
                >
                  <v-tab
                    value="Allgemeines"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                    >Allgemeines
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiHome }}</v-icon>
                    </template></v-tab
                  >
                  <v-tab
                    v-if="berechtigungTabBoolean"
                    value="Berechtigungnen"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                    >Berechtigungnen
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiAccountCog }}</v-icon>
                    </template></v-tab
                  >
                  <v-tab
                    v-if="backupTabBoolean"
                    value="Backup"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    Backup
                    <template #prepend>
                      <v-icon size="x-large">{{ mdiDatabase }}</v-icon>
                    </template>
                  </v-tab>
                </v-tabs>
              </v-col>
            </v-row>
          </div>
          <div class="right-panel-scroll">
            <v-row>
              <v-col>
                <v-tabs-window v-model="tab">
                  <v-tabs-window-item value="Allgemeines">
                    <storage-details-general
                      :selected-storage-item="selectedStorageDetail"
                    />
                  </v-tabs-window-item>
                  <v-tabs-window-item
                    v-if="berechtigungTabBoolean"
                    value="Berechtigungnen"
                  >
                    <storage-details-permissions
                      :selected-storage-item="selectedStorageDetail"
                    />
                  </v-tabs-window-item>
                  <v-tabs-window-item
                    v-if="backupTabBoolean"
                    value="Backup"
                  >
                    <storage-details-backup
                      :selected-storage-item="selectedStorageDetail"
                      :snapshots="snapshots"
                      :loading="loadingSnapshots"
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
        >
          Select a Storage Item to view details.
        </div>
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
import type { UnifiedStorageItem } from "@/types/Storage";
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";
import type { UnifiedStorageSnapshotItem } from "@/types/UnifiedStorageSnapshotItem";

import { mdiAccountCog, mdiDatabase, mdiHome, mdiClose } from "@mdi/js";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import storageService from "@/api/storageService";
import testenvService from "@/api/testenvService.ts";
import commingSoon from "@/assets/commingSoon.png";
import StorageDetailsBackup from "@/components/Storage/StorageDetailsBackup.vue";
import StorageDetailsGeneral from "@/components/Storage/StorageDetailsGeneral.vue";
import StorageDetailsPermissions from "@/components/Storage/StorageDetailsPermissions.vue";
import StorageList from "@/components/Storage/StorageList.vue";

const leftPanelWidth = ref(400);
const isResizing = ref(false);
const minWidthPercent = 0.07; // Minimum width in % of window width
const maxWidthPercent = 0.35; // Maximum width in % of window width

const selectedStorage = ref<UnifiedStorageItemList[]>([]);
const selectedStorageDetail = ref<UnifiedStorageItem | null>(null);
const tab = ref("Allgemeines");
const loadingDetails = ref(false);
const loadingSnapshots = ref(false);
const snapshots = ref<UnifiedStorageSnapshotItem[]>([]);
const testing = ref<boolean>(false); // Only show view in test env
const showBanner = ref(true); // Controls visibility of the test-environment banner
const loadingTestEnv = ref(false);

const route = useRoute();
const router = useRouter();

const berechtigungTabBoolean = computed(() => {
  return (
    selectedStorageDetail.value?.type === "NFS" ||
    selectedStorageDetail.value?.type === "CIFS"
  );
});
const backupTabBoolean = computed(() => {
  const detail = selectedStorageDetail.value;
  if (!detail) return false;
  return (
    (detail.type === "NFS" || detail.type === "CIFS") && detail.isWorm === false
  );
});

const onStorageSelected = (item: UnifiedStorageItemList) => {
  selectedStorage.value = [item];

  const targetPath = `/storage/${item.type}/${item.uuid}`;
  if (route.path !== targetPath) {
    router.push(targetPath);
  }
};

async function syncSelectionFromRoute(
  typeParam: string | string[] | undefined,
  idParam: string | string[] | undefined
) {
  const routeType = typeof typeParam === "string" ? typeParam : undefined;
  const routeId = typeof idParam === "string" ? idParam : undefined;
  const normalizedRouteType = routeType?.toUpperCase();

  if (!routeId || !normalizedRouteType) {
    return;
  }

  if (
    selectedStorage.value[0]?.uuid === routeId &&
    (!normalizedRouteType ||
      selectedStorage.value[0]?.type === normalizedRouteType)
  ) {
    return;
  }

  try {
    const matchedStorage = await storageService.getUnifiedStorageItem(
      loadingDetails,
      normalizedRouteType,
      routeId
    );

    selectedStorageDetail.value = matchedStorage;
    selectedStorage.value = [
      {
        uuid: matchedStorage.uuid,
        name: matchedStorage.name,
        type: matchedStorage.type,
        protocol: matchedStorage.protocol,
      },
    ];
    const canonicalPath = `/storage/${matchedStorage.type}/${matchedStorage.uuid}`;
    if (route.path !== canonicalPath) {
      await router.push(canonicalPath);
    }

    loadTabData(tab.value, true);
  } catch {
    selectedStorage.value = [];
    selectedStorageDetail.value = null;
  }
}

onMounted(() => {
  testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
    testing.value = enabled;
  });

  syncSelectionFromRoute(route.params.type, route.params.id);
});

function getTitle() {
  const detail = selectedStorageDetail.value;
  if (!detail) return "";

  if (detail.protocol === "NFS") {
    return detail.nfs_mount_path;
  } else if (detail.protocol === "CIFS") {
    return detail.cifs_mount_path;
  } else {
    return detail.name;
  }
}

function loadTabData(tabName: string, silent = false) {
  if (tabName === "Backup") {
    fetchSnapshots(silent);
  }
}

function fetchSnapshots(silent = false) {
  if (selectedStorageDetail.value?.uuid && selectedStorageDetail.value?.type) {
    const loadingRef = silent ? ref(false) : loadingSnapshots;
    return storageService
      .getUnifiedStorageSnapshotItems(
        loadingRef,
        selectedStorageDetail.value.type,
        selectedStorageDetail.value.uuid
      )
      .then((res) => {
        snapshots.value = res;
      });
  } else {
    snapshots.value = [];
    return Promise.resolve();
  }
}

watch(tab, (newTab) => {
  if (selectedStorageDetail.value) {
    loadTabData(newTab);
  }
});

watch(selectedStorageDetail, () => {
  if (tab.value === "Berechtigungnen" && !berechtigungTabBoolean.value) {
    tab.value = "Allgemeines";
  }
  if (tab.value === "Backup" && !backupTabBoolean.value) {
    tab.value = "Allgemeines";
  }
});

// Resizing Logic
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
  // Decrease width by 20px, but not below minimum
  const container = document.querySelector(".split-container") as HTMLElement;
  if (!container) return;
  const minWidth = container.offsetWidth * minWidthPercent;
  leftPanelWidth.value = Math.max(leftPanelWidth.value - 20, minWidth);
}

function resizeRight() {
  // Increase width by 20px, but not above maximum
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
  // Cleanup event listeners on unmount
  document.removeEventListener("mousemove", handleResize);
  document.removeEventListener("mouseup", stopResize);
  document.removeEventListener("touchmove", handleResize);
  document.removeEventListener("touchend", stopResize);
});

watch(
  () => selectedStorage.value,
  async (newVal) => {
    if (newVal && newVal.length > 0) {
      const item = newVal[0];
      if (!item) {
        selectedStorageDetail.value = null;
        return;
      }
      if (
        selectedStorageDetail.value?.uuid === item.uuid &&
        selectedStorageDetail.value?.type === item.type
      ) {
        loadTabData(tab.value, true);
        return;
      }
      try {
        selectedStorageDetail.value =
          await storageService.getUnifiedStorageItem(
            loadingDetails,
            item.type,
            item.uuid
          );
        // Load tab data for newly selected item
        loadTabData(tab.value);
      } catch {
        selectedStorageDetail.value = null;
      }
    } else {
      selectedStorageDetail.value = null;
    }
  }
);

watch(
  () => [route.params.type, route.params.id],
  ([newType, newId], [oldType, oldId]) => {
    if (newType === oldType && newId === oldId) return;
    syncSelectionFromRoute(newType, newId);
  }
);
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
