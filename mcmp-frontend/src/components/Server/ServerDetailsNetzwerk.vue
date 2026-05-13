<template>
  <CommonCard title="vNics">
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="nics"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.connected="{ item }">
        <v-icon
          :color="item.connected ? '_green' : '_red'"
          size="x-large"
        >
          {{ item.connected ? mdiCheckCircle : mdiCloseCircle }}
        </v-icon>
      </template>
      <template #item.device="{ item }">
        {{ item.device }}
      </template>
      <template #item.cardType="{ item }">
        {{ item.cardType }}
      </template>
      <template #item.macAddress="{ item }">
        {{ item.macAddress }}
      </template>
      <template #item.portGroup="{ item }">
        {{ item.portGroup ? item.portGroup.name : "-" }}
      </template>
      <template #item.vlan="{ item }">
        {{ item.portGroup ? item.portGroup.vlan : "-" }}
      </template>
      <template #item.ips="{ item }">
        {{ formatIps(item.toolsIpAddress) }}
      </template>
      <!--
            <template #item.edit="{ item }">
              <v-btn
                text
                @click="editNic(item)"
              >
                <v-icon size="x-large">{{ mdiCircleEditOutline }}</v-icon>
              </v-btn>
            </template>
            -->
    </v-data-table>
  </CommonCard>
</template>

<script setup lang="ts">
import type Nic from "@/types/Nic";

import { mdiCheckCircle, mdiCloseCircle } from "@mdi/js";

import CommonCard from "@/components/common/CommonCard.vue";

const props = defineProps<{
  nics: Nic[];
  loading: boolean;
}>();

const headers = [
  { title: "Verbunden", key: "connected" },
  { title: "Device", key: "device" },
  { title: "Card Typ", key: "cardType" },
  { title: "MAC", key: "macAddress" },
  { title: "Portgruppe", key: "portGroup" },
  { title: "VLAN", key: "vlan" },
  { title: "IPs", key: "ips" },
  // { title: "Bearbeiten", key: "edit", sortable: false },
];

function formatIps(ip: string) {
  if (!ip) return "-";
  return ip;
}

function editNic(nic: Nic) {
  // Dummy API request
  alert(`Edit NIC: ${nic.device}`);
}
</script>
