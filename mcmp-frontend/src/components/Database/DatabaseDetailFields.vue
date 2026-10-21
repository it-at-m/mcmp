<template>
  <v-row dense>
    <v-col
      v-for="entry in fields"
      :key="entry.label"
      cols="12"
      sm="6"
      md="4"
      lg="3"
    >
      <div class="detail-field">
        <div class="detail-field-label">{{ entry.label }}</div>
        <div class="detail-field-value">{{ entry.value }}</div>
      </div>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
import type { DatabaseDetailField } from "@/types/Database";

defineProps<{
  fields: DatabaseDetailField[];
}>();
</script>

<style scoped>
.detail-field {
  padding: 4px 0;
}

.detail-field-label {
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.6);
  margin-bottom: 2px;
}

.detail-field-value {
  font-size: 0.95rem;
  word-break: break-word;
}
</style>
