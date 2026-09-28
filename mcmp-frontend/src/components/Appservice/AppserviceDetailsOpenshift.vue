<template>
  <common-card
    :title="cardTitle"
    top-margin="0"
    :is-default-expanded="false"
  >
    <template #append-title>
      <count-badge :count="namespaceCount" />
    </template>

    <template #toolbar-actions>
      <div class="action-buttons">
        <openshift-namespace-order
          v-if="props.selectedAppservice"
          :key="props.selectedAppservice.id"
          :appservice="props.selectedAppservice"
          @order-done="loadNamespaces(props.selectedAppservice)"
        >
          <template #activator="{ props: activatorProps }">
            <v-tooltip
              location="top"
              text="zusätzlichen Namespace bestellen"
            >
              <template #activator="{ props: tooltipProps }">
                <v-btn
                  v-bind="{ ...activatorProps, ...tooltipProps }"
                  icon
                  flat
                  aria-label="zusätzlichen Namespace bestellen"
                >
                  <v-icon :icon="mdiPlus" />
                </v-btn>
              </template>
            </v-tooltip>
          </template>
        </openshift-namespace-order>
      </div>
    </template>

    <v-data-table
      :headers="headers"
      :items="namespaces"
      :loading="loading"
      :items-per-page="-1"
      density="compact"
      class="elevation-1"
      hide-default-footer
    >
      <template #no-data>
        <div class="py-4 text-center text-medium-emphasis">
          Keine Namespaces vorhanden
        </div>
      </template>
      <template #item.name="{ item }">
        <div class="links">
          <router-link :to="`/openshift/${item.id}`">
            {{ item.name }}
          </router-link>
        </div>
      </template>
      <template #item.clusterEnvironment="{ item }">
        {{
          formatter.formatOpenshiftClusterEnvironment(item.clusterEnvironment)
        }}
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice";
import type { OpenshiftNamespaceRef } from "@/types/OpenshiftNamespaceListItem";

import { mdiPlus } from "@mdi/js";
import { computed, ref, watch } from "vue";

import openshiftService from "@/api/openshiftService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import OpenshiftNamespaceOrder from "@/components/Openshift/OpenshiftNamespaceOrder.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();

const namespaces = ref<OpenshiftNamespaceRef[]>([]);
const loading = ref(false);

const cardTitle = computed(() => "Openshift Namespaces");
const namespaceCount = computed(() => namespaces.value.length);

const headers = [
  { title: "Name", key: "name" },
  { title: "Cluster", key: "clusterEnvironment" },
];

async function loadNamespaces(appservice: Appservice | null) {
  if (!appservice) {
    namespaces.value = [];
    return;
  }
  namespaces.value = [];
  const result = await openshiftService.getNamespacesByAppserviceId(
    loading,
    appservice.id
  );
  if (props.selectedAppservice?.id === appservice.id) {
    namespaces.value = result;
  }
}

watch(
  () => props.selectedAppservice,
  (appservice) => {
    void loadNamespaces(appservice);
  },
  { immediate: true }
);
</script>

<style scoped>
.action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>