<template>
  <history
    :type="'admin'"
    :history="history"
    :loading="loading"
    :headers="headers"
    :page="currentPage"
    :items-per-page="itemsPerPage"
    :total-items="totalItems"
    server-side
    title="Admin History"
    :show-refresh="true"
    @refresh="fetchHistory"
    @update:page="currentPage = $event"
    @update:items-per-page="itemsPerPage = $event"
    @update:sort="onSort"
  >
    <!-- Filter-Bereich -->
    <v-sheet
      rounded="lg"
      class="pa-4 mb-4 filter-container"
    >
      <!-- Zeile 1: Suchfelder -->
      <div class="text-caption text-medium-emphasis mb-2">Suche & Filter</div>
      <v-row dense>
        <v-col
          cols="12"
          sm="6"
          md="1"
        >
          <v-text-field
            v-model="searchJobId"
            label="Job ID"
            :prepend-inner-icon="mdiMagnify"
            density="compact"
            hide-details
            clearable
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="1"
        >
          <v-text-field
            v-model="searchAwxJobId"
            label="AWX Job ID"
            :prepend-inner-icon="mdiMagnify"
            density="compact"
            hide-details
            clearable
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="2"
        >
          <user-autocomplete-field
            v-model="searchUsername"
            label="Benutzername (min. 3 Zeichen)"
            :loading="loading"
            density="compact"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="2"
        >
          <server-autocomplete-field
            v-model="searchServerName"
            label="Servername (min. 3 Zeichen)"
            :loading="loading"
            density="compact"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="2"
        >
          <v-select
            v-model="searchActionIdentifier"
            :items="allActionIdentifiers"
            label="Job-Typ"
            density="compact"
            hide-details
            clearable
            multiple
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="2"
        >
          <v-select
            v-model="searchStatusIdentifier"
            :items="allStatusIdentifiers"
            label="Job Status"
            density="compact"
            hide-details
            clearable
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="2"
        >
          <v-text-field
            v-model="searchAwxVariables"
            label="AWX-Variables"
            :prepend-inner-icon="mdiMagnify"
            density="compact"
            hide-details
            clearable
          />
        </v-col>
      </v-row>

      <!-- Zeile 2: Datumsfilter -->
      <div class="text-caption text-medium-emphasis mb-2">Zeitraum</div>
      <v-row
        dense
        align="center"
      >
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <common-date-picker
            v-model="searchCreatedAtFrom"
            label="Erstellt von"
            density="compact"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <common-date-picker
            v-model="searchCreatedAtTo"
            label="Erstellt bis"
            density="compact"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <common-date-picker
            v-model="searchChangeStartDateFrom"
            label="Startdatum von"
            density="compact"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
          md="3"
        >
          <common-date-picker
            v-model="searchChangeStartDateTo"
            label="Startdatum bis"
            density="compact"
          />
        </v-col>
      </v-row>
    </v-sheet>
  </history>
</template>

<script setup lang="ts">
import type JobList from "@/types/JobList.ts";
import type { ServerAutocomplete } from "@/types/ServerAutocomplete";
import type { UserAutocomplete } from "@/types/UserAutocomplete";

import { mdiMagnify } from "@mdi/js";
import { onMounted, onUnmounted, ref, watch } from "vue";

import jobService from "@/api/jobService";
import CommonDatePicker from "@/components/common/CommonDatePicker.vue";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import History from "@/components/common/History.vue";
import ServerAutocompleteField from "@/components/common/ServerAutocompleteField.vue";
import UserAutocompleteField from "@/components/common/UserAutocompleteField.vue";

const history = ref<JobList[]>([]);
const loading = ref<boolean>(false);
const searchJobId = ref("");
const searchAwxJobId = ref("");
const searchUsername = ref<UserAutocomplete | null>(null);
const searchServerName = ref<ServerAutocomplete | null>(null);
const searchActionIdentifier = ref<string[]>([]);
const searchStatusIdentifier = ref("");
const searchAwxVariables = ref("");
const searchCreatedAtFrom = ref<string | null>(null);
const searchCreatedAtTo = ref<string | null>(null);
const searchChangeStartDateFrom = ref<string | null>(null);
const searchChangeStartDateTo = ref<string | null>(null);
const refreshInterval = ref<NodeJS.Timeout | null>(null);
const allActionIdentifiers = ref<string[]>([]);
const allStatusIdentifiers = ref<string[]>([]);

