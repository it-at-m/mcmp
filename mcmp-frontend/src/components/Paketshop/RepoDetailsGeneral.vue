<template>
  <common-card title="Informationen">
    <v-row>
      <v-col cols="6">
        <h3>Name</h3>
      </v-col>
      <v-col cols="6">
        <h3>Status</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="6"
        class="pt-0"
      >
        <p>{{ repository.name }}</p>
      </v-col>
      <v-col
        cols="6"
        class="pt-0"
      >
        <p :title="REPO_LOCK_STATUS_TOOLTIPS[repository.lockStatus]">
          {{ REPO_LOCK_STATUS_LABELS[repository.lockStatus] }}
        </p>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type { RepositoryDetail } from "@/types/RepositoryDetail";

import CommonCard from "@/components/common/CommonCard.vue";
import {
  REPO_LOCK_STATUS_LABELS,
  REPO_LOCK_STATUS_TOOLTIPS,
} from "@/composables/repoLockStatus";

defineProps<{
  repository: RepositoryDetail;
}>();
</script>
