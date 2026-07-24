<template>
  <common-card
    v-if="testing && namespaces.length"
    :title="cardTitle"
    top-margin="0"
    :is-default-expanded="false"
  >
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="namespaces"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.name="{ item }">
        <div class="links">
          <router-link :to="`/openshift/${item.id}`">
            {{ item.name }}
          </router-link>
        </div>
      </template>
      <template #item.clusterName="{ item }">
        {{ formatter.formatOpenshiftClusterName(item.clusterName) }}
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice";
import type { OpenshiftNamespaceRef } from "@/types/OpenshiftNamespaceListItem";

import { computed, ref, watch } from "vue";

import openshiftService from "@/api/openshiftService";
import testenvService from "@/api/testenvService";
import CommonCard from "@/components/common/CommonCard.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();

const testing = ref(false);
const loadingTestEnv = ref(false);
testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
  testing.value = enabled;
});

const namespaces = ref<OpenshiftNamespaceRef[]>([]);
const loading = ref(false);

const cardTitle = computed(
  () => `Openshift Namespaces (${namespaces.value.length})`
);

const headers = [
  { title: "Name", key: "name" },
  { title: "Cluster", key: "clusterName" },
];

async function loadNamespaces(appservice: Appservice | null) {
  if (!testing.value || !appservice) {
    namespaces.value = [];
    return;
  }
  namespaces.value = await openshiftService.getNamespacesByAppserviceId(
    loading,
    appservice.id
  );
}

watch(
  () => props.selectedAppservice,
  (appservice) => {
    void loadNamespaces(appservice);
  },
  { immediate: true }
);

watch(testing, () => {
  void loadNamespaces(props.selectedAppservice);
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