// Paginierungszustand hier verwalten
const currentPage = ref(1);
const itemsPerPage = ref(10);
const totalItems = ref(0);
const sortBy = ref<string | null>(null);
const sortDesc = ref(false);
const hasLoaded = ref(false);

onMounted(() => {
  loadAutocompleteData().then(() => {
    hasLoaded.value = true;
  });
});

watch(hasLoaded, (newVal) => {
  if (newVal) {
    fetchHistory();
    startAutoRefresh();
  }
});

onUnmounted(() => {
  stopAutoRefresh();
});

watch(
  [
    searchJobId,
    searchAwxJobId,
    searchUsername,
    searchServerName,
    searchActionIdentifier,
    searchStatusIdentifier,
    searchAwxVariables,
    searchCreatedAtFrom,
    searchCreatedAtTo,
    searchChangeStartDateFrom,
    searchChangeStartDateTo,
  ],
  () => {
    // Bei neuer Suche auf erste Seite zurücksetzen
    currentPage.value = 1;
    fetchHistory();
    startAutoRefresh();
  }
);

watch([currentPage, itemsPerPage], () => {
  fetchHistory();
});

function onSort(sort: { by: string; desc: boolean }) {
  sortBy.value = sort.by;
  sortDesc.value = sort.desc;
  fetchHistory();
}

function fetchHistory() {
  jobService
    .searchJobs(
      loading,
      currentPage.value,
      itemsPerPage.value,
      sortBy.value,
      sortDesc.value,
      searchJobId.value === "" ? null : searchJobId.value,
      searchAwxJobId.value === "" ? null : searchAwxJobId.value,
      searchCreatedAtFrom.value
        ? new Date(searchCreatedAtFrom.value).toISOString()
        : null,
      searchCreatedAtTo.value
        ? new Date(searchCreatedAtTo.value).toISOString()
        : null,
      searchChangeStartDateFrom.value
        ? new Date(searchChangeStartDateFrom.value).toISOString()
        : null,
      searchChangeStartDateTo.value
        ? new Date(searchChangeStartDateTo.value).toISOString()
        : null,
      searchUsername.value?.id || null,
      searchServerName.value?.id || null,
      null,
      searchAwxVariables.value === "" ? null : searchAwxVariables.value,
      searchActionIdentifier.value.length === 0
        ? null
        : searchActionIdentifier.value,
      searchStatusIdentifier.value === "" ? null : searchStatusIdentifier.value
    )
    .then((res) => {
      history.value = res.content || [];
      totalItems.value = res.page.totalElements || 0;
    });
}

function loadAutocompleteData() {
  return Promise.all([
    jobService.getAllActionIdentifiers(loading).then((identifiers) => {
      allActionIdentifiers.value = identifiers;
    }),
    jobService.getAllStatusIdentifiers(loading).then((identifiers) => {
      allStatusIdentifiers.value = identifiers;
    }),
  ]);
}

function startAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
  }
  refreshInterval.value = setInterval(() => {
    fetchHistory();
  }, 60000);
}

function stopAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
    refreshInterval.value = null;
  }
}

const headers = ref([
  { title: "Job ID", key: "id" },
  { title: "Status", key: "status" },
  { title: "Titel", key: "title" },
  { title: "Erstellt am", key: "createdAt" },
  { title: "Startdatum", key: "changeStartDate" },
  { title: "Loginname", key: "userName" },
  { title: "Server", key: "serverName" },
]);
</script>

<style scoped>
.filter-container {
  border: 1px solid rgba(128, 128, 128, 0.3);
  background: transparent !important;
}
</style>
