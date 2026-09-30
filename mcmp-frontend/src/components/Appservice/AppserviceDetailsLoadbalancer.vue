<template>
  <common-card
    :title="cardTitle"
    top-margin="0"
    :is-default-expanded="false"
  >
    <template #append-title>
      <count-badge :count="loadbalancerCount" />
    </template>

    <template #toolbar-actions>
      <div class="action-buttons">
        <loadbalancer-order
          v-if="props.selectedAppservice"
          :key="props.selectedAppservice.id"
          :appservice="props.selectedAppservice"
          @order-done="loadLoadbalancers(props.selectedAppservice)"
        >
          <template #activator="{ props: activatorProps }">
            <v-tooltip
              location="top"
              text="zusätzlichen Loadbalancer bestellen"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  v-bind="{ ...activatorProps, ...tooltipProps }"
                  icon
                  flat
                  aria-label="zusätzlichen Loadbalancer bestellen"
                >
                  <v-icon :icon="mdiPlus" />
                </v-btn>
              </template>
            </v-tooltip>
          </template>
        </loadbalancer-order>
      </div>
    </template>

    <v-data-table
      :headers="headers"
      :items="loadbalancers"
      :loading="loading"
      :items-per-page="-1"
      density="compact"
      class="elevation-1"
      hide-default-footer
    >
      <template #no-data>
        <div class="py-4 text-center text-medium-emphasis">
          Keine Loadbalancer vorhanden
        </div>
      </template>

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

import { mdiPlus } from "@mdi/js";
import { computed, ref, watch } from "vue";

import loadbalancerService from "@/api/loadbalancerService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import LoadbalancerOrder from "@/components/Loadbalancer/LoadbalancerOrder.vue";

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
<style scoped>
.action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
