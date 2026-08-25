<template>
  <status-chip
    v-if="!canEdit"
    :value="canEdit"
    :check-value="false"
    match-text="Nur Lesezugriff"
    not-match-text=""
    match-mode="equal"
  />
  <status-chip
    v-if="!canEdit && assignedCount === 0"
    :value="assignedCount"
    :check-value="1"
    match-text=""
    not-match-text="Bearbeitung ist gesperrt."
    match-mode="greaterEquals"
    :tooltip="`${entityLabel} ist keinem Anwendungsservice zugewiesen`"
    href="https://go.muenchen.de/sp/KB0023236"
  />
  <status-chip
    v-if="!canEdit && assignedCount > 1"
    :value="assignedCount"
    :check-value="1"
    match-text=""
    not-match-text="Bearbeitung ist gesperrt."
    match-mode="lessEquals"
    :tooltip="`${entityLabel} ist mehreren Anwendungsservices zugewiesen`"
    href="https://go.muenchen.de/sp/KB0023236"
  />
</template>

<script setup lang="ts">
import StatusChip from "@/components/common/StatusChip.vue";

withDefaults(
  defineProps<{
    canEdit?: boolean;
    assignedCount?: number;
    entityLabel: string;
  }>(),
  {
    canEdit: false,
    assignedCount: 0,
  }
);
</script>
