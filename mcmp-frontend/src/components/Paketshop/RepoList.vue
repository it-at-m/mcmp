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
      :search="search"
      search-label="Repository suchen..."
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
          <repo-lock-status-icon
            :status="item.lockStatus"
            class="name-cell-icon"
          />
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
              <h2>Keine Repositories gefunden</h2>
              <span>Bitte überprüfen Sie Ihre Filtereinstellungen</span>
            </v-alert>
            <v-alert
              v-else
              type="info"
            >
              <h2>Keine Repositories verfügbar</h2>
            </v-alert>
          </v-col>
        </v-row>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository.ts";
import type { DataTableHeader } from "vuetify";

import { mdiStar, mdiStarOutline } from "@mdi/js";
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";

import repositoryService from "@/api/repositoryService.ts";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";
import RepoLockStatusIcon from "@/components/Paketshop/RepoLockStatusIcon.vue";

interface SortByEntry {
  key: string;
  order: "asc" | "desc";
}

const props = defineProps<{
  initialSearch?: string;
}>();

const initialUrlId = useRoute().params.id;

const emit = defineEmits<{
  (e: "update:selected", selected: Repository | null): void;
  (e: "update:search", val: string): void;
}>();

const loading = ref(false);
const items = ref<Repository[]>([]);
const totalItems = ref(0);
const itemsPerPage = ref(25);
const currentPage = ref(1);
const sortBy = ref<SortByEntry[]>([{ key: "name", order: "asc" }]);
const selected = ref<Repository[]>([]);
const hasMore = ref(true);
const search = ref(props.initialSearch ?? "");
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const tableRef = ref<{
  triggerObserveScroll: () => void;
  resetSelection: () => void;
} | null>(null);

const headers = ref<DataTableHeader[]>([
  { title: "Name", key: "name", align: "start", sortable: true },
]);

const currentSort = computed<SortByEntry>(
  () => sortBy.value[0] ?? { key: "name", order: "asc" }
);

const selectedId = computed(() =>
  selected.value.length > 0 ? selected.value[0]?.id : null
);

watch(selectedId, (val) => {
  if (val === null || val === undefined) {
    tableRef.value?.resetSelection();
  }
});

const tableItems = computed(() => items.value);

const normalizedUrlParamId = computed(() =>
  typeof initialUrlId === "string" ? initialUrlId : undefined
);

function selectItem(item: Repository) {
  selected.value = [item];
  emit("update:selected", item);
}

function onRowKeydown({ key, item }: { key: string; item: Repository }) {
  if (key === "f" || key === "F") {
    toggleFavorite(item);
  }
}

async function toggleFavorite(item: Repository) {
  const source = items.value.find((i) => i.id === item.id);
  if (!source) return;
  const originalState = source.isFavorite;
  source.isFavorite = !source.isFavorite;
  sortItems();

  try {
    if (originalState) {
      await repositoryService.removeRepositoryFromFavorites(source.id);
    } else {
      await repositoryService.addRepositoryToFavorites(source.id);
    }
  } catch {
    source.isFavorite = originalState;
    sortItems();
  }
}

function sortItems() {
  const dir = currentSort.value.order === "desc" ? -1 : 1;
  items.value = [...items.value].sort((a, b) => {
    const favDiff = (b.isFavorite ? 1 : 0) - (a.isFavorite ? 1 : 0);
    if (favDiff !== 0) return favDiff;
    return dir * a.name.localeCompare(b.name);
  });
}

async function loadItems(page = 1) {
  loading.value = true;
  const offset = (page - 1) * itemsPerPage.value;
  try {
    const sanitizedSearch = (search.value || "")
      .replace(/[^a-zA-Z0-9 ._:/-]/g, "")
      .trim();

    const res = await repositoryService.getVisibleRepositories(
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
    }
  } catch (e) {
    console.debug("Failed to load repos", e);
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

watch(
  () => props.initialSearch,
  (val) => {
    if (val !== undefined && val !== search.value) search.value = val;
  }
);

function onRowClick(item: Repository) {
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
    emit("update:search", search.value);
    currentPage.value = 1;
    await loadItems(1);
    await nextTick();
    tableRef.value?.triggerObserveScroll();
  }, 300);
});

onMounted(async () => {
  await loadItems(1);
  if (normalizedUrlParamId.value) {
    const id = Number(normalizedUrlParamId.value);
    const match = items.value.find((i) => i.id === id);
    if (match) selected.value = [match];
  }
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

.name-cell-icon {
  margin-right: 8px;
}
</style>
