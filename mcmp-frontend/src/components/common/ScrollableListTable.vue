<template>
  <div class="scrollable-list-table-root">
    <div class="search-container">
      <v-text-field
        v-model="searchModel"
        :label="searchLabel"
        :prepend-inner-icon="mdiMagnify"
        variant="outlined"
        density="comfortable"
        hide-details
        class="material-search-field"
        rounded
        clearable
      >
        <!-- Vuetify Tooltip wird nur gerendert, wenn der String übergeben wurde -->
        <v-tooltip
          v-if="searchTooltip"
          activator="parent"
          location="top"
        >
          {{ searchTooltip }}
        </v-tooltip>
      </v-text-field>
    </div>

    <div
      ref="tableWrapper"
      class="keyboard-table-wrapper"
      :tabindex="wrapperTabindex"
      @keydown="onTableKeydown"
      @focusin="onFocusIn"
      @focusout="onFocusOut"
    >
      <v-data-table-server
        :headers="headers"
        :items="items"
        :items-length="totalItems"
        :loading="loading"
        :search="searchModel"
        :items-per-page="itemsPerPage"
        :sort-by="sortBy"
        select-strategy="single"
        item-value="id"
        density="compact"
        fixed-header
        class="scrollable-list-table"
        hide-default-footer
        hover
        @update:sort-by="emit('update:sortBy', $event)"
        @click:row="onRowClick"
      >
        <!-- Alle Header- und Item-Slots durchreichen -->
        <template
          v-for="(_, name) in $slots"
          #[name]="slotProps"
        >
          <slot
            :name="name"
            v-bind="slotProps ?? {}"
          />
        </template>

        <template #no-data>
          <slot name="no-data">
            <v-row />
            <v-row>
              <v-col>
                <v-alert type="info">
                  <h2>Keine Einträge gefunden</h2>
                </v-alert>
              </v-col>
            </v-row>
          </slot>
        </template>

        <!-- Sentinel für Infinite Scroll -->
        <template #[`body.append`]>
          <tr>
            <td :colspan="headers.length">
              <div
                ref="scrollSentinel"
                style="height: 1px"
                title="Lädt weitere Einträge"
              />
            </td>
          </tr>
        </template>
      </v-data-table-server>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T extends { id: number | string }">
import type { DataTableHeader } from "vuetify";

import { mdiMagnify } from "@mdi/js";
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";

const props = defineProps<{
  items: T[];
  totalItems: number;
  loading: boolean;
  headers: DataTableHeader[];
  sortBy: { key: string; order: "asc" | "desc" }[];
  itemsPerPage?: number;
  searchLabel?: string;
  searchTooltip?: string; // HIER: Das Prop für dein Tooltip registriert
  selectedId?: number | string | null;
  hasMore: boolean;
  search?: string;
}>();

const emit = defineEmits<{
  (e: "update:sortBy", val: { key: string; order: "asc" | "desc" }[]): void;
  (e: "update:search", val: string): void;
  (e: "rowClick", item: T): void;
  (e: "loadMore"): void;
  (e: "clearSelection"): void;
  (e: "rowKeydown", val: { key: string; item: T }): void;
}>();

// ── Search ────────────────────────────────────────────────────────────────────

const searchModel = ref(props.search ?? "");

watch(searchModel, (val) => emit("update:search", val));
watch(
  () => props.search,
  (val) => {
    if (val !== undefined && val !== searchModel.value) searchModel.value = val;
  }
);

// ── Keyboard / Focus ──────────────────────────────────────────────────────────

const tableWrapper = ref<HTMLElement | null>(null);
const focusedRowIndex = ref(0);
const focusedItemId = ref<T["id"] | null>(null);
const tableHasFocus = ref(false);

const wrapperTabindex = ref(0);

function updateWrapperTabindex() {
  const el = getScrollContainer();
  if (!el) return;
  wrapperTabindex.value = el.scrollHeight > el.clientHeight + 4 ? -1 : 0;
}

function getRows(): Element[] {
  return Array.from(
    (tableWrapper.value ?? document).querySelectorAll(
      ".scrollable-list-table .v-data-table__tr"
    )
  );
}

