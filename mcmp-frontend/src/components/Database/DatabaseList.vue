<template>
  <div class="database-list-container">
    <scrollable-list-table
      :items="[]"
      :total-items="0"
      :loading="false"
      :headers="headers"
      :sort-by="sortBy"
      :has-more="false"
      :search="search"
      search-label="Datenbank suchen..."
      @update:sort-by="sortBy = $event"
      @update:search="search = $event"
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
import type { DataTableHeader } from "vuetify";

import { ref } from "vue";

import ScrollableListTable from "@/components/common/ScrollableListTable.vue";

const search = ref("");
const sortBy = ref<{ key: string; order: "asc" | "desc" }[]>([
  { key: "name", order: "asc" },
]);

const headers: DataTableHeader[] = [
  { title: "Name", key: "name", align: "start", sortable: true },
];
</script>

<style scoped>
.database-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
