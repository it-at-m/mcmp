<template>
  <div class="storage-list-container">
    <scrollable-list-table
      ref="tableRef"
      :items="items"
      :total-items="totalItems"
      :loading="loading"
      :headers="headers"
      :sort-by="sortBy"
      :items-per-page="itemsPerPage"
      :has-more="hasMore"
      :selected-id="selectedId"
      item-key="uuid"
      search-label="Storage suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @rowClick="onRowClick"
      @loadMore="onLoadMore"
    >
      <template #[`item.protocol`]="{ item }">
        <v-tooltip>
          <template #activator="{ props }">
            <v-icon
              v-bind="props"
              size="x-large"
              :color="switchColor(item.protocol)"
            >
              {{ switchType(item.protocol) }}
            </v-icon>
          </template>
          {{ item.protocol }}
        </v-tooltip>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";

import {
  mdiBucketOutline,
  mdiFolderNetworkOutline,
  mdiFolderOutline,
  mdiHarddisk,
} from "@mdi/js";
import { computed, nextTick, onMounted, ref, watch } from "vue"; // Removed onUnmounted as observer is in ScrollableListTable

import storageService from "@/api/storageService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";

const props = defineProps<{
  modelValue?: UnifiedStorageItemList[];
  urlParamsId?: string | string[];
}>();

const emit = defineEmits(["update:modelValue", "update:selected"]);

const search = ref("");
const loading = ref(false);
const items = ref<UnifiedStorageItemList[]>([]);
const totalItems = ref(0);
const itemsPerPage = ref(20);
const currentPage = ref(1);
const sortBy = ref<any[]>([{ key: "name", order: "asc" }]);
const selected = ref<UnifiedStorageItemList[]>([]);
const hasMore = ref(true);

const selectedId = computed(() =>
  selected.value.length > 0 ? selected.value[0]?.uuid : null
);

// Keyboard Navigation Refs
const tableRef = ref<any>(null);

const headers = [
  { title: "Typ", key: "protocol", sortable: true },
  { title: "Name", key: "name", sortable: true },
];

function switchType(type: string) {
  switch (type) {
    case "NFS":
      return mdiFolderNetworkOutline;
    case "CIFS":
      return mdiFolderOutline;
    case "S3":
      return mdiBucketOutline;
    default:
      return mdiHarddisk;
  }
}

function switchColor(type: string) {
  switch (type) {
    case "NFS":
      return "#ee0000";
    case "CIFS":
      return "#0078d4";
    case "S3":
      return "orange";
    default:
      return "grey";
  }
}

const normalizedUrlParamId = computed(() =>
  typeof props.urlParamsId === "string" ? props.urlParamsId : undefined
);

function selectItem(item: UnifiedStorageItemList) {
  selected.value = [item];
  emit("update:selected", item);
  emit("update:modelValue", [item]);
}

const loadItems = async (page = 1) => {
  loading.value = true;
  try {
    const sortKey = sortBy.value.length ? sortBy.value[0].key : "name";
    const sortOrder = sortBy.value.length ? sortBy.value[0].order : "asc";

    const sanitizedSearch = (search.value || "")
      .replace(/[^a-zA-Z0-9 .-_]/g, "")
      .trim();

    const response = await storageService.getUnifiedStorage(
      loading,
      page - 1, // API is 0-based? Previous code used page-1.
      itemsPerPage.value,
      sortKey,
      sortOrder,
      sanitizedSearch
    );

    if (page === 1) {
      items.value = response.content;
    } else {
      items.value.push(...response.content);
    }
    totalItems.value = response.page.totalElements;
    hasMore.value = items.value.length < totalItems.value;

    if (selected.value.length === 0 && items.value.length > 0) {
      if (page === 1 && !normalizedUrlParamId.value) {
        // Only select first on initial load when no route-id is provided
        const item = items.value[0];
        if (item) {
          selectItem(item);
        }
      }
    } else if (totalItems.value === 0) {
      selected.value = [];
      emit("update:selected", null);
      emit("update:modelValue", []);
    }
  } catch (e) {
    console.error("Failed to load storage items", e);
  } finally {
    loading.value = false;
  }
};

const updateSortBy = (newSortBy: any[]) => {
  sortBy.value = newSortBy;
  currentPage.value = 1;
  loadItems(1);
  nextTick(() => {
    // Trigger scroll observer reset if needed
    if (tableRef.value) tableRef.value.triggerObserveScroll();
  });
};

const onSearchUpdate = (val: string) => {
  search.value = val;
};

const onRowClick = (item: UnifiedStorageItemList) => {
  // Changed signature to match emitted item
  if (!item) return;
  selectItem(item);
};

// Removed manual focus logic

const onLoadMore = async () => {
  if (!loading.value && hasMore.value) {
    currentPage.value++;
    await loadItems(currentPage.value);
  }
};

// Search Watcher
watch(search, async () => {
  currentPage.value = 1;
  await loadItems(1);
  await nextTick();
  if (tableRef.value) tableRef.value.triggerObserveScroll();
});

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      selected.value = newVal;
    }
  },
  { immediate: true }
);

watch(
  [normalizedUrlParamId, items],
  ([urlId]) => {
    if (!urlId) return;

    const routeMatch = items.value.find((item) => item.uuid === urlId);
    if (routeMatch && selected.value[0]?.uuid !== routeMatch.uuid) {
      selectItem(routeMatch);
    }
  },
  { immediate: true }
);

onMounted(async () => {
  await loadItems(1);
  await nextTick();
  if (tableRef.value) tableRef.value.triggerObserveScroll();
});

// Removed manual IntersectionObserver code and Keyboard Listeners
</script>

<style scoped>
.storage-list-container {
  height: 100%; /* Fill container */
  display: flex;
  flex-direction: column;
}
</style>
