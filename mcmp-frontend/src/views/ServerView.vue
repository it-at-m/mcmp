<template>
  <v-container
    fluid
    class="split-container"
  >
    <div
      class="split-view"
      :class="{ resizing: isResizing }"
    >
      <!-- Linker Bereich (ServerList) -->
      <div
        class="left-panel"
        :style="{ width: leftPanelWidth + 'px' }"
      >
        <server-list
          ref="serverListRef"
          :un-activate-server-row="unActivateServerListRow"
          :selected="selectedServer"
          :url-params-id="route.params.id"
          :initial-search="serverSearch"
          @update:selected="getSelectedServerFromServerListComponent"
          @reset-un-activate-server-row="unActivateServerListRow = false"
          @update:search="serverSearch = $event"
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

      <!-- Rechter Bereich (Detailansicht) -->
      <div class="right-panel">
        <div v-if="notFound">
          <v-row class="pa-4">
            <v-col cols="12">
              <common-alert
                type="warning"
                prominent
              >
                <div class="text-subtitle-1">
                  Der Server existiert nicht (mehr) oder Sie haben keine
                  Berechtigung.
                </div>
                <div class="text-body-2 mt-2">
                  Bitte aktualisieren Sie Ihr Lesezeichen oder wählen Sie einen
                  Server aus der Liste links aus.
                  <span v-if="notFoundId !== null">
                    (ID: {{ notFoundId }})</span
                  >
                </div>
              </common-alert>
            </v-col>
          </v-row>
        </div>
        <div v-else-if="selectedServer.length === 0 && hasAtLeastOneServer" />
        <div
          v-else-if="selectedServer.length > 0"
          class="right-panel-inner"
        >
          <div class="right-panel-sticky">
            <selected-server-actions-and-status
              v-if="selectedServerItem && selectedServerItem.id"
              :selected-server="selectedServerItem"
              :loading-server-details="loadingDetails"
              @change="getSelectedServer"
              @navigate-to-history="navigateToHistory"
              @navigate-to-patchnight="navigateToPatchnight"
            />
            <v-row>
              <v-col
                v-if="selectedServerItem"
                class="d-flex align-center"
              >
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
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="Allgemeines"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiHome }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiHome }}</v-icon
                      >
                      <span>Allgemeines</span>
                    </template>
                  </v-tab>

                  <v-tab
                    value="Patchnight"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="Patchnight"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiCogOutline }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiCogOutline }}</v-icon
                      >
                      <span>Patchnight</span>
                    </template>
                  </v-tab>

                  <v-tab
                    value="Festplatten"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="Storage"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiHarddisk }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiHarddisk }}</v-icon
                      >
                      <span>Storage</span>
                    </template>
                  </v-tab>

                  <v-tab
                    value="Backup"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="Data Protection"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiDatabase }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiDatabase }}</v-icon
                      >
                      <span>Data Protection</span>
                    </template>
                  </v-tab>

                  <v-tab
                    value="Netzwerk"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="Netzwerk"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiLan }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiLan }}</v-icon
                      >
                      <span>Netzwerk</span>
                    </template>
                  </v-tab>

                  <v-tab
                    v-if="showReposTab"
                    value="Repos"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="Repos"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiPackageVariant }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiPackageVariant }}</v-icon
                      >
                      <span>Repos</span>
                    </template>
                  </v-tab>

                  <v-tab
                    value="History"
                    rounded="lg"
                    class="d-flex justify-center align-center"
                  >
                    <v-tooltip
                      v-if="!showTabText"
                      location="bottom"
                      text="History"
                    >
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          size="x-large"
                          >{{ mdiHistory }}</v-icon
                        >
                      </template>
                    </v-tooltip>

                    <template v-else>
                      <v-icon
                        size="x-large"
                        class="me-2"
                        >{{ mdiHistory }}</v-icon
                      >
                      <span>History</span>
                    </template>
                  </v-tab>

                  <v-spacer />

                  <v-tooltip text="zusätzliche technische Informationen">
                    <template #activator="{ props }">
                      <v-tab
                        value="Expert"
                        v-bind="props"
                        aria-label="zusätzliche technische Informationen"
                        rounded="xl"
                      >
                        <template #prepend>
                          <v-icon size="x-large"
                            >{{ mdiInformationSlabCircleOutline }}
                          </v-icon>
                        </template>
                      </v-tab>
                    </template>
                  </v-tooltip>
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
              <v-col v-if="selectedServer.length > 0">
                <v-tabs-window v-model="tab">
                  <v-tabs-window-item value="Allgemeines">
                    <server-details-allgemein
                      :selected-server="selectedServerItem"
                      @changed="
                        () =>
                          getSelectedServerFromServerListComponent(
                            selectedServer
                          )
                      "
                    />
                  </v-tabs-window-item>

                  <v-tabs-window-item value="Patchnight">
                    <server-details-patchnight
                      :selected-server="selectedServerItem"
                      @changed="
                        () =>
                          getSelectedServerFromServerListComponent(
                            selectedServer
                          )
                      "
                    />
                  </v-tabs-window-item>

                  <v-tabs-window-item value="Festplatten">
                    <server-details-festplatten
                      :selected-server="selectedServerItem"
                      :disks="disks"
                      :mount-points="mountPoints"
                      :share-mount-points="shareMountPoints"
                      :loading="[
                        loadingDisks,
                        loadingMountPoints,
                        loadingShareMountPoints,
                      ]"
                      :snapshots="snapshots"
                    />
                  </v-tabs-window-item>

                  <v-tabs-window-item value="Backup">
                    <server-details-backup
                      :selected-server="selectedServerItem"
                      :snapshots="snapshots"
                      :backups="backups"
                      :loading="[loadingSnapshots, loadingBackups]"
                      @changed="
                        () =>
                          getSelectedServerFromServerListComponent(
                            selectedServer
                          )
                      "
                    />
                  </v-tabs-window-item>

                  <v-tabs-window-item
                    v-if="showReposTab"
                    value="Repos"
                  >
                    <server-details-repos
                      :repos="repos"
                      :loading="loadingRepos"
                    />
                  </v-tabs-window-item>

                  <v-tabs-window-item value="Netzwerk">
                    <server-details-netzwerk
                      :nics="nics"
                      :loading="loadingNics"
                      :lb-memberships="lbMemberships"
                      :loading-lb-memberships="loadingLbMemberships"
                    />
                  </v-tabs-window-item>

                  <v-tabs-window-item value="History">
                    <server-details-history
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

                  <v-tabs-window-item value="Expert">
                    <server-details-expert
                      :selected-server="selectedServerItem"
                    />
                  </v-tabs-window-item>
                </v-tabs-window>
              </v-col>
            </v-row>
          </div>
        </div>
      </div>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import type Backup from "@/types/Backup";
