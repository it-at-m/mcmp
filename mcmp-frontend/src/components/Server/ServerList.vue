<template>
  <div class="server-list-container links">
    <scrollable-list-table
      ref="tableRef"
      :items="filteredServers"
      :total-items="totalServers"
      :loading="loadingServer"
      :headers="headers"
      :sort-by="sortBy"
      :items-per-page="itemsPerPage"
      :has-more="hasMore"
      :selected-id="selectedRow"
      :search="search"
      search-label="Server suchen..."
      @update:sort-by="updateSortBy"
      @update:search="onSearchUpdate"
      @row-click="onRowClick"
      @load-more="onLoadMore"
      @row-keydown="onRowKeydown"
    >
      <template #[`header.name`]="{ column, toggleSort }">
        <div
          class="header-container"
          @click="toggleSort(column)"
        >
          <span>Servername</span>
          <v-icon
            v-if="sortBy.length > 0 && sortBy[0].key === 'name'"
            size="small"
            class="v-data-table-header__sort-icon"
          >
            {{ sortBy[0].order === "asc" ? mdiArrowUp : mdiArrowDown }}
          </v-icon>
          <v-badge
            :model-value="
              statusFilter.length !== 0 || osFilter !== '' || favoritesFilter
            "
            dot
          >
            <div
              class="filter-buttons"
              @click.stop
            >
              <v-menu :close-on-content-click="false">
                <template #activator="{ props: activatorProps }">
                  <v-btn
                    v-bind="activatorProps"
                    icon
                    size="x-small"
                    variant="text"
                    title="Filter anzeigen"
                  >
                    <v-icon>{{ mdiFilterVariant }}</v-icon>
                  </v-btn>
                </template>
                <v-list
                  density="compact"
                  style="border-width: thin"
                >
                  <v-list-subheader>Status</v-list-subheader>
                  <v-list-item
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="statusFilter"
                      label="On"
                      value="poweredOn"
                      hide-details
                      density="compact"
                    />
                  </v-list-item>
                  <v-list-item
                    density="compact"
                    class="py-0"
                  >
                    <v-checkbox
                      v-model="statusFilter"
                      label="Off"
                      value="poweredOff"
                      hide-details
                      density="compact"
                    />
                    <v-checkbox
                      v-model="favoritesFilter"
                      label="Favoriten"
                      density="compact"
                      hide-details
                      color="primary"
                    />
                  </v-list-item>
                  <v-divider class="my-2" />
                  <v-list-subheader>Betriebssystem</v-list-subheader>
                  <v-radio-group
                    v-model="osFilter"
                    hide-details
                    density="compact"
                  >
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Alle"
                        value=""
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Alle Windows"
                        value="windows"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Managed Windows"
                        value="mng-windows"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Alle Linux"
                        value="linux"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Managed Linux"
                        value="mng-linux"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Managed Oracle"
                        value="oracle"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Managed Non-Oracle"
                        value="non-oracle"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Windows Clients"
                        value="windows-clients"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                      ><v-radio
                        label="Unmanaged"
                        value="unmanaged"
                        hide-details
                        density="compact"
                    /></v-list-item>
                    <v-list-item
                      density="compact"
                      class="py-0"
                    >
                      <v-radio
                        label="Ohne Anwendungsservice"
                        value="no-appservice"
                        hide-details
                        density="compact"
                      />
                    </v-list-item>
                  </v-radio-group>
                </v-list>
              </v-menu>
            </div>
          </v-badge>
        </div>
      </template>

      <template #[`item.name`]="{ item }">
        <div class="server-name-cell">
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
          <v-tooltip
            :text="
              item.powerState === 'poweredOn'
                ? 'Eingeschaltet!'
                : item.powerState === 'poweredOff'
                  ? 'Ausgeschaltet!'
                  : 'Suspended'
            "
          >
            <template #activator="{ props }">
              <div class="power-state-icon-inline">
                <v-icon
                  :color="
                    item.powerState === 'poweredOn'
                      ? 'btn_green'
                      : item.powerState === 'poweredOff'
                        ? 'btn_red'
                        : 'accent'
                  "
                  size="25"
                  v-bind="props"
                >
                  {{
                    item.powerState === "poweredOn"
                      ? mdiPlayCircle
                      : item.powerState === "poweredOff"
                        ? mdiStopCircle
                        : mdiPauseCircle
                  }}
                </v-icon>
              </div>
            </template>
          </v-tooltip>
          <os-cell
            :os-full-name="item.os || ''"
            size="small"
            class="os-icon-inline"
          />
          <span class="server-name-text">{{ item.name.split(".")[0] }}</span>
          <v-tooltip
            v-if="item.hasWarnings"
            location="top"
            text="Handlung erforderlich"
          >
            <template #activator="{ props: tooltipProps }">
              <v-icon
                v-bind="tooltipProps"
                :icon="mdiAlert"
                color="orange"
                size="20"
                class="ml-1"
              />
            </template>
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
              <h2>Keine Server gefunden</h2>
              <span>Bitte überprüfen Sie Ihre Filtereinstellungen</span>
            </v-alert>
            <v-alert
              v-else
              type="info"
            >
              <h2>Keine Server verfügbar</h2>
              <span
                >Bitte überprüfen Sie das Ihre Server einem Anwendungsservice
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
import type { ServerList } from "@/types/ServerList";
import type { DataTableHeader } from "vuetify";

