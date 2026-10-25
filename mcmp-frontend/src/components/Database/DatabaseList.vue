<template>
  <div class="database-list-container">
    <scrollable-list-table
      :items="items"
      :total-items="items.length"
      :loading="false"
      :headers="headers"
      :sort-by="sortBy"
      :has-more="false"
      :search="search"
      search-label="Datenbank suchen..."
      @update:sort-by="sortBy = $event"
      @update:search="search = $event"
      @click:row="onRowClick"
      @row-click="onRowClick"
    >
      <template #no-data>
        <v-row />
        <v-row>
          <v-col>
            <v-alert type="info">
              <h2>Keine Datenbanken verfügbar</h2>
            </v-alert>
          </v-col>
        </v-row>
      </template>
    </scrollable-list-table>
  </div>
</template>

<script setup lang="ts">
import type { DatabaseListItem } from "@/types/Database";
import type { DataTableHeader } from "vuetify";

import { ref } from "vue";

import ScrollableListTable from "@/components/common/ScrollableListTable.vue";
import { databaseListItems } from "@/components/Database/databaseDummyData";

const emit = defineEmits<{
  (e: "update:selected", item: DatabaseListItem | null): void;
}>();

const search = ref("");
const sortBy = ref<{ key: string; order: "asc" | "desc" }[]>([
  { key: "name", order: "asc" },
]);

const headers: DataTableHeader[] = [
  { title: "Name", key: "name", align: "start", sortable: true },
  { title: "Typ", key: "engine", align: "start", sortable: true },
  { title: "FQDN", key: "fqdn", align: "start", sortable: true },
];

const items = ref<DatabaseListItem[]>([...databaseListItems]);

function resolveItem(value: unknown): DatabaseListItem | null {
  if (!value || typeof value !== "object") return null;

  if ("item" in value) {
    const inner = (value as { item?: DatabaseListItem }).item;
    return inner && typeof inner.id === "number" ? inner : null;
  }

  const candidate = value as DatabaseListItem;
  return typeof candidate.id === "number" && typeof candidate.name === "string"
    ? candidate
    : null;
}

/**
 * Unterstützt beide Varianten:
 * - Vuetify: click:row(event, { item, index })
 * - eigene Wrapper: row-click(item)
 */
function onRowClick(first: unknown, second?: unknown) {
  const item = resolveItem(second) ?? resolveItem(first);
  if (!item) return;
  emit("update:selected", item);
}
</script>

<style scoped>
.database-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
