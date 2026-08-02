<template>
  <v-container
    v-if="currentLabel || appserviceName"
    fluid
    class="pb-0 mb-0"
  >
    <v-row
      class="flex-nowrap"
      no-gutters
    >
      <v-col class="title-col d-flex align-center">
        <breadcrumb-nav
          :appservice-id="appserviceId ?? null"
          :appservice-name="appserviceName ?? null"
          :appservice-count="appserviceCount"
          :current-icon="currentIcon"
          :current-icon-color="currentIconColor"
          :current-label="currentLabel"
        />
      </v-col>

      <v-col
        v-if="$slots.actions"
        cols="auto"
        class="d-flex justify-end flex-shrink-0"
      >
        <div class="action-button-group mt-2">
          <slot name="actions" />
        </div>
      </v-col>
    </v-row>

    <slot name="banners" />

    <v-row v-if="$slots.statusChips">
      <v-col class="ml-4 pa-0">
        <v-chip-group
          :show-arrows="true"
          tabindex="-1"
        >
          <slot name="statusChips" />
        </v-chip-group>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import BreadcrumbNav from "@/components/common/BreadcrumbNav.vue";

defineProps<{
  appserviceId?: number | null;
  appserviceName?: string | null;
  appserviceCount?: number;
  currentIcon?: string;
  currentIconColor?: string;
  currentLabel?: string;
}>();
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.title-col {
  min-width: 0;
}

.action-button-group {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  flex-shrink: 0;
  background: rgb(var(--v-theme-bg_light));
  border-radius: 28px;
  padding: 4px 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.12),
    0 1px 2px rgba(0, 0, 0, 0.24);
  transition: box-shadow 0.3s ease;
}

.action-button-group:hover {
  box-shadow:
    0 3px 6px rgba(0, 0, 0, 0.16),
    0 3px 6px rgba(0, 0, 0, 0.23);
}

@media print {
  .action-button-group,
  :global(.v-chip-group) {
    display: none !important;
  }

  :global(.v-container) {
    padding-top: 20px !important;
    margin-top: 0 !important;
  }

  .v-row {
    margin: 0 !important;
  }

  .v-col {
    padding-top: 10px !important;
    padding-bottom: 10px !important;
  }

  h2 {
    margin-top: 0 !important;
    line-height: 1.2 !important;
  }
}
</style>