import type Disk from "@/types/Disk";
import type JobList from "@/types/JobList";
import type { LbServerMembership } from "@/types/LbServerMembership";
import type MountPoint from "@/types/MountPoint";
import type Nic from "@/types/Nic";
import type Repository from "@/types/Repository";
import type Snapshot from "@/types/Snapshot";
import type { UnifiedStorageMountItem } from "@/types/UnifiedStorageMountItem.ts";

import {
  mdiCogOutline,
  mdiDatabase,
  mdiHarddisk,
  mdiHistory,
  mdiHome,
  mdiInformationSlabCircleOutline,
  mdiLan,
  mdiPackageVariant,
} from "@mdi/js";
import { computed, onMounted, onUnmounted, provide, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useDisplay } from "vuetify";

import backupService from "@/api/backupService";
import diskService from "@/api/diskService";
import jobService from "@/api/jobService";
import loadbalancerService from "@/api/loadbalancerService";
import mountPointService from "@/api/mountPointService";
import nicService from "@/api/nicService";
import repositoryService from "@/api/repositoryService";
import serverService from "@/api/serverService";
import snapshotService from "@/api/snapshotService";
import StorageService from "@/api/storageService.ts";
import CollapseAllCardsButton from "@/components/common/CollapseAllCardsButton.vue";
import CommonAlert from "@/components/common/CommonAlert.vue";
import SelectedServerActionsAndStatus from "@/components/Server/SelectedServerActionsAndStatus.vue";
import ServerDetailsAllgemein from "@/components/Server/ServerDetailsAllgemein.vue";
import ServerDetailsBackup from "@/components/Server/ServerDetailsBackup.vue";
import ServerDetailsExpert from "@/components/Server/ServerDetailsExpert.vue";
import ServerDetailsFestplatten from "@/components/Server/ServerDetailsFestplatten.vue";
import ServerDetailsHistory from "@/components/Server/ServerDetailsHistory.vue";
import ServerDetailsNetzwerk from "@/components/Server/ServerDetailsNetzwerk.vue";
import ServerDetailsPatchnight from "@/components/Server/ServerDetailsPatchnight.vue";
import ServerDetailsRepos from "@/components/Server/ServerDetailsRepos.vue";
import ServerList from "@/components/Server/ServerList.vue";
import { useCollapsibleCards } from "@/composables/useCollapsibleCards";
import { useScrollRestoration } from "@/composables/useScrollRestoration";
import { useTabQuerySync } from "@/composables/useTabQuerySync";
import Server from "@/types/Server";

