<template>
  <common-card
    v-for="(irule, idx) in lb.irules"
    :key="irule.name"
    :title="irule.name"
    :top-margin="idx === 0 ? undefined : '0'"
  >
    <pre class="irule-content">{{ irule.content ?? "-" }}</pre>
  </common-card>

  <div
    v-if="!lb.irules || lb.irules.length === 0"
    class="pa-4 text-grey"
  >
    Keine IRules vorhanden.
  </div>
</template>

<script setup lang="ts">
import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";

import CommonCard from "@/components/common/CommonCard.vue";

defineProps<{
  lb: LoadbalancerDetail;
}>();
</script>
<style scoped>
.irule-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: monospace;
  margin: 0;
}
</style>
