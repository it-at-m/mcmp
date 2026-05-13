<template>
  <v-tooltip :text="status ?? ''">
    <template #activator="{ props }">
      <div class="status-icon round-icon">
        <v-icon
          v-bind="props"
          :icon="icon"
          :color="color"
          size="small"
        />
      </div>
    </template>
  </v-tooltip>
</template>

<script setup lang="ts">
import {
  mdiCheckCircle,
  mdiCloseCircle,
  mdiHelpCircle,
  mdiProgressWrench,
} from "@mdi/js";
import { computed } from "vue";

const props = defineProps<{
  status: string | null | undefined;
}>();

const upperStatus = computed(() => props.status?.toUpperCase() ?? "");

const icon = computed(() => {
  switch (upperStatus.value) {
    case "RESOLVED":
      return mdiCheckCircle;
    case "FAILED":
      return mdiCloseCircle;
    case "OPEN":
      return mdiProgressWrench;
    default:
      return mdiHelpCircle;
  }
});

const color = computed(() => {
  switch (upperStatus.value) {
    case "RESOLVED":
      return "success";
    case "FAILED":
      return "error";
    case "OPEN":
      return "accent";
    default:
      return "grey";
  }
});
</script>

<style scoped>
.status-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}
</style>
