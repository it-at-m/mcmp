<template>
  <v-menu>
    <template #activator="{ props: menuProps }">
      <v-tooltip
        location="bottom"
        :text="tooltip"
        :open-on-hover="true"
      >
        <template #activator="{ props: tooltipProps }">
          <span v-bind="tooltipProps">
            <v-btn
              v-bind="menuProps"
              class="material-action-btn"
              variant="flat"
              :icon="mdiTools"
              size="small"
              :disabled="isBatchDisabled"
              aria-label="Wartungsmodus"
            />
          </span>
        </template>
      </v-tooltip>
    </template>
    <v-list>
      <v-list-item>
        <win-wartungsmodus-dialog
          title="Wartungsmodus setzen"
          as-menu-item
          :is-batch-operation="true"
          :selected-server-ids="selectedServerIds"
          :parent-all-selected-servers-eligible="
            parentAllSelectedServersEligible
          "
          :parent-disabled-tooltip="parentDisabledTooltip"
          @save="emit('save', true)"
        />
      </v-list-item>
      <v-list-item>
        <win-wartungsmodus-dialog
          title="Wartungsmodus vorzeitig beenden"
          as-menu-item
          :is-batch-operation="true"
          :selected-server-ids="selectedServerIds"
          :parent-all-selected-servers-eligible="
            parentAllSelectedServersEligible
          "
          :parent-disabled-tooltip="parentDisabledTooltip"
          @save="emit('save', true)"
        />
      </v-list-item>
    </v-list>
  </v-menu>
</template>

<script setup lang="ts">
import { mdiTools } from "@mdi/js";
import { computed } from "vue";

import WinWartungsmodusDialog from "@/components/Server/ActionButtons/WinWartungsmodusDialog.vue";

const props = defineProps<{
  selectedServerIds?: number[];
  parentAllSelectedServersEligible?: boolean;
  parentDisabledTooltip?: string;
}>();

const emit = defineEmits<(e: "save", save: boolean) => boolean>();

const isBatchDisabled = computed(() => {
  if (!props.parentAllSelectedServersEligible) return true;
  return (props.selectedServerIds ?? []).length === 0;
});

const tooltip = computed(() => {
  if ((props.selectedServerIds ?? []).length === 0) {
    return "Keine Server ausgewählt.";
  }
  if (!props.parentAllSelectedServersEligible) {
    return (
      props.parentDisabledTooltip ||
      "Nicht berechtigt oder Server nicht verwaltet."
    );
  }
  return "Wartungsmodus";
});
</script>

<style scoped>
.material-action-btn {
  border-radius: 50% !important;
  margin: 0 4px;
  width: 33.35px !important;
  height: 33.35px !important;
  box-shadow:
    0 3px 1px -2px rgba(0, 0, 0, 0.2),
    0 2px 2px 0 rgba(0, 0, 0, 0.14),
    0 1px 5px 0 rgba(0, 0, 0, 0.12);
  transition: box-shadow 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.material-action-btn:hover {
  box-shadow:
    0 2px 4px -1px rgba(0, 0, 0, 0.2),
    0 4px 5px 0 rgba(0, 0, 0, 0.14),
    0 1px 10px 0 rgba(0, 0, 0, 0.12);
}
</style>
