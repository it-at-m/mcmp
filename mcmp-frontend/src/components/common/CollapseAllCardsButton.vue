<template>
  <v-tooltip location="bottom">
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        :icon="expanded ? mdiUnfoldLessHorizontal : mdiUnfoldMoreHorizontal"
        variant="text"
        @click="$emit('toggle')"
      ></v-btn>
    </template>
    <span>{{ expanded ? "Alle einklappen" : "Alle aufklappen" }}</span>
  </v-tooltip>
</template>

<script setup lang="ts">
import { mdiUnfoldLessHorizontal, mdiUnfoldMoreHorizontal } from "@mdi/js";

defineProps<{
  expanded: boolean;
}>();

defineEmits<{
  toggle: [];
}>();
</script>