function stripHeaderTabindex() {
  tableWrapper.value
    ?.querySelectorAll("th[tabindex]")
    .forEach((th) => th.setAttribute("tabindex", "-1"));
}

function applyFocusHighlight(index: number) {
  getRows().forEach((el) => el.classList.remove("focused-row"));
  if (!tableHasFocus.value) return;
  const rows = getRows();
  if (rows[index]) rows[index].classList.add("focused-row");
}

function onFocusIn() {
  tableHasFocus.value = true;
  if (focusedItemId.value === null) {
    focusedItemId.value = props.items[focusedRowIndex.value]?.id ?? null;
  }
  applyFocusHighlight(focusedRowIndex.value);
}

function onFocusOut() {
  tableHasFocus.value = false;
  getRows().forEach((el) => el.classList.remove("focused-row"));
}

function scrollToRow(index: number) {
  const rows = getRows();
  if (rows[index])
    (rows[index] as HTMLElement).scrollIntoView({ block: "nearest" });
}

function selectRowByIndex(index: number) {
  const rows = getRows();
  // selected-row Highlight setzen
  rows.forEach((el) => el.classList.remove("selected-row"));
  if (rows[index]) rows[index].classList.add("selected-row");

  applyFocusHighlight(index);
  const item = props.items[index];
  if (item) emit("rowClick", item);
}

function moveFocusToIndex(index: number) {
  applyFocusHighlight(index);
}

function onTableKeydown(e: KeyboardEvent) {
  if (!props.items.length) return;

  if (e.key === "ArrowDown") {
    if (focusedRowIndex.value < props.items.length - 1) {
      focusedRowIndex.value++;
      focusedItemId.value = props.items[focusedRowIndex.value]?.id ?? null;
      moveFocusToIndex(focusedRowIndex.value);
      scrollToRow(focusedRowIndex.value);
      e.preventDefault();
    }
  } else if (e.key === "ArrowUp") {
    if (focusedRowIndex.value > 0) {
      focusedRowIndex.value--;
      focusedItemId.value = props.items[focusedRowIndex.value]?.id ?? null;
      moveFocusToIndex(focusedRowIndex.value);
      scrollToRow(focusedRowIndex.value);
      e.preventDefault();
    }
  } else if (e.key === "Enter" || e.key === " ") {
    selectRowByIndex(focusedRowIndex.value);
    e.preventDefault();
  } else {
    const item = props.items[focusedRowIndex.value];
    if (item) emit("rowKeydown", { key: e.key, item });
  }
}

function onRowClick(_event: MouseEvent, { item }: { item: T }) {
  emit("rowClick", item);

  getRows().forEach((el) => {
    el.classList.remove("selected-row");
    el.classList.remove("focused-row");
  });

  const cell = (_event.target as HTMLElement).closest(".v-data-table__tr");
  if (cell) {
    cell.classList.add("selected-row");
    cell.classList.add("focused-row");
  }

  const rows = Array.from(getRows());
  const idx = rows.indexOf(cell as Element);
  if (idx !== -1) {
    focusedRowIndex.value = idx;
    focusedItemId.value = props.items[idx]?.id ?? null;
  }

  tableWrapper.value?.focus();
}

// Keep focus on the same item when the list reorders (e.g. favorite toggle);
// only reset to the top when the previously focused item is gone (new search/filter).
watch(
  () => props.items,
  (newItems, oldItems) => {
    if (newItems.length > (oldItems?.length ?? 0)) return;

    if (focusedItemId.value !== null) {
      const newIndex = newItems.findIndex(
        (item) => item.id === focusedItemId.value
      );
      if (newIndex !== -1) {
        focusedRowIndex.value = newIndex;
        nextTick(() => {
          moveFocusToIndex(newIndex);
          scrollToRow(newIndex);
        });
        return;
      }
    }

    focusedRowIndex.value = 0;
    focusedItemId.value = newItems[0]?.id ?? null;
    nextTick(() => scrollToRow(0));
  }
);

