<template>
  <div class="storage-list-container">
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
      :search="search"
      search-label="Storage suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @row-click="onRowClick"
      @load-more="onLoadMore"
      @row-keydown="onRowKeydown"
    >
      <template #[`header.storageCategory`]>
        <div class="header-container">
          <v-badge
            :model-value="selectedCategoryFilters.length !== 0"
            dot
          >
            <div class="filter-buttons">
              <v-menu :close-on-content-click="false">
                <template #activator="{ props: filterActivatorProps }">
                  <v-btn
                    v-bind="filterActivatorProps"
                    icon
                    size="x-small"
                    variant="text"
                    title="Kategorie-Filter anzeigen"
                  >
                    <v-icon>{{ mdiFilterVariant }}</v-icon>
                  </v-btn>
                </template>
                <v-list
                  density="compact"
                  style="border-width: thin"
                >
                  <v-list-subheader>Typ</v-list-subheader>
                  <v-list-item
                    v-for="cat in allCategories"
                    :key="cat.value"
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="selectedCategoryFilters"
                      :label="cat.label"
                      :value="cat.value"
                      hide-details
                      density="compact"
                    />
                  </v-list-item>
                </v-list>
              </v-menu>
            </div>
          </v-badge>
        </div>
      </template>

      <template #[`item.name`]="{ item }">
        {{
          item.type?.toUpperCase() === "QTREE" && item.path
            ? item.path
            : item.name
        }}
      </template>

      <template #[`item.storageCategory`]="{ item }">
        <div class="storage-category-cell">
          <v-btn
            icon
            variant="text"
            density="compact"
            :color="item.isFavorite ? 'warning' : 'grey-lighten-1'"
            class="mr-1 ml-2"
            tabindex="-1"
            title="Favorit (Taste F, wenn Zeile fokussiert)"
            @click.stop="toggleFavorite(item)"
          >
            <v-icon>{{ item.isFavorite ? mdiStar : mdiStarOutline }}</v-icon>
          </v-btn>
          <v-tooltip>
            <template #activator="{ props: tooltipProps }">
              <v-icon
                v-bind="tooltipProps"
                size="x-large"
                :color="switchColor(item.type)"
              >
                {{ switchType(item.type) }}
              </v-icon>
            </template>
            {{ formatStorageCategory(item.storageCategory) }}
          </v-tooltip>
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
              <h2>Keine Storages gefunden</h2>
              <span>Bitte überprüfen Sie Ihre Filtereinstellungen</span>
            </v-alert>
            <v-alert
              v-else
              type="info"
              class="links"
            >
              <h2>Keine Storages verfügbar</h2>
              <span
                >Bitte überprüfen Sie das Ihre Storages einem Anwendungsservice
                zugeordnet sind.<br />Weitere Informationen finden Sie
              </span>
              <a
                :href="APPSERVICE_EXPLAIN_URL"
                target="_blank"
                >hier</a
              >
            </v-alert>
          </v-col>
        </v-row>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";
import type { DataTableHeader } from "vuetify";

import {
  mdiBucketOutline,
  mdiFilterVariant,
  mdiFolderNetworkOutline,
  mdiFolderOutline,
  mdiHarddisk,
  mdiStar,
  mdiStarOutline,
} from "@mdi/js";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";

import storageService from "@/api/storageService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";
import { APPSERVICE_EXPLAIN_URL } from "@/constants.ts";

interface SortByEntry {
  key: string;
  order: "asc" | "desc";
}

type TableItem = UnifiedStorageItemList & { id: string };

const props = defineProps<{
  modelValue?: UnifiedStorageItemList[];
  urlParamsId?: string | string[];
  initialSearch?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", selected: UnifiedStorageItemList[]): void;
  (e: "update:selected", selected: UnifiedStorageItemList | null): void;
  (e: "update:totalItems", totalItems: number): void;
  (e: "update:search", val: string): void;
}>();

const search = ref(props.initialSearch ?? "");
const loading = ref(false);
const items = ref<UnifiedStorageItemList[]>([]);
const totalItems = ref(0);
const itemsPerPage = ref(20);
const currentPage = ref(1);
const sortBy = ref<SortByEntry[]>([{ key: "name", order: "asc" }]);
const selected = ref<UnifiedStorageItemList[]>([]);
const hasMore = ref(true);
const selectedCategoryFilters = ref<string[]>([]);
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const allCategories = [
  { value: "NFS_STANDARD_SHARE", label: "NFS Standard Share" },
  { value: "NFS_CLONE", label: "NFS Clone" },
  { value: "NFS_WORM", label: "NFS WORM" },
  { value: "NFS_SHARED", label: "NFS Shared" },
  { value: "ORACLE_VOLUME", label: "Oracle Volume" },
  { value: "CIFS_STANDARD_SHARE", label: "CIFS Standard Share" },
  { value: "CIFS_CLONE", label: "CIFS Clone" },
  { value: "CIFS_WORM", label: "CIFS WORM" },
  { value: "S3_SERVICE_BUCKET", label: "S3 Service Bucket" },
  { value: "ORACLE_FRA_QTREE", label: "Oracle FRA Qtree" },
];

