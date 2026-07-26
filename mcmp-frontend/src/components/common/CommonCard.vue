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
        <div
          v-if="$slots['prepend-title'] && $slots['prepend-title']().length"
          class="ml-4 d-flex align-center"
        >
          <slot name="prepend-title" />
        </div>
        <v-toolbar-title
          class="text-h6"
          :style="!disableExpansion ? 'cursor: pointer' : undefined"
          @click.stop="!disableExpansion && (expanded = !expanded)"
        >
          <div class="d-flex align-center">
            {{ title }}
            <slot name="append-title" />
          </div>
        </v-toolbar-title>

        <slot name="toolbar-actions" />

        <v-tooltip location="bottom">
          <template #activator="{ props: tooltipProps }">
            <v-btn
              v-if="!disableExpansion"
              v-bind="tooltipProps"
              :icon="expanded ? mdiChevronUp : mdiChevronDown"
              variant="text"
              @click.stop="expanded = !expanded"
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
import type { Ref } from "vue";

import { mdiChevronDown, mdiChevronUp } from "@mdi/js";
import { computed, inject, ref } from "vue";

const props = withDefaults(
  defineProps<{
    title: string;
    topMargin?: string;
    loading?: boolean;
    disableExpansion?: boolean;
    isDefaultExpanded?: boolean;
    cardId?: string;
  }>(),
  {
    topMargin: undefined,
    disableExpansion: false,
    isDefaultExpanded: true,
    cardId: undefined,
  }
);

interface CardExpandStore {
  isExpanded: (id: string, fallback: boolean) => boolean;
  setExpanded: (id: string, value: boolean) => void;
}

const store = inject<CardExpandStore | null>("cardExpandStore", null);
const currentTabRef = inject<Ref<string> | null>("currentTab", null);
const ownerTab = currentTabRef ? currentTabRef.value : "";
const cardKey = `${ownerTab}::${props.cardId ?? props.title}`;
const defaultExpanded = props.disableExpansion ? true : props.isDefaultExpanded;

store?.setExpanded(cardKey, store.isExpanded(cardKey, defaultExpanded));

const localExpanded = ref(defaultExpanded);

const expanded = computed({
  get: () =>
    store ? store.isExpanded(cardKey, defaultExpanded) : localExpanded.value,
  set: (value: boolean) => {
    if (store) {
      store.setExpanded(cardKey, value);
    } else {
      localExpanded.value = value;
    }
  },
});
</script>
