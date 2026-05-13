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
import { mdiInformationSlabCircleOutline } from "@mdi/js";

const props = defineProps<{
  location?: string;
  marginTop?: string;
}>();
</script>
