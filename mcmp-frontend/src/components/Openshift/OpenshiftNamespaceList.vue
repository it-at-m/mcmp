<template>
  <div class="namespace-list-container">
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
      search-label="Namespace suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @row-click="onRowClick"
      @load-more="onLoadMore"
      @row-keydown="onRowKeydown"
    >
      <template #item.name="{ item }">
        <div class="name-cell">
          <v-btn
            icon
            variant="text"
            density="compact"
            :color="item.isFavorite ? 'warning' : 'grey-lighten-1'"
            class="mr-1"
            tabindex="-1"
            title="Favorit (Taste F, wenn Zeile fokussiert)"
            @click.stop="toggleFavorite(item)"
          >
            <v-icon>{{ item.isFavorite ? mdiStar : mdiStarOutline }}</v-icon>
          </v-btn>
          <span>{{ item.name }}</span>
        </div>
      </template>
      <template #no-data>
        <v-row />
        <v-row>
          <v-col>
            <v-alert
              v-if="search && search.length > 0"
              type="info"
            >
              <h2>Keine Namespaces gefunden</h2>
              <span>Bitte überprüfen Sie Ihre Filtereinstellungen</span>
            </v-alert>
            <v-alert
              v-else
              type="info"
            >
              <h2>Keine Namespaces verfügbar</h2>
            </v-alert>
          </v-col>
        </v-row>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type { OpenshiftNamespaceListItem } from "@/types/OpenshiftNamespaceListItem.ts";
import type { DataTableHeader } from "vuetify";

import { mdiStar, mdiStarOutline } from "@mdi/js";
import { computed, nextTick, onMounted, ref, watch } from "vue";

import openshiftService from "@/api/openshiftService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";

interface SortByEntry {
  key: string;
  order: "asc" | "desc";
}

const props = defineProps<{
  modelValue?: OpenshiftNamespaceListItem[];
  urlParamsId?: string | string[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", selected: OpenshiftNamespaceListItem[]): void;
  (e: "update:selected", selected: OpenshiftNamespaceListItem | null): void;
}>();

const loading = ref(false);
const items = ref<OpenshiftNamespaceListItem[]>([]);
const totalItems = ref(0);
const itemsPerPage = ref(25);
const currentPage = ref(1);
const sortBy = ref<SortByEntry[]>([{ key: "name", order: "asc" }]);
const selected = ref<OpenshiftNamespaceListItem[]>([]);
const hasMore = ref(true);
const search = ref("");
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const tableRef = ref<{ triggerObserveScroll: () => void } | null>(null);

const headers = ref<DataTableHeader[]>([
  { title: "Name", key: "name", align: "start", sortable: true },
]);

const currentSort = computed<SortByEntry>(
  () => sortBy.value[0] ?? { key: "name", order: "asc" }
);

const selectedId = computed(() =>
  selected.value.length > 0 ? selected.value[0]?.id : null
);

const tableItems = computed(() => items.value);

const normalizedUrlParamId = computed(() =>
  typeof props.urlParamsId === "string" ? props.urlParamsId : undefined
);

function selectItem(item: OpenshiftNamespaceListItem) {
  selected.value = [item];
  emit("update:selected", item);
  emit("update:modelValue", [item]);
}

function onRowKeydown({
  key,
  item,
}: {
  key: string;
  item: OpenshiftNamespaceListItem;
}) {
  if (key === "f" || key === "F") {
    toggleFavorite(item);
  }
}

async function toggleFavorite(item: OpenshiftNamespaceListItem) {
  const source = items.value.find((i) => i.id === item.id);
  if (!source) return;
  const originalState = source.isFavorite;
  source.isFavorite = !source.isFavorite;
  items.value = [...items.value].sort((a, b) => {
    const favDiff = (b.isFavorite ? 1 : 0) - (a.isFavorite ? 1 : 0);
    if (favDiff !== 0) return favDiff;
    return a.name.localeCompare(b.name);
  });

  try {
    if (originalState) {
      await openshiftService.removeNamespaceFromFavorites(source.id);
    } else {
      await openshiftService.addNamespaceToFavorites(source.id);
    }
  } catch {
    source.isFavorite = originalState;
  }
}

async function loadItems(page = 1) {
  loading.value = true;
  const offset = (page - 1) * itemsPerPage.value;
  try {
    const sanitizedSearch = (search.value || "")
      .replace(/[^a-zA-Z0-9 ._:/-]/g, "")
      .trim();

    const res = await openshiftService.getVisibleNamespaces(
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
    console.debug("Failed to load namespaces", e);
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

function onRowClick(item: OpenshiftNamespaceListItem) {
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
.namespace-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.name-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
