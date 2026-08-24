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
