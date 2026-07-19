<template>
  <history
    :type="'user'"
    :history="history"
    :loading="loading"
    :headers="headers"
    :page="currentPage"
    :items-per-page="itemsPerPage"
    :total-items="totalItems"
    server-side
    title="Aufgaben"
    :show-refresh="true"
    @refresh="fetchHistory"
    @update:page="currentPage = $event"
    @update:items-per-page="itemsPerPage = $event"
    @update:sort="onSort"
  />
</template>

<script setup lang="ts">
import type JobList from "@/types/JobList.ts";

import { onMounted, onUnmounted, ref, watch } from "vue";

import jobService from "@/api/jobService";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import History from "@/components/common/History.vue";

const history = ref<JobList[]>([]);
const loading = ref(false);
const refreshInterval = ref<NodeJS.Timeout | null>(null);
const totalItems = ref(0);

const currentPage = ref(1);
const itemsPerPage = ref(10);
const sortBy = ref<string | null>("id");
const sortDesc = ref(true);

const emit = defineEmits<{
  getNotification: [];
}>();

onMounted(() => {
  fetchHistory();
  startAutoRefresh();
  resetNotification();
});

onUnmounted(() => {
  stopAutoRefresh();
});

watch([currentPage, itemsPerPage], () => {
  fetchHistory();
});

function resetNotification(): void {
  jobService.resetNotifications(loading).then(() => {
    emit("getNotification");
  });
}

function fetchHistory() {
  jobService
    .getJobsByUsername(
      loading,
      currentPage.value,
      itemsPerPage.value,
      sortBy.value,
      sortDesc.value
    )
    .then((jobs) => {
      history.value = jobs.content;
      totalItems.value = jobs.page.totalElements;
    });
}

function onSort(sort: { by: string; desc: boolean }) {
  sortBy.value = sort.by;
  sortDesc.value = sort.desc;
  fetchHistory();
}

function startAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
  }

  refreshInterval.value = setInterval(() => {
    fetchHistory();
    resetNotification();
  }, 60000);
}

function stopAutoRefresh() {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value);
    refreshInterval.value = null;
  }
}

const headers = ref([
  { title: "Job-ID", key: "id" },
  { title: "Status", key: "status" },
  { title: "Titel", key: "title" },
  { title: "Erstellt am", key: "createdAt" },
  { title: "Startdatum", key: "changeStartDate" },
  { title: "Server", key: "serverName" },
]);
</script>
