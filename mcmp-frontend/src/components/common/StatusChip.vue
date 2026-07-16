<template>
  <v-tooltip
    :text="tooltip"
    location="bottom"
    :aria-label="tooltip"
  >
    <template #activator="{ props: tooltipProps }">
      <v-chip
        v-bind="tooltip === '' ? {} : tooltipProps"
        :text="isMatch ? matchText : notMatchText"
        :base-color="isMatch ? '_green' : '_red'"
        :ripple="false"
        tabindex="-1"
        variant="flat"
        :href="href"
        target="_blank"
      />
    </template>
  </v-tooltip>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    tooltip?: string;
    value?: string | number | boolean;
    checkValue: string | number | boolean;
    matchText: string;
    notMatchText: string;
    matchMode: "equal" | "greater" | "less" | "greaterEquals" | "lessEquals";
    href?: string;
  }>(),
  {
    value: false,
    tooltip: "",
  }
);

const isMatch = computed(() => {
  if (props.value === undefined || props.value === null) {
    return false;
  }
  switch (props.matchMode) {
    case "greater":
      return Number(props.value) > Number(props.checkValue);
    case "greaterEquals":
      return Number(props.value) >= Number(props.checkValue);
    case "less":
      return Number(props.value) < Number(props.checkValue);
    case "lessEquals":
      return Number(props.value) <= Number(props.checkValue);
    case "equal":
    default:
      return props.value === props.checkValue;
  }
});
</script>
