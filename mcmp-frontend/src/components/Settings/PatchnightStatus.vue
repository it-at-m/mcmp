<template>
  <common-card title="Fehlerhafte Patchnight Updates">
    <v-data-table
      :headers="headers"
      :items="tableItems"
      class="elevation-1"
      :loading="loading"
      :no-data-text="$t('components.patchnightStatus.noDataText')"
      :sort-by="sortBy"
      :items-per-page="10"
    >
      <template #item.osSortKey="{ item }">
        <os-cell
          :osFullName="item.osFullName"
          size="small"
          class="os-icon-inline"
        />
      </template>

      <template #item.name="{ item }">
        <router-link
          class="links"
          :to="`/server/${item.id}`"
        >
          {{ item.name }}
        </router-link>
      </template>

      <template #item.patchnightExitcodeChangeDate="{ item }">
        {{ formatter.formatToBerlinDate(item.patchnightExitcodeChangeDate) }}
      </template>

      <template #item.patchnightExitstring="{ item }">
        <div class="exitstring-cell">
          {{ item.patchnightExitstring }}
        </div>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Server from "@/types/Server";
import type { DataTableHeader } from "vuetify/framework";

import { computed, onMounted, ref } from "vue";

import serverService from "@/api/serverService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import OsCell from "@/components/Server/OsCell.vue";
import { useFormatter } from "@/composables/formatter";

type PatchnightStatusRow = Server & {
  osFullName: string;
  osSortKey: string;
};

const formatter = useFormatter();

const loading = ref(false);
const servers = ref<Server[]>([]);

const sortBy = ref([{ key: "name", order: "asc" as "asc" | "desc" }]);

const headers = ref<DataTableHeader[]>([
  {
    title: "OS",
    key: "osSortKey",
    align: "start",
    sortable: true,
    width: "44px",
  },
  {
    title: "Servername",
    key: "name",
    align: "start",
    sortable: true,
  },
  {
    title: "gemeldet am",
    key: "patchnightExitcodeChangeDate",
    align: "start",
    sortable: true,
  },
  {
    title: "Exitcode",
    key: "patchnightExitcode",
    align: "start",
    sortable: true,
  },
  {
    title: "Fehlermeldung",
    key: "patchnightExitstring",
    align: "start",
    sortable: false,
  },
]);

function getOsFullName(item: Server): string {
  return item.guestToolsFullName || item.guestConfigFullName || "";
}

const tableItems = computed<PatchnightStatusRow[]>(() =>
  servers.value.map((s) => {
    const osFullName = getOsFullName(s);
    return {
      ...s,
      osFullName,
      osSortKey: osFullName.toLowerCase(),
    };
  })
);

function getServers() {
  serverService.getPatchnightErrorServers(loading).then((response) => {
    servers.value = response;
  });
}

onMounted(() => {
  getServers();
});
</script>

<style scoped>
.exitstring-cell {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.35;
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
  min-width: 30px !important;
  height: 30px !important;
  max-height: 30px !important;
  object-fit: contain !important;
  margin: 0 !important;
  padding: 0 !important;
}

:deep(.os-icon-inline img),
:deep(.os-icon-inline .os-icon) {
  width: 30px !important;
  height: 30px !important;
  max-width: 30px !important;
  max-height: 30px !important;
  object-fit: contain !important;
  display: block !important;
  margin: 0 auto !important;
  padding: 0 !important;
}

:deep(a.links),
:deep(a.links:visited),
:deep(a.links:hover),
:deep(a.links:active) {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
