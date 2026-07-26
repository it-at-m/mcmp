<template>
  <span class="count-badge">
    {{ count }}
  </span>
</template>

<script setup lang="ts">
defineProps<{
  count: number;
}>();
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  margin-left: 8px;
  border-radius: 999px;
  background-color: rgb(var(--v-theme-primary));
  color: rgb(var(--v-theme-on-primary));
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
}
</style>
