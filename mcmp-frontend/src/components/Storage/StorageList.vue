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
      search-label="Storage suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @row-click="onRowClick"
      @load-more="onLoadMore"
    >
      <template #[`header.type`]="{ column, toggleSort }">
        <div
          class="header-container"
          @click="toggleSort(column)"
        >
          <span>{{ typeHeaderLabel }}</span>
          <v-icon
            v-if="currentSort.key === 'type'"
            size="small"
            class="v-data-table-header__sort-icon"
          >
            {{ currentSort.order === 'asc' ? mdiArrowUp : mdiArrowDown }}
          </v-icon>
          <v-badge
            :model-value="selectedTypeFilters.length !== 0"
            dot
          >
            <div
              class="filter-buttons"
              @click.stop
            >
              <v-menu :close-on-content-click="false">
                <template #activator="{ props: filterActivatorProps }">
                  <v-btn
                    v-bind="filterActivatorProps"
                    icon
                    size="x-small"
                    variant="text"
                    :title="typeFilterTitle"
                  >
                    <v-icon>{{ mdiFilterVariant }}</v-icon>
                  </v-btn>
                </template>
                <v-list
                  density="compact"
                  style="border-width: thin"
                >
                  <v-list-subheader>{{ typeFilterSubheader }}</v-list-subheader>
                  <v-list-item
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="selectedTypeFilters"
                      label="NFS"
                      value="nfs"
                      hide-details
                      density="compact"
                    />
                  </v-list-item>
                  <v-list-item
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="selectedTypeFilters"
                      label="CIFS"
                      value="cifs"
                      hide-details
                      density="compact"
                    />
                  </v-list-item>
                  <v-list-item
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="selectedTypeFilters"
                      label="QTREE"
                      value="qtree"
                      hide-details
                      density="compact"
                    />
                  </v-list-item>
                  <v-list-item
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="selectedTypeFilters"
                      label="S3"
                      value="s3"
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

      <template #[`item.type`]="{ item }">
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
          {{ item.type }}
        </v-tooltip>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";
import type { DataTableHeader } from "vuetify";

import {
  mdiArrowDown,
  mdiArrowUp,
  mdiBucketOutline,
  mdiFilterVariant,
  mdiFolderNetworkOutline,
  mdiFolderOutline,
  mdiHarddisk,
} from "@mdi/js";
import { computed, nextTick, onMounted, ref, watch } from "vue";

import storageService from "@/api/storageService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";

interface SortByEntry {
  key: string;
  order: "asc" | "desc";
}

type TableItem = UnifiedStorageItemList & { id: string };

const props = defineProps<{
  modelValue?: UnifiedStorageItemList[];
  urlParamsId?: string | string[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", selected: UnifiedStorageItemList[]): void;
  (e: "update:selected", selected: UnifiedStorageItemList | null): void;
}>();

const search = ref("");
const loading = ref(false);
const items = ref<UnifiedStorageItemList[]>([]);
const totalItems = ref(0);
const itemsPerPage = ref(20);
const currentPage = ref(1);
const sortBy = ref<SortByEntry[]>([{ key: "name", order: "asc" }]);
const selected = ref<UnifiedStorageItemList[]>([]);
const hasMore = ref(true);
const selectedTypeFilters = ref<string[]>([]);
const typeHeaderLabel = "Typ";
const typeFilterSubheader = "Typ";
const typeFilterTitle = "Typ-Filter anzeigen";

const selectedId = computed(() =>
  selected.value.length > 0 ? selected.value[0]?.uuid : null
);

const tableRef = ref<{ triggerObserveScroll: () => void } | null>(null);

const headers = ref<DataTableHeader[]>([
  { title: typeHeaderLabel, key: "type", sortable: true },
  { title: "Name", key: "name", sortable: true },
]);

const currentSort = computed<SortByEntry>(() => sortBy.value[0] ?? { key: "name", order: "asc" });

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

// When the type filter changes we request the backend with the selected types
watch(selectedTypeFilters, async () => {
  currentPage.value = 1;
  await loadItems(1);
  await nextTick();
  tableRef.value?.triggerObserveScroll();
});

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
      .replace(/[^a-zA-Z0-9 ._:\/-]/g, "")
      .trim();

    const typesParam = selectedTypeFilters.value.length
      ? selectedTypeFilters.value.map((t) => t.toUpperCase())
      : undefined;

    const response = await storageService.getUnifiedStorage(
      loading,
      page - 1,
      itemsPerPage.value,
      sortKey,
      sortOrder,
      sanitizedSearch,
      typesParam
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

watch(search, async () => {
  currentPage.value = 1;
  await loadItems(1);
  await nextTick();
  tableRef.value?.triggerObserveScroll();
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

.header-container {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}

.header-container .filter-buttons {
  margin-left: auto;
}

.filter-buttons {
  display: flex;
  gap: 4px;
}
</style>
