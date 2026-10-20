<template>
  <common-card title="Nicht zugeordnete Foreman-Hosts">
    <template #append-title>
      <div class="d-flex align-center ga-2">
        <count-badge :count="items.length" />
        <v-btn
          size="small"
          variant="tonal"
          color="green-darken-2"
          :prepend-icon="mdiMicrosoftExcel"
          :disabled="items.length === 0"
          @click.stop="exportCsv"
          @mousedown.stop
        >
          CSV Export
        </v-btn>
      </div>
    </template>

    <v-data-table
      :headers="headers"
      :items="items"
      class="elevation-1 links"
      :loading="loading"
      :sort-by="sortBy"
      :items-per-page="25"
    >
      <template #no-data>
        <div
          v-if="!loading"
          class="wrapping-cell py-4 text-center"
        >
          Keine nicht zugeordneten Foreman-Hosts gefunden!
        </div>
      </template>

      <template #item.foreman="{ item }">
        <div class="wrapping-cell">{{ getHostname(item.foreman) }}</div>
      </template>

      <template #item.name="{ item }">
        <div class="wrapping-cell">
          <a
            :href="`https://${item.foreman}/new/hosts/${item.name}`"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ item.name }}
          </a>
        </div>
      </template>

      <template #item.operatingsystem="{ item }">
        <div class="wrapping-cell">{{ item.operatingsystem }}</div>
      </template>

      <template #item.uuid="{ item }">
        <div class="wrapping-cell">{{ item.uuid }}</div>
      </template>

      <template #item.dmi_product_uuid="{ item }">
        <div class="wrapping-cell">{{ item.dmi_product_uuid }}</div>
      </template>

      <template #item.dmi_product_serial_number="{ item }">
        <div class="wrapping-cell">{{ item.dmi_product_serial_number }}</div>
      </template>

      <template #item.mac="{ item }">
        <div class="wrapping-cell">{{ item.mac }}</div>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type UnmappedForemanHost from "@/types/UnmappedForemanHost";
import type { CsvColumn, CsvValue } from "@/util/csv";
import type { DataTableHeader } from "vuetify/framework";

import { mdiMicrosoftExcel } from "@mdi/js";
import { onMounted, ref } from "vue";

import reportService from "@/api/reportService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import { buildCsv, downloadCsv } from "@/util/csv";

const loading = ref(false);
const items = ref<UnmappedForemanHost[]>([]);

const sortBy = ref([{ key: "name", order: "asc" as "asc" | "desc" }]);

const headers = ref<DataTableHeader[]>([
  {
    title: "Foreman",
    key: "foreman",
    align: "start",
    sortable: true,
  },
  {
    title: "Name",
    key: "name",
    align: "start",
    sortable: true,
  },
  {
    title: "Betriebssystem",
    key: "operatingsystem",
    align: "start",
    sortable: true,
  },
  {
    title: "Foreman-ID",
    key: "foreman_id",
    align: "start",
    sortable: true,
  },
  {
    title: "UUID",
    key: "uuid",
    align: "start",
    sortable: true,
  },
  {
    title: "DMI Product SerialNumber",
    key: "dmi_product_serial_number",
    align: "start",
    sortable: false,
  },
  {
    title: "DMI Product UUID",
    key: "dmi_product_uuid",
    align: "start",
    sortable: false,
  },
  {
    title: "MAC",
    key: "mac",
    align: "start",
    sortable: false,
  },
]);

// Explicitly typed columns for the CSV export. Keeping them in sync with
// the table headers avoids optional `key`/`title` handling and casts.
const csvColumns: CsvColumn<UnmappedForemanHost>[] = [
  { header: "Foreman", value: (item) => item.foreman },
  { header: "Name", value: (item) => item.name },
  { header: "Betriebssystem", value: (item) => item.operatingsystem },
  { header: "Foreman-ID", value: (item) => item.foreman_id },
  { header: "UUID", value: (item) => item.uuid },
  {
    header: "DMI Product SerialNumber",
    value: (item) => item.dmi_product_serial_number,
  },
  { header: "DMI Product UUID", value: (item) => item.dmi_product_uuid },
  { header: "MAC", value: (item) => item.mac },
];

function getUnmappedForemanHosts() {
  reportService
    .getUnmappedForemanHosts(loading)
    .then((response) => {
      items.value = response;
    })
    .catch((error) => {
      console.debug(
        "Fehler beim Laden der nicht zugeordneten Foreman-Hosts:",
        error
      );
    });
}

function getHostname(fqdn: string): string {
  if (!fqdn) {
    return "";
  }
  const firstDot = fqdn.indexOf(".");
  return firstDot === -1 ? fqdn : fqdn.substring(0, firstDot);
}

function exportCsv() {
  if (items.value.length === 0) {
    return;
  }

  const date = new Date().toISOString().slice(0, 10);
  const filename = `nicht-zugeordnete-foreman-hosts_${date}.csv`;

  downloadCsv(filename, buildCsv(items.value, csvColumns));
}

onMounted(() => {
  getUnmappedForemanHosts();
});
</script>

<style scoped>
.wrapping-cell {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.35;
}
</style>
