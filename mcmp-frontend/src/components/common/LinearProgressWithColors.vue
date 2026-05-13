<template>
  <v-progress-linear
    :model-value="value"
    height="20"
    :color="barColor"
    :class="{ 'text-black': props.value >= 50 }"
    bg-opacity="0.3"
    rounded
  >
    {{ showPercentage ? Math.round(value) + "%" : "" }}
    <info-tooltip
      v-if="tooltipText"
      :color="textColor"
      :text="tooltipText"
    />
  </v-progress-linear>
</template>

<script setup lang="ts">
import { computed } from "vue";

import InfoTooltip from "@/components/common/InfoTooltip.vue";

const props = defineProps<{
  value: number;
  showPercentage?: boolean;
  tooltipText?: string;
}>();

const barColor = computed(() => {
  if (props.value >= 50) return "yellow-lighten-3";
  if (props.value >= 81) return "light_red";
  return "light_green";
});

const textColor = computed(() => (props.value >= 50 ? "black" : "white"));
</script>
