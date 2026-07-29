<template>
  <common-card title="Repositories">
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="repos"
      :items-per-page="-1"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.locked="{ item }">
        <v-tooltip
          v-if="item.locked"
          text="Repository ist gesperrt"
          location="top"
        >
          <template #activator="{ props }">
            <v-icon
              v-bind="props"
              :icon="mdiLock"
              size="small"
            />
          </template>
        </v-tooltip>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository";

import { mdiLock } from "@mdi/js";

import CommonCard from "@/components/common/CommonCard.vue";

defineProps<{
  repos: Repository[];
  loading: boolean;
}>();

const headers = [
  { title: "Name", key: "name" },
  { title: "Gesperrt", key: "locked" },
];
</script>
