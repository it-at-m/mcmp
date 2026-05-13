<template>
  <v-menu>
    <template #activator="{ props: menuProps }">
      <v-tooltip
        text="Wartungsmodus"
        location="bottom"
      >
        <template #activator="{ props: tooltipProps }">
          <v-btn
            v-bind="{ ...menuProps, ...tooltipProps }"
            class="material-action-btn"
            variant="flat"
            :icon="mdiTools"
            size="small"
          >
          </v-btn>
        </template>
      </v-tooltip>
    </template>
    <v-list>
      <v-list-item>
        <win-wartungsmodus-dialog
          title="Wartungsmodus setzen"
          :server="server"
        >
        </win-wartungsmodus-dialog>
      </v-list-item>
      <v-list-item>
        <win-wartungsmodus-dialog
          title="Wartungsmodus vorzeitig beenden"
          :server="server"
        >
        </win-wartungsmodus-dialog>
      </v-list-item>
    </v-list>
  </v-menu>
</template>

<script setup lang="ts">
import { mdiTools } from "@mdi/js";

import WinWartungsmodusDialog from "@/components/Server/ActionButtons/WinWartungsmodusDialog.vue";
import Server from "@/types/Server";

const props = defineProps<{
  server: Server;
}>();
</script>

<style scoped>
.material-action-btn {
  border-radius: 50% !important;
  margin: 0 4px;
  width: 40px !important;
  height: 40px !important;
  min-width: 40px !important;
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
