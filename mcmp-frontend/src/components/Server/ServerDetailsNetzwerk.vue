<template>
  <common-card title="vNics">
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
    </v-data-table>
  </common-card>
  <common-card
    title="Loadbalancer Mitgliedschaften"
    :loading="loadingLbMemberships"
    top-margin="0"
  >
    <v-data-table
      :loading="loadingLbMemberships"
      :headers="lbHeaders"
      :items="lbMemberships"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.vsDomain="{ item }">
        <div class="links">
          <router-link :to="`/loadbalancer/${item.vsId}`">{{
            item.vsDomain
          }}</router-link>
        </div>
      </template>
      <template #no-data>
        <span class="text-medium-emphasis"
          >Kein Loadbalancer Pool-Mitglied</span
        >
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type { LbServerMembership } from "@/types/LbServerMembership";
import type Nic from "@/types/Nic";

import { mdiCheckCircle, mdiCloseCircle } from "@mdi/js";

import CommonCard from "@/components/common/CommonCard.vue";

const props = defineProps<{
  nics: Nic[];
  loading: boolean;
  lbMemberships: LbServerMembership[];
  loadingLbMemberships: boolean;
}>();

const lbHeaders = [
  { title: "Domain", key: "vsDomain" },
  { title: "Pool", key: "poolName" },
  { title: "IP", key: "memberIp" },
  { title: "Port", key: "memberPort" },
];

const headers = [
  { title: "Verbunden", key: "connected" },
  { title: "Device", key: "device" },
  { title: "Card Typ", key: "cardType" },
  { title: "MAC", key: "macAddress" },
  { title: "Portgruppe", key: "portGroup" },
  { title: "VLAN", key: "vlan" },
  { title: "IPs", key: "ips" },
];

function formatIps(ip: string) {
  if (!ip) return "-";
  return ip;
}
</script>
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