import {
  mdiAlert,
  mdiArrowDown,
  mdiArrowUp,
  mdiFilterVariant,
  mdiPauseCircle,
  mdiPlayCircle,
  mdiStar,
  mdiStarOutline,
  mdiStopCircle,
} from "@mdi/js";
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";

import serverService from "@/api/serverService";
import ScrollableListTable from "@/components/common/ScrollableListTable.vue";
import OsCell from "@/components/Server/OsCell.vue";
import { APPSERVICE_EXPLAIN_URL } from "@/constants.ts";

const props = defineProps<{
  selected: ServerList[];
  unActivateServerRow: boolean;
  urlParamsId?: string | string[];
  urlParamsAppId?: string | string[];
  initialSearch?: string;
}>();

const favoritesFilter = ref(
  localStorage.getItem("mcmp_favorites_filter") === "true"
);
const loadingServer = ref(false);
const servers = ref<ServerList[]>([]);
const totalServers = ref(0);
const search = ref(props.initialSearch ?? "");
const currentPage = ref(1);
const itemsPerPage = ref(25);
const hasMore = ref(true);
const selectedRow = ref<string | null>(null);
const sortBy = ref([{ key: "name", order: "asc" as "asc" | "desc" }]);
const tableRef = ref<{
  triggerObserveScroll: () => void;
  resetSelection: () => void;
} | null>(null);
const statusFilter = ref<string[]>(
  JSON.parse(localStorage.getItem("mcmp_status_filter") || "[]")
);
const osFilter = ref<string>(localStorage.getItem("mcmp_os_filter") || "");
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const emit = defineEmits<{
  (e: "update:selected", selected: ServerList[]): void;
  (e: "resetUnActivateServerRow"): void;
  (e: "update:search", val: string): void;
}>();

const headers = ref<DataTableHeader[]>([
  { title: "Servername", key: "name", align: "start", sortable: true },
]);

const filteredServers = computed(() => servers.value);

function sortServersByFavorite() {
  const order = sortBy.value[0]?.order === "desc" ? -1 : 1;
  servers.value = [...servers.value].sort((a, b) => {
    const favDiff = (b.isFavorite ? 1 : 0) - (a.isFavorite ? 1 : 0);
    if (favDiff !== 0) return favDiff;
    return order * a.name.localeCompare(b.name);
  });
}

function removeIfBeyondLoadedRange(server: any) {
  if (!hasMore.value) return;
  const order = sortBy.value[0]?.order === "desc" ? -1 : 1;
  const nonFavorites = servers.value.filter(
    (s) => !s.isFavorite && s.id !== server.id
  );
  if (nonFavorites.length === 0) return;
  const lastName = nonFavorites[nonFavorites.length - 1].name;
  const beyondRange = order * server.name.localeCompare(lastName) > 0;
  if (beyondRange) {
    servers.value = servers.value.filter((s) => s.id !== server.id);
  }
}

function onRowKeydown({ key, item }: { key: string; item: any }) {
  if (key === "f" || key === "F") {
    toggleFavorite(item);
  }
}

async function toggleFavorite(server: any) {
  const originalState = server.isFavorite;
  server.isFavorite = !server.isFavorite;

  if (originalState) {
    removeIfBeyondLoadedRange(server);
  }
  sortServersByFavorite();

  try {
    if (originalState) {
      await serverService.removeServerFromFavorites(server.id);
    } else {
      await serverService.addServerToFavorites(server.id);
    }
  } catch (error) {
    server.isFavorite = originalState;
    if (!servers.value.find((s) => s.id === server.id)) {
      servers.value = [...servers.value, server];
    }
    sortServersByFavorite();
  }
}

watch([statusFilter, osFilter, favoritesFilter], async () => {
  currentPage.value = 1;
  await loadServers(
    1,
    statusFilter.value,
    osFilter.value,
    favoritesFilter.value
  );
  await nextTick();
  tableRef.value?.triggerObserveScroll();
});

