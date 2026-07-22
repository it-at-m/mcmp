<template>
  <common-card
    v-if="testing && loadbalancers.length"
    title="Loadbalancer"
    :loading="loading"
    top-margin="0"
    :is-default-expanded="false"
  >
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="loadbalancers"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
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

import { ref, watch } from "vue";

import loadbalancerService from "@/api/loadbalancerService";
import testenvService from "@/api/testenvService";
import CommonCard from "@/components/common/CommonCard.vue";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const testing = ref(false);
const loadingTestEnv = ref(false);
testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
  testing.value = enabled;
});

const loadbalancers = ref<LoadbalancerListItem[]>([]);
const loading = ref(false);

const headers = [
  { title: "Name", key: "name" },
  { title: "Domain", key: "domain" },
  { title: "Listen", key: "listen" },
  { title: "Port", key: "port" },
];

async function loadLoadbalancers(appservice: Appservice | null) {
  if (!testing.value || !appservice) {
    loadbalancers.value = [];
    return;
  }
  loadbalancers.value =
    await loadbalancerService.getLoadbalancersByAppserviceId(
      loading,
      appservice.id
    );
}

watch(
  () => props.selectedAppservice,
  (appservice) => {
    void loadLoadbalancers(appservice);
  },
  { immediate: true }
);

watch(testing, () => {
  void loadLoadbalancers(props.selectedAppservice);
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
