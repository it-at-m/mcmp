<script setup lang="ts">
import { mdiInformationOutline } from "@mdi/js";

const props = defineProps<{
  text?: string;
  href?: string;
  color?: string;
}>();
</script>

<template>
  <v-tooltip
    location="top"
    content-class="text-left"
  >
    <template #activator="{ props: activatorProps }">
      <slot
        name="activator"
        :props="activatorProps"
      >
        <span
          v-bind="activatorProps"
          class="ml-1"
        >
          <component
            :is="props.href ? 'a' : 'span'"
            :href="props.href"
            target="_blank"
            rel="noopener noreferrer"
          >
            <v-icon
              :icon="mdiInformationOutline"
              size="16"
              :color="props.color ?? 'primary'"
              style="vertical-align: baseline; position: relative; top: 2px"
            />
          </component>
        </span>
      </slot>
    </template>

    <slot>
      <span v-if="props.text">{{ props.text }}</span>
    </slot>
  </v-tooltip>
</template>