type HttpError = Error & { status?: number };
const notFound = ref(false);
const notFoundId = ref<number | null>(null);

const { width } = useDisplay();
const showTabText = computed(() => width.value >= 1500);

const selectedServer = ref<Server[]>([]);
const selectedServerItem = computed(() => selectedServer.value[0] ?? null);
const showReposTab = ref(false);
const tab = ref("Allgemeines");
const serverSearch = ref("");
const loadingDetails = ref(true);
const scrollContainer = ref<HTMLElement | null>(null);

const { allCardsExpanded, toggleAllCards } = useCollapsibleCards(tab);
useTabQuerySync(tab);
useTabQuerySync(serverSearch, "search");
useScrollRestoration(scrollContainer);

const disks = ref<Disk[]>([]);
const loadingDisks = ref(true);
const mountPoints = ref<MountPoint[]>([]);
const loadingMountPoints = ref(true);
const shareMountPoints = ref<UnifiedStorageMountItem[]>([]);
const loadingShareMountPoints = ref(true);
const snapshots = ref<Snapshot[]>([]);
const loadingSnapshots = ref(true);
const nics = ref<Nic[]>([]);
const loadingNics = ref(true);
const repos = ref<Repository[]>([]);
const loadingRepos = ref(true);
const lbMemberships = ref<LbServerMembership[]>([]);
const loadingLbMemberships = ref(true);
const backups = ref<Backup[]>([]);
const loadingBackups = ref(true);
const history = ref<import("@/types/Page").Page<JobList> | null>(null);
const loadingHistory = ref(true);
const unActivateServerListRow = ref(false);
const hasAtLeastOneServer = ref(true);
const refreshInterval = ref<ReturnType<typeof setInterval> | null>(null);
const serverListRef = ref<InstanceType<typeof ServerList> | null>(null);
const hasOpenDialog = ref(false);
const route = useRoute();
const router = useRouter();

// Split Handle Logic
const leftPanelWidth = ref(400); // Start width in pixels
const isResizing = ref(false);
const minWidthPercent = 0.07; // Minimum width in % of window width
const maxWidthPercent = 0.35; // Maximum width in % of window width

const currentPage = ref(1);
const currentItemsPerPage = ref(10);
const currentSortBy = ref<string | null>(null);
const currentSortDesc = ref(false);

const emit = defineEmits<(e: "getNotification") => void>();

