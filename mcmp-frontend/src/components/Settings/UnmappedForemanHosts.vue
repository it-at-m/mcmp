<template>
  <common-card title="Nicht zugeordnete Foreman-Hosts">
    <template #append-title>
      <count-badge :count="items.length" />
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
            :href="`https://${item.foreman}/new-hosts/${item.name}`"
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
import type { DataTableHeader } from "vuetify/framework";

import { onMounted, ref } from "vue";

import reportService from "@/api/reportService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";

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