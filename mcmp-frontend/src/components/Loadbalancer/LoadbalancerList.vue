<template>
  <div class="loadbalancer-list-container">
    <scrollable-list-table
      ref="tableRef"
      :items="tableItems"
      :total-items="totalItems"
      :loading="loading"
      :headers="headers"
      :sort-by="sortBy"
      :items-per-page="itemsPerPage"
      :has-more="hasMore"
      :selected-id="selectedId"
      search-label="Loadbalancer suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @row-click="onRowClick"
      @load-more="onLoadMore"
    />
  </div>
</template>

<script setup lang="ts">
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem.ts";
import type { DataTableHeader } from "vuetify";

import { computed, nextTick, onMounted, ref, watch } from "vue";

import loadbalancerService from "@/api/loadbalancerService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";

interface SortByEntry {
  key: string;
  order: "asc" | "desc";
}

type TableItem = LoadbalancerListItem & { id: number };

const props = defineProps<{
  modelValue?: LoadbalancerListItem[];
  urlParamsId?: string | string[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", selected: LoadbalancerListItem[]): void;
  (e: "update:selected", selected: LoadbalancerListItem | null): void;
}>();

const loading = ref(false);
const items = ref<LoadbalancerListItem[]>([]);
const totalItems = ref(0);
const itemsPerPage = ref(25);
const currentPage = ref(1);
const sortBy = ref<SortByEntry[]>([{ key: "name", order: "asc" }]);
const selected = ref<LoadbalancerListItem[]>([]);
const hasMore = ref(true);
const search = ref("");
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const tableRef = ref<{ triggerObserveScroll: () => void } | null>(null);

const headers = ref<DataTableHeader[]>([
  { title: "Domain", key: "domain", align: "start", sortable: false },
]);

const currentSort = computed<SortByEntry>(
  () => sortBy.value[0] ?? { key: "name", order: "asc" }
);

const selectedId = computed(() =>
  selected.value.length > 0 ? selected.value[0]?.id : null
);

const tableItems = computed<TableItem[]>(() =>
  items.value.map((item) => ({ ...item, id: item.id, domain: item.domain ?? item.name }))
);

const normalizedUrlParamId = computed(() =>
  typeof props.urlParamsId === "string" ? props.urlParamsId : undefined
);

function selectItem(item: LoadbalancerListItem) {
  selected.value = [item];
  emit("update:selected", item);
  emit("update:modelValue", [item]);
}

async function loadItems(page = 1) {
  loading.value = true;
  const offset = (page - 1) * itemsPerPage.value;
  try {
    const sanitizedSearch = (search.value || "")
      .replace(/[^a-zA-Z0-9 ._:/-]/g, "")
      .trim();

    const res = await loadbalancerService.getVisibleLoadbalancers(
      loading,
      offset,
      itemsPerPage.value,
      currentSort.value.key,
      currentSort.value.order,
      sanitizedSearch || undefined
    );

    if (page === 1) {
      items.value = res.content;
    } else {
      items.value.push(...res.content);
    }
    totalItems.value = res.page.totalElements;
    hasMore.value = items.value.length < totalItems.value;

    if (selected.value.length === 0 && items.value.length > 0) {
      if (page === 1 && !normalizedUrlParamId.value) {
        const item = items.value[0];
        if (item) selectItem(item);
      }
    } else if (totalItems.value === 0) {
      selected.value = [];
      emit("update:selected", null);
      emit("update:modelValue", []);
    }
  } catch (e) {
    console.debug("Failed to load loadbalancers", e);
  } finally {
    loading.value = false;
  }
}

function updateSortBy(newSortBy: SortByEntry[]) {
  sortBy.value = newSortBy;
  currentPage.value = 1;
  loadItems(1);
  nextTick(() => tableRef.value?.triggerObserveScroll());
}

function onSearchUpdate(val: string) {
  search.value = val;
}

function onRowClick(item: TableItem) {
  if (!item) return;
  selectItem(item);
}

async function onLoadMore() {
  if (!hasMore.value || loading.value) return;
  currentPage.value++;
  await loadItems(currentPage.value);
  await nextTick();
  tableRef.value?.triggerObserveScroll();
}

watch(search, () => {
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(async () => {
    currentPage.value = 1;
    await loadItems(1);
    await nextTick();
    tableRef.value?.triggerObserveScroll();
  }, 300);
});

onMounted(async () => {
  if (normalizedUrlParamId.value) {
    const id = Number(normalizedUrlParamId.value);
    if (!isNaN(id)) {
      await loadItems(1);
      const match = items.value.find((i) => i.id === id);
      if (match) {
        selectItem(match);
        return;
      }
    }
  }
  await loadItems(1);
});
</script>

<style scoped>
.loadbalancer-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
