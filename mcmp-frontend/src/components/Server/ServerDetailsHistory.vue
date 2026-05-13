<template>
  <history
    :type="'user'"
    :history="history?.content || []"
    :loading="loading"
    :headers="headers"
    :page="page"
    :items-per-page="itemsPerPage"
    :serverSide="true"
    :totalItems="history?.page.totalElements || 0"
    title="Aufgaben"
    :show-refresh="true"
    @refresh="refreshJobs"
    @update:page="$emit('update:page', $event)"
    @update:items-per-page="$emit('update:items-per-page', $event)"
    @update:sort="$emit('update:sort', $event)"
  />
</template>

<script setup lang="ts">
import type JobList from "@/types/JobList";
import type { Page } from "@/types/Page";

import { ref } from "vue";

import History from "@/components/common/History.vue";

defineProps<{
  history: Page<JobList> | null;
  loading: boolean;
  page: number;
  itemsPerPage: number;
}>();

const emit = defineEmits<{
  (e: "refreshJobs"): void;
  (e: "update:page" | "update:items-per-page", value: number): void;
  (e: "update:sort", sort: { by: string; desc: boolean }): void;
}>();

const headers = ref([
  { title: "Job ID", key: "id" },
  { title: "Status", key: "status" },
  { title: "Titel", key: "title" },
  { title: "Erstellt am", key: "createdAt" },
  { title: "Startdatum", key: "changeStartDate" },
  { title: "Durchgeführt von", key: "userName" },
]);

function refreshJobs() {
  emit("refreshJobs");
}
</script>
