<template>
  <common-card title="Informationen">
    <v-row>
      <v-col cols="4">
        <h3>Name</h3>
      </v-col>
      <v-col cols="4">
        <h3>Status</h3>
      </v-col>
      <v-col cols="4">
        <h3>
          Anwendungsservice<info-tooltip>
            <div class="pa-1">
              <strong>MCMP Anwendungsservice-Ansicht</strong>
              <p class="text-caption mt-2 mb-1">
                Öffnet die Detailseite des Anwendungsservice direkt hier in der
                MCMP.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="4"
        class="pt-0"
      >
        <p>{{ repository.name }}</p>
      </v-col>
      <v-col
        cols="4"
        class="pt-0"
      >
        <p :title="REPO_LOCK_STATUS_TOOLTIPS[repository.lockStatus]">
          {{ REPO_LOCK_STATUS_LABELS[repository.lockStatus] }}
        </p>
      </v-col>
      <v-col
        cols="4"
        class="pt-0 links"
      >
        <div v-if="repository.appservices && repository.appservices.length > 1">
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="app in repository.appservices"
              :key="app.id"
              class="mb-1"
            >
              <router-link :to="`/appservice/${app.id}`">
                {{ app.name }}
              </router-link>
            </li>
          </ul>
        </div>
        <div
          v-else-if="
            repository.appservices && repository.appservices.length === 1
          "
        >
          <router-link :to="`/appservice/${repository.appservices?.[0]?.id}`">
            {{ repository.appservices?.[0]?.name }}
          </router-link>
        </div>
        <p v-else>-</p>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type { RepositoryDetail } from "@/types/RepositoryDetail";

import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import {
  REPO_LOCK_STATUS_LABELS,
  REPO_LOCK_STATUS_TOOLTIPS,
} from "@/composables/repoLockStatus";

defineProps<{
  repository: RepositoryDetail;
}>();
</script>
