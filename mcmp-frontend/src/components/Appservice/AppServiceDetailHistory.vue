<template>
  <history
    :type="'user'"
    :history="history?.content || []"
    :loading="loading"
    :headers="headers"
    :page="page"
    :items-per-page="itemsPerPage"
    :server-side="true"
    :total-items="history?.page.totalElements || 0"
    title="Aufgaben"
    :show-refresh="true"
    @refresh="refreshJobs"
    @update:page="$emit('update:page', $event)"
    @update:items-per-page="$emit('update:items-per-page', $event)"
    @update:sort="$emit('update:sort', $event)"
  >
    <v-sheet
      rounded="lg"
      class="mb-0 filter-container"
    >
      <div class="text-caption text-medium-emphasis mb-1">
        Suche &amp; Filter
      </div>
      <v-row
        dense
        align="center"
      >
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <v-text-field
            v-model="searchText"
            label="Titel / Volltextsuche"
            rounded
            :prepend-inner-icon="mdiMagnify"
            density="compact"
            hide-details
            clearable
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <common-date-picker
            v-model="createdFrom"
            label="Erstellt von"
            rounded
            density="compact"
            text-field-class=""
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <common-date-picker
            v-model="createdTo"
            label="Erstellt bis"
            rounded
            density="compact"
            text-field-class=""
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="3"
          class="d-flex align-center"
        >
          <v-btn
            v-if="hasActiveFilters"
            variant="text"
            size="small"
            rounded
            :prepend-icon="mdiClose"
            @click="resetFilters"
          >
            Filter zurücksetzen
          </v-btn>
        </v-col>
      </v-row>
    </v-sheet>
  </history>
</template>

<script setup lang="ts">
import type JobList from "@/types/JobList";
import type { Page } from "@/types/Page";

import { mdiClose, mdiMagnify } from "@mdi/js";
import { computed, onUnmounted, ref, watch } from "vue";

import CommonDatePicker from "@/components/common/CommonDatePicker.vue";
import History from "@/components/common/History.vue";

defineProps<{
  history: Page<JobList> | null;
  loading: boolean;
  page: number;
  itemsPerPage: number;
}>();

export interface HistoryFilters {
  searchText: string | null;
  createdFrom: string | null;
  createdTo: string | null;
}

const emit = defineEmits<{
  (e: "refreshJobs"): void;
  (e: "update:page" | "update:items-per-page", value: number): void;
  (e: "update:sort", sort: { by: string; desc: boolean }): void;
  (e: "update:filters", filters: HistoryFilters): void;
}>();

const headers = ref([
  { title: "Job ID", key: "id" },
  { title: "Status", key: "status" },
  { title: "Titel", key: "title" },
  { title: "Erstellt am", key: "createdAt" },
  { title: "Startdatum", key: "changeStartDate" },
  { title: "Durchgeführt von", key: "userName" },
]);

const searchText = ref("");
const createdFrom = ref<string | null>(null);
const createdTo = ref<string | null>(null);

const hasActiveFilters = computed(
  () =>
    searchText.value !== "" ||
    createdFrom.value !== null ||
    createdTo.value !== null
);

function buildFilters(): HistoryFilters {
  return {
    searchText: searchText.value === "" ? null : searchText.value,
    createdFrom: createdFrom.value
      ? new Date(createdFrom.value).toISOString()
      : null,
    createdTo: createdTo.value ? new Date(createdTo.value).toISOString() : null,
  };
}

function emitFilters() {
  emit("update:filters", buildFilters());
}

function resetFilters() {
  searchText.value = "";
  createdFrom.value = null;
  createdTo.value = null;
}

let searchTextTimeout: ReturnType<typeof setTimeout> | null = null;
watch(searchText, () => {
  if (searchTextTimeout) clearTimeout(searchTextTimeout);
  searchTextTimeout = setTimeout(emitFilters, 400);
});

watch([createdFrom, createdTo], emitFilters);

onUnmounted(() => {
  if (searchTextTimeout) clearTimeout(searchTextTimeout);
});

function refreshJobs() {
  emit("refreshJobs");
}
</script>

<style scoped>
.filter-container {
  border: 1px solid rgba(128, 128, 128, 0.3);
  background: transparent !important;
}
</style>