provide("registerOpenDialog", registerOpenDialog);
provide("unregisterOpenDialog", unregisterOpenDialog);

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

  const clientX = "touches" in event ? event.touches[0].clientX : event.clientX;
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

function handlePageUpdate(page: number) {
  currentPage.value = page;
  fetchHistory();
}

function handleItemsPerPageUpdate(items: number) {
  currentItemsPerPage.value = items;
  currentPage.value = 1;
  fetchHistory();
}

function onSort(sort: { by: string; desc: boolean }) {
  currentSortBy.value = sort.by;
  currentSortDesc.value = sort.desc;
  fetchHistory();
}

onMounted(() => {
  startAutoRefresh();
});

onUnmounted(() => {
  // Cleanup event listeners on unmount
  document.removeEventListener("mousemove", handleResize);
  document.removeEventListener("mouseup", stopResize);
  document.removeEventListener("touchmove", handleResize);
  document.removeEventListener("touchend", stopResize);
  stopAutoRefresh();
});

function getSelectedServerFromServerListComponent(newSelected: Server[]) {
  selectedServer.value = newSelected;
  notFound.value = false;
  notFoundId.value = null;
  if (selectedServer.value.length === 0) {
    hasAtLeastOneServer.value = false;
    stopAutoRefresh();
    return;
  }
  getSelectedServer();
  startAutoRefresh();
}

function getSelectedServer(silent = false) {
  const loadingRef = silent ? ref(false) : loadingDetails;
  const currentId = selectedServerItem.value?.id;

  if (!currentId) return;
  const serverId = currentId!;

  serverService
    .getServerById(loadingRef, serverId)
    .then((res: Server) => {
      notFound.value = false;
      notFoundId.value = null;

      selectedServer.value = [res];
      const targetPath = `/server/${String(serverId ?? "")}`;
      if (route.path !== targetPath) {
        router.push({ path: targetPath, query: route.query });
      }

      // Tab-Daten nur laden, wenn Server wirklich geladen wurde
      loadTabData(tab.value, silent);
    })
    .catch((err: HttpError) => {
      if (serverId !== Number(route.params.id)) {
        return;
      }
      if (err?.status === 404) {
        notFound.value = true;
        notFoundId.value = serverId;

        // Optional: alte Details entfernen, damit nichts "stale" stehen bleibt
        selectedServer.value.splice(0, selectedServer.value.length);

        // Optional: Auto-Refresh stoppen (sonst kommt der 404 zyklisch wieder)
        stopAutoRefresh();
        return;
      }

      // andere Fehler dürfen weiterhin "global" behandelt werden (oder du machst hier eigene UI)
      throw err;
    });
}

function loadTabData(tabName: string, silent = false) {
  if (tabName === "Festplatten") {
    fetchDisks(silent);
    fetchMountPoints(silent);
    fetchShareMountPoints(silent);
    fetchSnapshots(silent);
  } else if (tabName === "Backup") {
    fetchSnapshots(silent);
    fetchBackups(silent);
  } else if (tabName === "Netzwerk") {
    fetchNics(silent);
    fetchLbMemberships(silent);
  } else if (tabName === "Repos") {
    fetchRepos(silent);
  } else if (tabName === "History") {
    fetchHistory(silent);
  }
}

function fetchDisks(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingDisks;
    return diskService.getDisksByServerId(loadingRef, serverId).then((res) => {
      disks.value = res;
    });
  } else {
    disks.value = [];
    return Promise.resolve();
  }
}

function fetchMountPoints(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingMountPoints;
    return mountPointService
      .getMountPointsByServerId(loadingRef, serverId)
      .then((res) => {
        mountPoints.value = res;
      });
  } else {
    mountPoints.value = [];
    return Promise.resolve();
  }
}

function fetchShareMountPoints(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingShareMountPoints;
    return StorageService.getUnifiedStorageMountItemsForServer(
      loadingRef,
      serverId
    ).then((res) => {
      shareMountPoints.value = res;
    });
  } else {
    shareMountPoints.value = [];
    return Promise.resolve();
  }
}

