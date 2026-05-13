<template>
  <div class="appservice-list-container">
    <scrollable-list-table
      ref="tableRef"
      :items="appservicesItems"
      :total-items="totalItems"
      :loading="loading"
      :headers="headers"
      :sort-by="sortBy"
      :items-per-page="itemsPerPage"
      :has-more="hasMore"
      :selected-id="selectedId"
      search-label="Anwendungsservice suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @rowClick="onRowClick"
      @loadMore="onLoadMore"
    />
  </div>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList";

import { computed, nextTick, onMounted, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";

const props = defineProps<{
  unActivateAppserviceRow?: boolean;
}>();

const emit = defineEmits(["appservice-selected", "un-activate-appservice-row"]);

const tableRef = ref<any>(null);
const search = ref("");
const appservicesItems = ref<AppserviceList[]>([]);
const loading = ref(false);
const active = ref<number[]>([]);
const totalItems = ref(0);
const hasMore = ref(true);

const headers = [
  { title: "Name", key: "name", sortable: true },
  { title: "ID", key: "id", sortable: true },
];

// Pagination state
const offset = ref(0);
const itemsPerPage = ref(50);
const sortBy = ref<any[]>([{ key: "name", order: "asc" }]);

const selectedId = computed(() =>
  active.value.length > 0 ? active.value[0] : null
);

const loadAppservices = async (reset = false) => {
  loading.value = true;
  try {
    const currentOffset = reset ? 0 : offset.value;
    const sortOrderKey = sortBy.value.length ? sortBy.value[0].order : "asc";

    const result = await appserviceService.getAppservices(
      loading,
      currentOffset,
      itemsPerPage.value,
      sortOrderKey,
      search.value
    );

    const newItems = result.content || []; // Handle potential structure

    if (reset) {
      appservicesItems.value = newItems;
    } else {
      appservicesItems.value.push(...newItems);
    }

    if (result.page && result.page.totalElements) {
      totalItems.value = result.page.totalElements;
    } else {
      totalItems.value = 9999; // Unknown
    }

    hasMore.value = newItems.length === itemsPerPage.value;
  } catch (e) {
    console.error("Failed to load appservices", e);
  } finally {
    loading.value = false;
  }
};

const onRowClick = (appservice: AppserviceList) => {
  active.value = [appservice.id];
  emit("appservice-selected", appservice);
};

const updateSortBy = (newSortBy: any[]) => {
  sortBy.value = newSortBy;
  offset.value = 0;
  loadAppservices(true);
  nextTick(() => {
    if (tableRef.value) tableRef.value.triggerObserveScroll();
  });
};

const onSearchUpdate = (val: string) => {
  search.value = val;
};

const onLoadMore = async () => {
  if (!loading.value && hasMore.value) {
    offset.value += itemsPerPage.value; // Server view likely uses offset or page index?
    // Wait, UnifiedStorageService uses page index (0, 1, 2)
    // AppserviceService uses offset (0, 50, 100).
    // Previous code: offset = ref(0); limit = 50.
    // So I should increment offset by limit.

    await loadAppservices(false);
  }
};

// Search watcher without debounce
watch(search, async () => {
  offset.value = 0;
  await loadAppservices(true);
  await nextTick();
  if (tableRef.value) tableRef.value.triggerObserveScroll();
});

watch(
  () => props.unActivateAppserviceRow,
  (val) => {
    if (val) {
      active.value = [];
      emit("un-activate-appservice-row");
    }
  }
);

onMounted(async () => {
  await loadAppservices(true);
  await nextTick();
  if (tableRef.value) tableRef.value.triggerObserveScroll();
});
</script>

<style scoped>
.appservice-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
