<template>
  <common-card
    v-if="testing && storages.length"
    title="Storage"
    :loading="loading"
    top-margin="0"
  >
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="storages"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.name="{ item }">
        <div class="links">
          <router-link :to="`/storage/${item.type}/${item.uuid}`">
            {{ item.name }}
          </router-link>
        </div>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice";
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";

import { ref, watch } from "vue";

import storageService from "@/api/storageService";
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

const storages = ref<UnifiedStorageItemList[]>([]);
const loading = ref(false);

const headers = [
  { title: "Name", key: "name" },
  { title: "Typ", key: "type" },
  { title: "Protokoll", key: "protocol" },
];

async function loadStorages(appservice: Appservice | null) {
  if (!testing.value || !appservice) {
    storages.value = [];
    return;
  }
  storages.value = await storageService.getUnifiedStorageByAppserviceId(
    loading,
    appservice.id
  );
}

watch(
  () => props.selectedAppservice,
  (appservice) => {
    void loadStorages(appservice);
  },
  { immediate: true }
);

watch(testing, () => {
  void loadStorages(props.selectedAppservice);
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