function fetchSnapshots(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingSnapshots;
    return snapshotService
      .getSnapshotsByServerId(loadingRef, serverId)
      .then((res) => {
        snapshots.value = res;
      });
  } else {
    snapshots.value = [];
    return Promise.resolve();
  }
}

function fetchNics(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingNics;
    return nicService.getNicsByServerId(loadingRef, serverId).then((res) => {
      nics.value = res;
    });
  } else {
    nics.value = [];
    return Promise.resolve();
  }
}

function fetchRepos(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingRepos;
    return repositoryService
      .getRepositoriesByServerId(loadingRef, serverId)
      .then((res) => {
        repos.value = res;
      });
  } else {
    repos.value = [];
    return Promise.resolve();
  }
}

function fetchLbMemberships(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingLbMemberships;
    return loadbalancerService
      .getPoolMembershipsByServerId(loadingRef, serverId)
      .then((res) => {
        lbMemberships.value = res;
      });
  } else {
    lbMemberships.value = [];
    return Promise.resolve();
  }
}

function fetchBackups(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingBackups;
    return backupService
      .getBackupsByServerId(loadingRef, serverId)
      .then((res) => {
        backups.value = res;
      });
  } else {
    backups.value = [];
    return Promise.resolve();
  }
}

function fetchHistory(silent = false) {
  const serverId = selectedServerItem.value?.id;
  if (serverId) {
    const loadingRef = silent ? ref(false) : loadingHistory;
    return jobService
      .getJobsByServerId(
        loadingRef,
        serverId,
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

function registerOpenDialog() {
  hasOpenDialog.value = true;
}

function unregisterOpenDialog() {
  hasOpenDialog.value = false;
}

function refreshServerData() {
  if (selectedServer.value.length > 0 && !hasOpenDialog.value) {
    getSelectedServer(true); // true = silent mode without loading spinner
  }
}

function startAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
  }

  // Start new interval (60 seconds = 60000ms)
  refreshInterval.value = setInterval(() => {
    refreshServerData();
    emit("getNotification");
  }, 60000);
}

function stopAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
    refreshInterval.value = null;
  }
}

function navigateToHistory() {
  tab.value = "History";
}

function navigateToPatchnight() {
  tab.value = "PatchnightStatus";
}

watch(
  () => route.params.id,
  (newId, oldId) => {
    if (newId === oldId) return;

    // Verhindere Reload wenn die neue ID bereits der aktuell geladene Server ist
    if (
      selectedServer.value.length > 0 &&
      selectedServer.value[0].id === Number(newId)
    ) {
      return;
    }

    notFound.value = false;
    notFoundId.value = null;
    // When route ID changes, load the corresponding server
    const id = Number(route.params.id);
    if (!isNaN(id)) {
      selectedServer.value.splice(0, selectedServer.value.length, {
        id,
      } as Server);
      unActivateServerListRow.value = true;
      getSelectedServer();
    }
  }
);

watch(
  selectedServerItem,
  (newVal) => {
    // Skip while only the lightweight list item is loaded (managed/roleLinux
    // aren't known yet) so the tab doesn't flicker away and back while the
    // full server details are still loading.
    if (newVal?.managed === undefined || newVal?.roleLinux === undefined) {
      return;
    }
    showReposTab.value = !!(newVal.managed && newVal.roleLinux);
    if (tab.value === "Repos" && !showReposTab.value) {
      tab.value = "Allgemeines";
    }
  },
  { immediate: true }
);

watch(tab, (newTab) => {
  // Load data when tab changes
  if (selectedServer.value.length > 0 && selectedServer.value[0]?.id) {
    loadTabData(newTab);
    // Reset pagination when switching to History tab
    if (newTab === "History") {
      currentPage.value = 1;
      currentItemsPerPage.value = 10;
    }
  }
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
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
