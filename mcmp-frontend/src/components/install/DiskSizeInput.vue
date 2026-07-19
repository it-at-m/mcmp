<template>
  <strong>{{ title }}*</strong>
  <v-number-input
    v-model="innerValue"
    :min="minSizeInGB"
    :max="maxSizeInGB"
    :hint="'Erlaubte Werte ' + minSizeInGB + ' bis ' + maxSizeInGB"
    persistent-hint
    control-variant="split"
    variant="solo"
  >
  </v-number-input>
</template>
<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  modelValue: number;
  title: string;
  minSizeInGB: number;
  maxSizeInGB: number;
}>();

const emit = defineEmits<(e: "update:modelValue", value: number) => void>();

const innerValue = computed<number>({
  get: () => props.modelValue,
  set: (val: number) => emit("update:modelValue", val),
});
</script>
