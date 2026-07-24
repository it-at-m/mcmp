<template>
  <div
    v-if="titleLabel"
    class="breadcrumb-nav mt-4 mb-1 links"
  >
    <template v-if="showParentCrumb">
      <router-link
        :to="`/appservice/${appserviceId}`"
        class="breadcrumb-parent"
      >
        <h3 class="breadcrumb-title d-flex align-center text-truncate">
          <v-icon class="mr-2">{{ mdiAccountGroup }}</v-icon>
          <span class="text-truncate">{{ appserviceName }}</span>
        </h3>
      </router-link>
      <v-icon class="breadcrumb-separator">{{ mdiChevronRight }}</v-icon>
    </template>

    <h3 class="breadcrumb-title d-flex align-center text-truncate">
      <v-icon
        class="mr-2"
        :color="titleIconColor"
        >{{ titleIcon }}</v-icon
      >
      <span class="text-truncate">{{ titleLabel }}</span>
    </h3>
  </div>
</template>

<script setup lang="ts">
import { mdiAccountGroup, mdiChevronRight } from "@mdi/js";
import { computed } from "vue";

const props = defineProps<{
  appserviceId: number | null;
  appserviceName: string | null;
  appserviceCount?: number;
  currentIcon?: string;
  currentIconColor?: string;
  currentLabel?: string;
}>();

const titleLabel = computed(() => props.currentLabel ?? props.appserviceName);
const titleIcon = computed(() =>
  props.currentLabel ? props.currentIcon : mdiAccountGroup
);
const titleIconColor = computed(() =>
  props.currentLabel ? props.currentIconColor : undefined
);
const showParentCrumb = computed(
  () =>
    !!props.currentLabel &&
    !!props.appserviceName &&
    (props.appserviceCount ?? 1) <= 1
);
</script>

<style scoped>
.breadcrumb-nav {
  display: flex;
  align-items: center;
  min-width: 0;
  margin: 4px 0 0 8px;
}

.breadcrumb-nav .text-truncate {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}

.breadcrumb-title {
  min-width: 0;
  margin: 0;
  font-size: 1.35rem;
}

/* Appservice crumb should give way first when space is tight - only it
   gets flex-shrink, so its name truncates before the current item's does. */
.breadcrumb-parent {
  flex-shrink: 1;
  min-width: 0;
}

.breadcrumb-nav > h2.breadcrumb-title {
  flex-shrink: 0;
}

.breadcrumb-separator {
  margin: 0 4px;
  opacity: 0.6;
  flex-shrink: 0;
}
</style>
