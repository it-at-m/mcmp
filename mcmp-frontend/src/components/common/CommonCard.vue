<template>
  <div :class="topMargin ? 'pt-' + topMargin : 'pt-4'">
    <v-card
      outlined
      border
      elevation="2"
      class="mb-4"
      rounded="lg"
      color="backgroundLight"
      :loading="loading"
    >
      <v-toolbar
        density="compact"
        color="transparent"
      >
        <div class="ml-4 d-flex align-center">
          <slot name="prepend-title" />
        </div>
        <v-toolbar-title
          class="text-h6"
          :text="title"
        ></v-toolbar-title>

        <slot name="toolbar-actions" />

        <v-tooltip location="bottom">
          <template #activator="{ props: tooltipProps }">
            <v-btn
              v-bind="tooltipProps"
              v-if="!disableExpansion"
              :icon="expanded ? mdiChevronUp : mdiChevronDown"
              variant="text"
              @click="expanded = !expanded"
            ></v-btn>
          </template>
          <span>{{ expanded ? "einklappen" : "aufklappen" }}</span>
        </v-tooltip>
      </v-toolbar>

      <v-expand-transition>
        <div v-show="disableExpansion || expanded">
          <v-divider></v-divider>
          <v-card-text>
            <slot />
          </v-card-text>
        </div>
      </v-expand-transition>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { mdiChevronDown, mdiChevronUp } from "@mdi/js";
import { ref } from "vue";

const props = withDefaults(
  defineProps<{
    title: string;
    topMargin?: string;
    loading?: boolean;
    disableExpansion?: boolean;
    isDefaultExpanded?: boolean;
  }>(),
  {
    disableExpansion: false,
    isDefaultExpanded: true,
  }
);

const expanded = ref(props.disableExpansion ? true : props.isDefaultExpanded);
</script>
