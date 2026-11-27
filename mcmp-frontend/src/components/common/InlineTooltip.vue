<template>
  <v-tooltip
    interactive
    :location="location || 'top'"
  >
    <template #activator="{ props }">
      <v-icon
        v-bind="props"
        size="large"
        :class="marginTop ? `mt-${marginTop}` : ''"
        >{{ mdiInformationSlabCircleOutline }}</v-icon
      >
    </template>
    <slot />
  </v-tooltip>
</template>

<script setup lang="ts">
import type { Anchor } from "vuetify";

import { mdiInformationSlabCircleOutline } from "@mdi/js";

defineProps<{
  location?: Anchor;
  marginTop?: string;
}>();
</script>
