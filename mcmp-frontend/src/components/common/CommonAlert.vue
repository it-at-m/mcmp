<template>
  <v-alert
    :color="!props.color && props.isSnowChange ? 'info' : props.color"
    :icon="icon"
    border
    variant="tonal"
    rounded="lg"
    :type="type"
  >
    <a style="color: rgb(var(--v-theme-text))">
      <slot />
      <div v-if="isSnowChange">
        <h4>Hinweis:</h4>
        Bei dieser Aktion wird ein Change in ServiceNow erstellt.
      </div>
    </a>
  </v-alert>
</template>

<script setup lang="ts">
const props = defineProps<{
  icon?: string;
  color?: string;
  type?: "info" | "warning" | "error" | "success";
  isSnowChange?: boolean;
}>();
</script>
