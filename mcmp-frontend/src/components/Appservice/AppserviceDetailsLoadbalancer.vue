<template>
  <common-card
    v-if="loadbalancers.length"
    :title="cardTitle"
    top-margin="0"
    :is-default-expanded="false"
  >
    <template #append-title>
      <count-badge :count="loadbalancerCount" />
    </template>
    <v-data-table
      :headers="headers"
      :items="loadbalancers"
      :items-per-page="-1"
      density="compact"
      class="elevation-1"
      hide-default-footer
    >
      <template #item.name="{ item }">
        <div class="links">
          <router-link :to="`/loadbalancer/${item.id}`">
            {{ item.name }}
          </router-link>
        </div>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice";
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem";

import { computed, ref, watch } from "vue";

import loadbalancerService from "@/api/loadbalancerService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const loadbalancers = ref<LoadbalancerListItem[]>([]);
const loading = ref(false);

const cardTitle = computed(() => "Loadbalancer");
const loadbalancerCount = computed(() => loadbalancers.value.length);

const headers = [
  { title: "Name", key: "name" },
  { title: "Domain", key: "domain" },
  { title: "Listen", key: "listen" },
  { title: "Port", key: "port" },
];

async function loadLoadbalancers(appservice: Appservice | null) {
  if (!appservice) {
    loadbalancers.value = [];
    return;
  }
  loadbalancers.value = [];
  const result = await loadbalancerService.getLoadbalancersByAppserviceId(
    loading,
    appservice.id
  );
  if (props.selectedAppservice?.id === appservice.id) {
    loadbalancers.value = result;
  }
}

watch(
  () => props.selectedAppservice,
  (appservice) => {
    void loadLoadbalancers(appservice);
  },
  { immediate: true }
);
</script>

<!--suppress CssUnresolvedCustomProperty -->