const selectedId = computed(() =>
  selected.value.length > 0 ? selected.value[0]?.uuid : null
);

const tableRef = ref<{ triggerObserveScroll: () => void } | null>(null);

const headers = ref<DataTableHeader[]>([
  { title: "Typ", key: "storageCategory", sortable: false, width: "88px" },
  { title: "Name", key: "name", sortable: true },
]);

const currentSort = computed<SortByEntry>(
  () => sortBy.value[0] ?? { key: "name", order: "asc" }
);

const tableItems = computed<TableItem[]>(() =>
  items.value.map((item) => ({
    ...item,
    id: item.uuid,
  }))
);

const normalizedUrlParamId = computed(() =>
  typeof props.urlParamsId === "string" ? props.urlParamsId : undefined
);

function normalizeType(type: string) {
  return (type ?? "").trim().toUpperCase();
}

watch(selectedCategoryFilters, async () => {
  currentPage.value = 1;
  await loadItems(1);
  await nextTick();
  tableRef.value?.triggerObserveScroll();
});

function formatStorageCategory(category: string | undefined): string {
  if (!category) return "-";
  return category
    .split("_")
    .map((word) =>
      word.length <= 4
        ? word.toUpperCase()
        : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    )
    .join(" ");
}

function switchType(type: string) {
  switch (normalizeType(type)) {
    case "NFS":
    case "QTREE":
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
  switch (normalizeType(type)) {
    case "NFS":
    case "QTREE":
      return "#ee0000";
    case "CIFS":
      return "#0078d4";
    case "S3":
      return "orange";
    default:
      return "grey";
  }
}

function onRowKeydown({ key, item }: { key: string; item: TableItem }) {
  if (key === "f" || key === "F") {
    toggleFavorite(item);
  }
}

async function toggleFavorite(item: TableItem) {
  const source = items.value.find(
    (i) => i.uuid === item.uuid && i.type === item.type
  );
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
      await storageService.removeStorageFromFavorites(source.type, source.uuid);
    } else {
      await storageService.addStorageToFavorites(source.type, source.uuid);
    }
  } catch {
    source.isFavorite = originalState;
  }
}

function selectItem(item: UnifiedStorageItemList) {
  selected.value = [item];
  emit("update:selected", item);
  emit("update:modelValue", [item]);
}

const loadItems = async (page = 1) => {
  loading.value = true;
  try {
    const sortKey = currentSort.value.key;
    const sortOrder = currentSort.value.order;

    const sanitizedSearch = (search.value || "")
      .replace(/[^a-zA-Z0-9 ._:/-]/g, "")
      .trim();

    const categoriesParam = selectedCategoryFilters.value.length
      ? selectedCategoryFilters.value
      : undefined;

    const response = await storageService.getUnifiedStorage(
      loading,
      page - 1,
      itemsPerPage.value,
      sortKey,
      sortOrder,
      sanitizedSearch,
      categoriesParam
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
    console.debug("Failed to load storage items", e);
  } finally {
    loading.value = false;
  }
};

const updateSortBy = (newSortBy: SortByEntry[]) => {
  sortBy.value = newSortBy;
  currentPage.value = 1;
  loadItems(1);
  nextTick(() => {
    tableRef.value?.triggerObserveScroll();
  });
};

const onSearchUpdate = (val: string) => {
  search.value = val;
};

watch(
  () => props.initialSearch,
  (val) => {
    if (val !== undefined && val !== search.value) search.value = val;
  }
);

const onRowClick = (item: TableItem) => {
  if (!item) return;
  selectItem(item);
};

const onLoadMore = async () => {
  if (!loading.value && hasMore.value) {
    currentPage.value++;
    await loadItems(currentPage.value);
  }
};

watch(totalItems, (newVal) => {
  emit("update:totalItems", newVal);
});

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

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout);
});

// watch for selectedTypeFilters is defined above to reload from backend

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
  tableRef.value?.triggerObserveScroll();
});
</script>

<style scoped>
.storage-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.storage-category-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-container {
  display: flex;
  align-items: center;
  justify-content: left;
  width: 100%;
  margin-left: 0;
}

.header-container .filter-buttons {
  margin-left: auto;
}

.filter-buttons {
  display: flex;
  gap: 4px;
}

:deep(td:first-child),
:deep(th:first-child) {
  width: 76px !important;
  min-width: 76px !important;
  max-width: 76px !important;
  padding-left: 8px !important;
  padding-right: 4px !important;
}

:deep(th:first-child) {
  padding-left: 6px !important;
}

.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
