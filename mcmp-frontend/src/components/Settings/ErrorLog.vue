<template>
  <common-card
    title="Error Logs"
    disable-expansion
  >
    <template #toolbar-actions>
      <v-btn
        :icon="mdiRefresh"
        variant="text"
        @click="fetchErrorLogs"
      />
    </template>

    <v-text-field
      v-model="searchReference"
      label="Fehlercode suchen (z. B. ERR-3F8K2QZ)"
      :prepend-inner-icon="mdiMagnify"
      density="compact"
      clearable
      hide-details
      class="mb-4"
    />

    <v-data-table-server
      :headers="headers"
      :items="errorLogs"
      :items-length="totalItems"
      :loading="loading"
      :items-per-page="itemsPerPage"
      :page="currentPage"
      item-value="id"
      @update:options="onUpdateOptions"
    >
      <template #item.createdAt="{ item }">
        {{ formatDate(item.createdAt) }}
      </template>

      <template #item.referenceId="{ item }">
        <code class="reference-id">{{ item.referenceId }}</code>
      </template>

      <template #item.requestMethod="{ item }">
        <v-chip
          v-if="item.requestMethod"
          :color="methodColor(item.requestMethod)"
          size="small"
          label
          variant="flat"
        >
          {{ item.requestMethod }}
        </v-chip>
        <span v-else>-</span>
      </template>

      <template #item.exceptionClass="{ item }">
        <v-chip
          :color="severityColor(item.exceptionClass)"
          size="small"
          variant="flat"
        >
          {{ shortClassName(item.exceptionClass) }}
        </v-chip>
      </template>

      <template #item.actions="{ item }">
        <v-btn
          color="blue"
          size="small"
          rounded="xl"
          @click="showDetails(item)"
        >
          Details
        </v-btn>
      </template>
    </v-data-table-server>

    <common-dialog
      v-model="detailsDialog"
      title="Fehlerdetails"
      :icon="mdiAlertCircleOutline"
      max-width="900"
      show-actions
      submit-activated
      @dialog-cancel="detailsDialog = false"
    >
      <div
        v-if="detailsLoading && !selectedError"
        class="d-flex justify-center pa-8"
      >
        <v-progress-circular
          indeterminate
          color="accent"
        />
      </div>

      <template v-if="selectedError">
        <div class="detail-row">
          <span class="detail-label">Fehlercode</span>
          <code class="reference-id">{{ selectedError.referenceId }}</code>
        </div>
        <div class="detail-row">
          <span class="detail-label">Zeitpunkt</span>
          <span>{{ formatDate(selectedError.createdAt) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Benutzer</span>
          <span>{{ selectedError.username || "-" }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Request</span>
          <span>
            <v-chip
              v-if="selectedError.requestMethod"
              :color="methodColor(selectedError.requestMethod)"
              size="small"
              label
              variant="flat"
              class="mr-2"
            >
              {{ selectedError.requestMethod }}
            </v-chip>
            {{ selectedError.requestPath || "-"
            }}{{
              selectedError.requestQuery ? `?${selectedError.requestQuery}` : ""
            }}
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Exception</span>
          <v-chip
            :color="severityColor(selectedError.exceptionClass)"
            size="small"
            variant="flat"
          >
            {{ selectedError.exceptionClass }}
          </v-chip>
        </div>
        <div class="detail-row">
          <span class="detail-label">Nachricht</span>
          <span>{{ selectedError.message || "-" }}</span>
        </div>

        <template v-if="selectedError.requestBody">
          <div class="text-subtitle-2 mt-4 mb-1">Request Body</div>
          <pre class="stacktrace">{{ selectedError.requestBody }}</pre>
        </template>

        <div class="text-subtitle-2 mt-4 mb-1">Stacktrace</div>
        <pre class="stacktrace">{{ selectedError.stacktrace }}</pre>
      </template>

      <template #actions>
        <v-spacer />
        <v-btn
          color="primary"
          size="large"
          rounded="xl"
          class="px-8"
          @click="detailsDialog = false"
        >
          Schließen
        </v-btn>
      </template>
    </common-dialog>
  </common-card>
</template>

<script setup lang="ts">
import type { ErrorLog } from "@/types/ErrorLog";

import { mdiAlertCircleOutline, mdiMagnify, mdiRefresh } from "@mdi/js";
import { ref, watch } from "vue";

import errorLogService from "@/api/errorLogService";
import CommonCard from "@/components/common/CommonCard.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";

const errorLogs = ref<ErrorLog[]>([]);
const loading = ref<boolean>(false);
const detailsLoading = ref<boolean>(false);
const currentPage = ref(1);
const itemsPerPage = ref(20);
const totalItems = ref(0);
const searchReference = ref("");
const detailsDialog = ref(false);
const selectedError = ref<ErrorLog | null>(null);

const headers = ref([
  { title: "Zeitpunkt", key: "createdAt" },
  { title: "Fehlercode", key: "referenceId" },
  { title: "Typ", key: "exceptionClass" },
  { title: "Nachricht", key: "message" },
  { title: "Methode", key: "requestMethod" },
  { title: "Pfad", key: "requestPath" },
  { title: "Benutzer", key: "username" },
  { title: "", key: "actions", sortable: false },
]);

function onUpdateOptions(options: { page: number; itemsPerPage: number }) {
  currentPage.value = options.page;
  itemsPerPage.value = options.itemsPerPage;
  fetchErrorLogs();
}

function fetchErrorLogs() {
  const reference = searchReference.value.trim();
  const request = reference
    ? errorLogService.searchByReference(loading, reference)
    : errorLogService.getErrorLogs(
        loading,
        currentPage.value - 1,
        itemsPerPage.value
      );

  request.then((res) => {
    errorLogs.value = res.content || [];
    totalItems.value = res.page.totalElements || 0;
  });
}

watch(searchReference, () => {
  currentPage.value = 1;
  fetchErrorLogs();
});

function showDetails(item: ErrorLog) {
  selectedError.value = null;
  detailsDialog.value = true;
  errorLogService.getErrorLogDetail(detailsLoading, item.id).then((detail) => {
    selectedError.value = detail;
  });
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString("de-DE");
}

function shortClassName(fqcn: string): string {
  const parts = fqcn.split(".");
  return parts[parts.length - 1] ?? fqcn;
}

function methodColor(method: string): string {
  switch (method.toUpperCase()) {
    case "GET":
      return "accent";
    case "POST":
      return "success";
    case "PUT":
    case "PATCH":
      return "warning";
    case "DELETE":
      return "error";
    default:
      return "secondary";
  }
}

function severityColor(exceptionClass: string): string {
  const name = shortClassName(exceptionClass);
  if (/AccessDenied/.test(name)) {
    return "warning";
  }
  if (/NotFound/.test(name)) {
    return "info";
  }
  if (
    /Validation|IllegalArgument|MissingFormatArgument|NotReadable/.test(name)
  ) {
    return "secondary";
  }
  return "error";
}
</script>

<style scoped>
.detail-row {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid rgba(128, 128, 128, 0.15);
}

.detail-label {
  min-width: 110px;
  font-weight: 600;
  opacity: 0.75;
}

.reference-id {
  font-family: monospace;
  font-size: 0.85rem;
  padding: 2px 8px;
  border-radius: 6px;
  /* noinspection CssUnresolvedCustomProperty */
  background: rgb(var(--v-theme-bg));
}

.stacktrace {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow-y: auto;
  font-size: 0.75rem;
  padding: 12px;
  border-radius: 8px;
  /* noinspection CssUnresolvedCustomProperty */
  background: rgb(var(--v-theme-bg));
}
</style>