watch(favoritesFilter, (newValue) => {
  localStorage.setItem("mcmp_favorites_filter", String(newValue));
});

watch(statusFilter, (newVal) => {
  localStorage.setItem("mcmp_status_filter", JSON.stringify(newVal));
});
watch(osFilter, (newVal) => {
  localStorage.setItem("mcmp_os_filter", newVal);
});

watch(
  () => props.unActivateServerRow,
  (newValue) => {
    if (newValue) {
      selectedRow.value = null;
      emit("update:selected", []);
      tableRef.value?.resetSelection();
      emit("resetUnActivateServerRow");
    }
  }
);

function updateSortBy(newSortBy: { key: string; order: "asc" | "desc" }[]) {
  sortBy.value = newSortBy;
  currentPage.value = 1;
  loadServers(1, statusFilter.value, osFilter.value, favoritesFilter.value);
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

async function onLoadMore() {
  currentPage.value++;
  await loadServers(
    currentPage.value,
    statusFilter.value,
    osFilter.value,
    favoritesFilter.value
  );
}

async function loadServers(
  page = 1,
  status: string[] = [],
  os = "",
  favorites = false
) {
  loadingServer.value = true;
  const offset = (page - 1) * itemsPerPage.value;
  const currentSort = sortBy.value[0] ?? { key: "name", order: "asc" };

  search.value = (search.value ?? "").replace(/[^a-zA-Z0-9 .-]/g, "").trim();

  try {
    const res = await serverService.getVisibleServers(
      loadingServer,
      offset,
      itemsPerPage.value,
      currentSort.key,
      currentSort.order,
      search.value,
      status,
      os,
      favorites
    );
    if (page === 1) {
      servers.value = res.content;
    } else {
      servers.value.push(...res.content);
    }
    totalServers.value = res.page.totalElements;
    hasMore.value = servers.value.length < totalServers.value;

    const hasUrlId =
      !!props.urlParamsId &&
      (Array.isArray(props.urlParamsId) ? props.urlParamsId.length > 0 : true);
    const hasUrlAppId =
      !!props.urlParamsAppId &&
      (Array.isArray(props.urlParamsAppId)
        ? props.urlParamsAppId.length > 0
        : true);

    if (
      props.selected.length === 0 &&
      servers.value.length > 0 &&
      !hasUrlId &&
      !hasUrlAppId &&
      !selectedRow.value
    ) {
      emit("update:selected", [servers.value[0]]);
    } else if (totalServers.value === 0) {
      emit("update:selected", []);
    }
  } finally {
    loadingServer.value = false;
  }
}

function onRowClick(item: ServerList) {
  selectedRow.value = item.id.toString();
  emit("update:selected", [item]);
}

watch(search, () => {
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(async () => {
    emit("update:search", search.value);
    currentPage.value = 1;
    await loadServers(
      1,
      statusFilter.value,
      osFilter.value,
      favoritesFilter.value
    );
    await nextTick();
    tableRef.value?.triggerObserveScroll();
  }, 300);
});

onMounted(async () => {
  if (props.urlParamsId) {
    const id = Array.isArray(props.urlParamsId)
      ? props.urlParamsId[0]
      : props.urlParamsId;
    selectedRow.value = id.toString();
    emit("update:selected", [{ id: parseInt(id) } as ServerList]);
  }
  await loadServers(
    1,
    statusFilter.value,
    osFilter.value,
    favoritesFilter.value
  );
});

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout);
});

function updateServerPowerState(serverId: number, newPowerState: string) {
  const idx = servers.value.findIndex((s) => s.id === serverId);
  if (idx !== -1) {
    servers.value[idx].powerState = newPowerState;
    emit("update:selected", [
      { ...props.selected[0], powerState: newPowerState },
    ]);
  }
}

defineExpose({ updateServerPowerState });
</script>

<style scoped>
.server-list-container {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.server-name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.power-state-icon-inline {
  display: flex;
  /* noinspection CssUnresolvedCustomProperty */
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 18px !important;
  height: 18px !important;
  border-radius: 50%;
  flex-shrink: 0;
}

.os-icon-inline {
  flex-shrink: 0;
  margin: 0 !important;
  padding: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

:deep(.os-icon-inline *) {
  width: 30px !important;
  max-width: 30px !important;
  min-height: 30px !important;
  height: 30px !important;
  object-fit: contain !important;
  margin: 0 !important;
  padding: 0 !important;
}

.server-name-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-left: 4px;
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

.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
