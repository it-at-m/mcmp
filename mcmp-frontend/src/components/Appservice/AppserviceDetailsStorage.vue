<template>
  <common-card
    v-if="storages.length"
    :title="cardTitle"
    top-margin="0"
    :is-default-expanded="false"
  >
    <template #append-title>
      <count-badge :count="storageCount" />
    </template>
    <v-data-table
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

import { computed, ref, watch } from "vue";

import storageService from "@/api/storageService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const storages = ref<UnifiedStorageItemList[]>([]);
const loading = ref(false);

const cardTitle = computed(() => "Storage");
const storageCount = computed(() => storages.value.length);

const headers = [
  { title: "Name", key: "name" },
  { title: "Typ", key: "type" },
  { title: "Protokoll", key: "protocol" },
];

async function loadStorages(appservice: Appservice | null) {
  if (!appservice) {
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
