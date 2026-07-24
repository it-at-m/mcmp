<template>
  <div class="appservice-list-container-root">
    <scrollable-list-table
      ref="tableRef"
      :items="appservicesItems"
      :total-items="itemsAvailableToLoad"
      :loading="loading"
      :headers="headers"
      :sort-by="sortBy"
      :items-per-page="itemsPerPage"
      :has-more="curOffset < itemsAvailableToLoad"
      :selected-id="selectedId"
      :search="search"
      search-label="Anwendungsservice suchen"
      search-tooltip="Name oder SNSVC des Anwendungsservice"
      @update:search="onSearchUpdate"
      @row-click="onRowClick"
      @load-more="onLoadMore"
      @row-keydown="onRowKeydown"
    >
      <template #[`item.name`]="{ item }">
        <div class="appservice-name-cell">
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
        <v-row>
          <v-col>
            <v-alert
              v-if="search && search.length > 0"
              type="info"
            >
              <h2>Keine Anwendungsservices gefunden</h2>
              <span>Bitte überprüfen Sie Ihre Filtereinstellungen</span>
            </v-alert>
            <v-alert
              v-else
              type="info"
              class="links"
            >
              <h2 v-if="search && search.length > 0">
                Keine Anwendungsservices gefunden
              </h2>
              <div v-else>
                <h2>Keine Appservices verfügbar</h2>
                <span
                  >Bitte überprüfen Sie, das Sie einer Changegroup mit
                  Anwendungsservices zugewiesen sind.<br />Weitere Informationen
                  finden Sie
                </span>
                <a
                  :href="APPSERVICE_EXPLAIN_URL"
                  target="_blank"
                  >hier</a
                >
              </div>
            </v-alert>
          </v-col>
        </v-row>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList.ts";
import type { DataTableHeader } from "vuetify";

import { mdiStar, mdiStarOutline } from "@mdi/js";
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService.ts";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";
import { APPSERVICE_EXPLAIN_URL } from "@/constants.ts";

const props = withDefaults(
  defineProps<{
    selected?: AppserviceList[];
    unActivateAppserviceRow?: boolean;
    urlParamsId?: string | string[];
    initialSearch?: string;
  }>(),
  {
    selected: () => [],
  }
);

const emit = defineEmits<{
  (e: "update:selected", selected: AppserviceList[]): void;
  (e: "resetUnActivateAppserviceRow"): void;
  (e: "appserviceSelected", appserviceId: number): void;
  (e: "update:search", val: string): void;
}>();

const loading = ref(false);
const search = ref(props.initialSearch ?? "");
const curOffset = ref(0);
const itemsAvailableToLoad = ref(0);
const appservicesItems = ref<AppserviceList[]>([]);
const selectedId = ref<number | null>(null);
const sortBy = ref([{ key: "name", order: "asc" as "asc" | "desc" }]);
const itemsPerPage = ref(50);
const tableRef = ref<{
  resetSelection: () => void;
  triggerObserveScroll: () => void;
} | null>(null);

let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const headers: DataTableHeader[] = [
  { title: "Anwendungsservice", key: "name", align: "start", sortable: true },
];

watch(
  () => props.unActivateAppserviceRow,
  (val) => {
    if (val) {
      selectedId.value = null;
      emit("update:selected", []);
      tableRef.value?.resetSelection();
      emit("resetUnActivateAppserviceRow");
    }
  }
);

watch(
  () => props.selected,
  (rows) => {
    selectedId.value = rows?.[0]?.id ?? null;
  },
  { immediate: true, deep: true }
);

function onSearchUpdate(val: string) {
  search.value = val ?? "";
}

watch(
  () => props.initialSearch,
  (val) => {
    if (val !== undefined && val !== search.value) search.value = val;
  }
);

watch(search, () => {
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(async () => {
    emit("update:search", search.value);
    curOffset.value = 0;
    appservicesItems.value = [];
    await loadAppservices();
    await nextTick();
    tableRef.value?.triggerObserveScroll();
  }, 300);
});

function onRowClick(item: AppserviceList) {
  selectedId.value = item.id;
  emit("update:selected", [item]);
  emit("appserviceSelected", item.id);
}

function onRowKeydown({ key, item }: { key: string; item: AppserviceList }) {
  if (key === "f" || key === "F") {
    toggleFavorite(item);
  }
}

function sortByFavorite() {
  appservicesItems.value = [...appservicesItems.value].sort((a, b) => {
    const favDiff = (b.isFavorite ? 1 : 0) - (a.isFavorite ? 1 : 0);
    if (favDiff !== 0) return favDiff;
    return a.name.localeCompare(b.name);
  });
}

async function toggleFavorite(item: AppserviceList) {
  const originalState = item.isFavorite;
  item.isFavorite = !item.isFavorite;
  sortByFavorite();

  try {
    if (originalState) {
      await appserviceService.removeAppserviceFromFavorites(item.id);
    } else {
      await appserviceService.addAppserviceToFavorites(item.id);
    }
  } catch {
    item.isFavorite = originalState;
    sortByFavorite();
  }
}

async function onLoadMore() {
  await loadAppservices();
  await nextTick();
  tableRef.value?.triggerObserveScroll();
}

async function loadAppservices() {
  loading.value = true;
  try {
    const res = await appserviceService.getAppservices(
      loading,
      curOffset.value,
      itemsPerPage.value,
      "asc",
      search.value.trim()
    );
    itemsAvailableToLoad.value = res.page.totalElements;
    const newItems = res.content
      .slice()
      .sort((a: AppserviceList, b: AppserviceList) => {
        const favDiff = (b.isFavorite ? 1 : 0) - (a.isFavorite ? 1 : 0);
        if (favDiff !== 0) return favDiff;
        return a.name.localeCompare(b.name);
      });

    if (curOffset.value === 0) {
      appservicesItems.value = newItems;
    } else {
      appservicesItems.value.push(...newItems);
    }
    curOffset.value += newItems.length;

    const hasUrlId =
      !!props.urlParamsId &&
      (Array.isArray(props.urlParamsId) ? props.urlParamsId.length > 0 : true);

    if (
      props.selected.length === 0 &&
      appservicesItems.value.length > 0 &&
      !hasUrlId &&
      !selectedId.value
    ) {
      const firstItem = appservicesItems.value[0];
      if (firstItem) {
        emit("update:selected", [firstItem]);
      }
    } else if (itemsAvailableToLoad.value === 0) {
      emit("update:selected", []);
    }
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await loadAppservices();
  await nextTick();
  tableRef.value?.triggerObserveScroll();
});

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout);
});
</script>

<style scoped>
.appservice-list-container-root {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.appservice-name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
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
