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
        <openshift-namespace-list
          :model-value="selectedItems"
          :initial-search="openshiftSearch"
          @update:selected="onNamespaceSelected"
          @update:search="openshiftSearch = $event"
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
              :current-icon="mdiKubernetes"
              :current-label="selectedDetail.name"
            >
              <template #statusChips>
                <appservice-assignment-status-chips
                  :can-edit="selectedDetail.canEdit"
                  :assigned-count="selectedDetail.appservices?.length ?? 0"
                  entity-label="Namespace"
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
                    <openshift-namespace-details-general
                      :namespace="selectedDetail"
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
import type { OpenshiftNamespaceDetail } from "@/types/OpenshiftNamespaceDetail";
import type { OpenshiftNamespaceListItem } from "@/types/OpenshiftNamespaceListItem";

import { mdiHome, mdiKubernetes } from "@mdi/js";
import { onMounted, onUnmounted, provide, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";

import openshiftService from "@/api/openshiftService";
import AppserviceAssignmentStatusChips from "@/components/common/AppserviceAssignmentStatusChips.vue";
import CollapseAllCardsButton from "@/components/common/CollapseAllCardsButton.vue";
import DetailPageHeader from "@/components/common/DetailPageHeader.vue";
import OpenshiftNamespaceDetailsGeneral from "@/components/Openshift/OpenshiftNamespaceDetailsGeneral.vue";
import OpenshiftNamespaceList from "@/components/Openshift/OpenshiftNamespaceList.vue";
import { useCollapsibleCards } from "@/composables/useCollapsibleCards";
import { useScrollRestoration } from "@/composables/useScrollRestoration";
import { useTabQuerySync } from "@/composables/useTabQuerySync";

const leftPanelWidth = ref(400);
const isResizing = ref(false);
const minWidthPercent = 0.07;
const maxWidthPercent = 0.35;

const selectedItems = ref<OpenshiftNamespaceListItem[]>([]);
const selectedDetail = ref<OpenshiftNamespaceDetail | null>(null);
const tab = ref("Allgemeines");
const openshiftSearch = ref("");
const loadingDetails = ref(false);
const scrollContainer = ref<HTMLElement | null>(null);

const hasOpenDialog = ref(false);
provide("registerOpenDialog", () => {
  hasOpenDialog.value = true;
});
provide("unregisterOpenDialog", () => {
  hasOpenDialog.value = false;
});

const { allCardsExpanded, toggleAllCards } = useCollapsibleCards(tab);
useTabQuerySync(tab);
useTabQuerySync(openshiftSearch, "search");
useScrollRestoration(scrollContainer);

const route = useRoute();
const router = useRouter();

async function onNamespaceSelected(item: OpenshiftNamespaceListItem | null) {
  if (!item) {
    selectedItems.value = [];
    selectedDetail.value = null;
    return;
  }
  selectedItems.value = [item];
  const targetPath = `/openshift/${item.id}`;
  if (route.path !== targetPath) {
    router.push({ path: targetPath, query: route.query });
  }
}

async function syncSelectionFromRoute(idParam: string | string[] | undefined) {
  const id = typeof idParam === "string" ? Number(idParam) : undefined;
  if (!id || isNaN(id)) return;
  if (selectedDetail.value?.id === id) return;
  try {
    selectedDetail.value = await openshiftService.getNamespaceById(
      loadingDetails,
      id
    );
    selectedItems.value = [
      {
        id: selectedDetail.value.id,
        name: selectedDetail.value.name,
        clusterName: selectedDetail.value.clusterName,
        environment: selectedDetail.value.environment,
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
    selectedDetail.value = await openshiftService.getNamespaceById(
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
