<template>
  <v-container fluid>
    <!-- Kennzahlen (KPIs) -->
    <v-row dense class="mb-4">
      <v-col cols="12" sm="6" md="4">
        <v-sheet rounded="lg" class="pa-3 filter-container">
          <div class="text-caption text-medium-emphasis">
            Jemals angemeldet (Gesamt)
          </div>
          <div class="text-h5 font-weight-bold mt-1">
            {{ totalEverUsers !== null ? totalEverUsers : "–" }}
          </div>
        </v-sheet>
      </v-col>
      <v-col cols="12" sm="6" md="4">
        <v-sheet rounded="lg" class="pa-3 filter-container">
          <div class="text-caption text-medium-emphasis">
            Aktive Anwender*innen ({{ activePeriodLabel }})
          </div>
          <div class="text-h5 font-weight-bold mt-1">
            {{ totalItems }}
          </div>
        </v-sheet>
      </v-col>
    </v-row>

    <!-- Filter-Bereich -->
    <v-sheet
        rounded="lg"
        class="pa-4 mb-4 filter-container"
    >
      <div class="text-caption text-medium-emphasis mb-2">
        Aktive Anwender*innen im Zeitraum
      </div>
      <v-row dense align="center">
        <!-- Schnellfilter: 3, 6, 9, 12 Monate + Alle -->
        <v-col cols="12" md="auto" class="d-flex gap-2 flex-wrap">
          <v-btn
              v-for="months in [3, 6, 9, 12]"
              :key="months"
              size="small"
              :variant="activeQuickFilter === months ? 'flat' : 'outlined'"
              :color="activeQuickFilter === months ? 'primary' : undefined"
              @click="setMonthsFilter(months)"
          >
            {{ months }} Monate
          </v-btn>
          <v-btn
              size="small"
              :variant="isAllActive ? 'flat' : 'outlined'"
              :color="isAllActive ? 'primary' : undefined"
              @click="resetFilters"
          >
            Alle
          </v-btn>
        </v-col>

        <!-- Variabler Stichtag (bis heute) -->
        <v-col cols="12" sm="6" md="3">
          <common-date-picker
              v-model="searchUpdatedAtFrom"
              label="Aktiv seit (bis heute)"
              density="compact"
          />
        </v-col>

        <!-- Textsuche -->
        <v-col cols="12" sm="6" md>
          <v-text-field
              v-model="searchText"
              label="Suche nach Username, Name oder E-Mail"
              :prepend-inner-icon="mdiMagnify"
              density="compact"
              hide-details
              clearable
          />
        </v-col>

        <!-- Export-Button für BCC-Mailverteiler -->
        <v-col cols="12" md="auto">
          <v-btn
              :prepend-icon="mdiContentCopy"
              variant="outlined"
              color="primary"
              :loading="copyingEmails"
              :disabled="totalItems === 0"
              @click="copyEmailsToClipboard"
          >
            E-Mails für BCC kopieren
          </v-btn>
        </v-col>
      </v-row>
    </v-sheet>

    <!-- Server-Side Tabelle -->
    <v-card rounded="lg" variant="outlined">
      <v-data-table-server
          v-model:items-per-page="itemsPerPage"
          v-model:page="currentPage"
          :headers="headers"
          :items="users"
          :items-length="totalItems"
          :loading="loading"
          @update:options="onUpdateOptions"
      >
        <template #item.createdAt="{ item }">
          {{ formatDateTime(item.createdAt) }}
        </template>

        <template #item.updatedAt="{ item }">
          {{ formatDateTime(item.updatedAt) }}
        </template>
      </v-data-table-server>
    </v-card>

    <!-- Feedback-Banner beim Kopieren -->
    <v-snackbar
        v-model="snackbar.show"
        :color="snackbar.color"
        :timeout="4000"
        location="bottom right"
    >
      {{ snackbar.text }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
import type { UserHistoryItem } from "@/types/User";

import { mdiContentCopy, mdiMagnify } from "@mdi/js";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";

import userService from "@/api/userService";
import CommonDatePicker from "@/components/common/CommonDatePicker.vue";

const users = ref<UserHistoryItem[]>([]);
const loading = ref<boolean>(false);
const copyingEmails = ref<boolean>(false);

const searchText = ref("");
const searchUpdatedAtFrom = ref<string | null>(null);
const activeQuickFilter = ref<number | null>(null);
const isSettingQuickFilter = ref(false);

const totalEverUsers = ref<number | null>(null);

const refreshInterval = ref<ReturnType<typeof setInterval> | null>(null);

const currentPage = ref(1);
const itemsPerPage = ref(10);
const totalItems = ref(0);
const sortBy = ref<string | null>("updatedAt");
const sortDesc = ref(true);

const snackbar = ref({
  show: false,
  text: "",
  color: "success",
});

const isAllActive = computed(
    () => activeQuickFilter.value === null && !searchUpdatedAtFrom.value
);

const activePeriodLabel = computed(() => {
  if (activeQuickFilter.value !== null) {
    const formatted = searchUpdatedAtFrom.value
        ? new Date(searchUpdatedAtFrom.value).toLocaleDateString("de-DE")
        : "";
    return formatted
        ? `letzte ${activeQuickFilter.value} Monate, seit ${formatted} bis heute`
        : `letzte ${activeQuickFilter.value} Monate bis heute`;
  }
  if (searchUpdatedAtFrom.value) {
    const formatted = new Date(searchUpdatedAtFrom.value).toLocaleDateString("de-DE");
    return `seit ${formatted} bis heute`;
  }
  return "gesamter Zeitraum";
});

onMounted(() => {
  fetchTotalEverUsers();
  fetchUsers();
  startAutoRefresh();
});

onUnmounted(() => {
  stopAutoRefresh();
  if (searchTextTimeout) clearTimeout(searchTextTimeout);
});

watch(searchUpdatedAtFrom, () => {
  if (!isSettingQuickFilter.value) {
    activeQuickFilter.value = null;
  }
  currentPage.value = 1;
  fetchUsers();
  startAutoRefresh();
});

let searchTextTimeout: ReturnType<typeof setTimeout> | null = null;
watch(searchText, () => {
  if (searchTextTimeout) clearTimeout(searchTextTimeout);
  searchTextTimeout = setTimeout(() => {
    currentPage.value = 1;
    fetchUsers();
    startAutoRefresh();
  }, 400);
});

function onUpdateOptions(options: {
  page: number;
  itemsPerPage: number;
  sortBy: Array<{ key: string; order: "asc" | "desc" }>;
}) {
  currentPage.value = options.page;
  itemsPerPage.value = options.itemsPerPage;

  const firstSort = options.sortBy?.[0];
  if (firstSort) {
    sortBy.value = firstSort.key;
    sortDesc.value = firstSort.order === "desc";
  } else {
    sortBy.value = "updatedAt";
    sortDesc.value = true;
  }

  fetchUsers();
}

function formatDateTime(isoString: string | null | undefined): string {
  if (!isoString) return "-";
  return new Date(isoString).toLocaleString("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function toIsoStringStartOfDay(dateStr: string | null): string | null {
  if (!dateStr) return null;
  const date = new Date(dateStr);
  date.setHours(0, 0, 0, 0);
  return date.toISOString();
}

function getCurrentUpdatedFromIso(): string | null {
  if (activeQuickFilter.value !== null) {
    const fromDate = new Date();
    fromDate.setMonth(fromDate.getMonth() - activeQuickFilter.value);
    return fromDate.toISOString();
  }
  return toIsoStringStartOfDay(searchUpdatedAtFrom.value);
}

function fetchTotalEverUsers() {
  userService
      .searchUsers(loading, 1, 1, null, true, null, null)
      .then((res) => {
        totalEverUsers.value = res.page.totalElements || 0;
      });
}

function fetchUsers() {
  userService
      .searchUsers(
          loading,
          currentPage.value,
          itemsPerPage.value,
          sortBy.value,
          sortDesc.value,
          searchText.value === "" ? null : searchText.value,
          getCurrentUpdatedFromIso()
      )
      .then((res) => {
        users.value = res.content || [];
        totalItems.value = res.page.totalElements || 0;

        if (
            !searchText.value &&
            !searchUpdatedAtFrom.value &&
            activeQuickFilter.value === null
        ) {
          totalEverUsers.value = totalItems.value;
        }
      });
}

async function copyEmailsToClipboard() {
  try {
    const res = await userService.searchUsers(
        copyingEmails,
        1,
        10000,
        "username",
        false,
        searchText.value === "" ? null : searchText.value,
        getCurrentUpdatedFromIso()
    );

    const uniqueEmails = Array.from(
        new Set(
            (res.content || [])
                .map((u) => u.email?.trim())
                .filter((email): email is string => Boolean(email))
        )
    );

    if (uniqueEmails.length === 0) {
      snackbar.value = {
        show: true,
        text: "Keine gültigen E-Mail-Adressen im gewählten Zeitraum gefunden.",
        color: "warning",
      };
      return;
    }

    const bccString = uniqueEmails.join("; ");
    await navigator.clipboard.writeText(bccString);

    snackbar.value = {
      show: true,
      text: `${uniqueEmails.length} E-Mail-Adresse(n) für BCC in die Zwischenablage kopiert.`,
      color: "success",
    };
  } catch (err) {
    console.error("Fehler beim Kopieren der E-Mail-Adressen:", err);
    snackbar.value = {
      show: true,
      text: "Fehler beim Kopieren in die Zwischenablage.",
      color: "error",
    };
  }
}

function setMonthsFilter(months: number) {
  isSettingQuickFilter.value = true;
  activeQuickFilter.value = months;

  const fromDate = new Date();
  fromDate.setMonth(fromDate.getMonth() - months);
  searchUpdatedAtFrom.value = fromDate.toISOString().split("T")[0] ?? null;

  setTimeout(() => {
    isSettingQuickFilter.value = false;
  }, 0);
}

function resetFilters() {
  isSettingQuickFilter.value = true;
  activeQuickFilter.value = null;
  searchText.value = "";
  searchUpdatedAtFrom.value = null;

  setTimeout(() => {
    isSettingQuickFilter.value = false;
  }, 0);
}

function startAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
  }
  refreshInterval.value = setInterval(() => {
    fetchUsers();
  }, 60000);
}

function stopAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
    refreshInterval.value = null;
  }
}

const headers = ref([
  { title: "Username", key: "username" },
  { title: "Name", key: "name" },
  { title: "E-Mail", key: "email" },
  { title: "Erstkontakt", key: "createdAt" },
  { title: "Letzter Kontakt", key: "updatedAt" },
]);
</script>

<style scoped>
.filter-container {
  border: 1px solid rgba(128, 128, 128, 0.3);
  background: transparent !important;
}

/*noinspection CssUnusedSymbol*/
.filter-container :deep(.v-input__details) {
  display: none;
}

/*noinspection CssUnusedSymbol*/
.filter-container :deep(.v-input) {
  margin-top: 0 !important;
  margin-bottom: 0 !important;
}

.gap-2 {
  gap: 8px;
}
</style>