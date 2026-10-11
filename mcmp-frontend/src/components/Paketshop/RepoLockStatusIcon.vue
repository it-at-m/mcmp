<template>
  <!-- OPEN repositories have no icon, like unlocked repositories before -->
  <v-tooltip
    v-if="status !== 'OPEN'"
    :text="REPO_LOCK_STATUS_TOOLTIPS[status]"
    location="top"
  >
    <template #activator="{ props: tooltipProps }">
      <v-icon
        v-bind="tooltipProps"
        :icon="REPO_LOCK_STATUS_ICONS[status]"
        size="small"
      />
    </template>
  </v-tooltip>
</template>

<script setup lang="ts">
import type { RepositoryLockStatus } from "@/types/Repository";

import {
  REPO_LOCK_STATUS_ICONS,
  REPO_LOCK_STATUS_TOOLTIPS,
} from "@/composables/repoLockStatus";

defineProps<{
  status: RepositoryLockStatus;
}>();
</script>