// Reset selected highlight when selectedId becomes null
watch(
  () => props.selectedId,
  (val) => {
    if (val === null || val === undefined) {
      getRows().forEach((el) => {
        el.classList.remove("selected-row");
        el.classList.remove("focused-row");
      });
    }
  }
);

// ── Infinite Scroll ───────────────────────────────────────────────────────────

const scrollSentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | null = null;

function getScrollContainer(): HTMLElement | null {
  return (
    tableWrapper.value?.querySelector(".v-table__wrapper") ||
    tableWrapper.value?.querySelector(".v-data-table__wrapper") ||
    null
  );
}

function observeScroll() {
  if (observer) observer.disconnect();

  setTimeout(() => {
    const root = getScrollContainer();
    if (!root || !scrollSentinel.value) return;

    observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !props.loading && props.hasMore) {
          emit("loadMore");
        }
      },
      { root, rootMargin: "200px", threshold: 0 }
    );
    observer.observe(scrollSentinel.value);
  }, 300);
}

watch(scrollSentinel, (el) => {
  if (el) observeScroll();
});

let resizeObserver: ResizeObserver | null = null;

function observeContainerResize() {
  setTimeout(() => {
    const el = getScrollContainer();
    if (!el) return;
    updateWrapperTabindex();
    resizeObserver?.disconnect();
    resizeObserver = new ResizeObserver(updateWrapperTabindex);
    resizeObserver.observe(el);
  }, 300);
}

onMounted(async () => {
  await nextTick();
  observeScroll();
  stripHeaderTabindex();
  observeContainerResize();
});

watch([() => props.headers, () => props.sortBy], () => {
  nextTick(stripHeaderTabindex);
});

watch(
  () => props.items,
  () => nextTick(updateWrapperTabindex)
);

onUnmounted(() => {
  if (observer) observer.disconnect();
  if (resizeObserver) resizeObserver.disconnect();
});

// ── Public API ────────────────────────────────────────────────────────────────

function resetSelection() {
  getRows().forEach((el) => {
    el.classList.remove("selected-row");
    el.classList.remove("focused-row");
  });
}

function triggerObserveScroll() {
  observeScroll();
}

defineExpose({ resetSelection, triggerObserveScroll });
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.scrollable-list-table-root {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.search-container {
  flex-shrink: 0;
  padding: 12px 16px;
  background: var(--v-theme-bg);
  border-bottom: 1px solid var(--v-theme-divider);
}

.keyboard-table-wrapper {
  flex: 1 1 auto;
  min-height: 0;
  max-height: 100%;
  overflow-x: auto;
  overflow-y: auto;
  display: flex;
  outline: none !important;
}

.keyboard-table-wrapper:focus,
.keyboard-table-wrapper:focus-visible {
  outline: none !important;
}

.scrollable-list-table {
  border-radius: 8px;
  background: rgb(var(--v-theme-bg_light)) !important;
}

:deep(.scrollable-list-table) {
  background-color: rgb(var(--v-theme-bg_light)) !important;
  box-shadow: none !important;
}

:deep(.scrollable-list-table) {
  overflow-x: auto !important;
  overflow-y: auto !important;
  max-height: 100% !important;
  height: 100% !important;
}

:deep(.scrollable-list-table table) {
  width: max-content !important;
  min-width: 100%;
  max-width: none !important;
  border-collapse: separate;
  border-spacing: 0;
}

/* Colspan rows (no-data / loading) must not inflate the table's max-content
   width based on their own long text - exclude them from the column-width
   calculation, they still stretch to the table's actual rendered width. */
:deep(.v-data-table-rows-no-data td),
:deep(.v-data-table-rows-loading td) {
  width: 1px;
}

:deep(.focused-row td) {
  box-shadow:
    inset 0 2px 0 0 #a6a4a6,
    inset 0 -2px 0 0 #a6a4a6;
}

.material-search-field {
  width: 100%;
  max-width: 100%;
}

:deep(.material-search-field) {
  border-radius: 28px !important;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.12),
    0 1px 2px rgba(0, 0, 0, 0.24);
}
</style>
