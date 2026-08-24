<template>
  <v-menu>
    <template #activator="{ props: menuProps }">
      <v-tooltip
        text="Checkmk"
        location="bottom"
      >
        <template #activator="{ props: tooltipProps }">
          <v-btn
            v-bind="{ ...menuProps, ...tooltipProps }"
            class="material-action-btn"
            variant="flat"
            icon
            size="small"
          >
            <img
              :src="checkmkIcon"
              alt="Checkmk dropdown menu"
              width="30"
              height="30"
            />
          </v-btn>
        </template>
      </v-tooltip>
    </template>
    <v-list>
      <v-list-item
        :href="checkmkUrl"
        target="_blank"
        clickable
        aria-label="Host anschauen"
        :append-icon="mdiOpenInNew"
      >
        <v-list-item-title>Host anschauen</v-list-item-title>
      </v-list-item>
      <check-mk-dialog
        title="Downtime setzen"
        :server="server"
      />
      <check-mk-dialog
        title="Service Discovery"
        :server="server"
      />
    </v-list>
  </v-menu>
</template>

<script setup lang="ts">
import { mdiOpenInNew } from "@mdi/js";
import { computed } from "vue";

import checkmkIcon from "@/assets/checkmk.svg";
import CheckMkDialog from "@/components/Server/ActionButtons/CheckMkDialog.vue";
import Server from "@/types/Server";

const props = defineProps<{
  server: Server;
}>();

const checkmkUrl = computed(() =>
  props.server
    ? `https://monitoring.muenchen.de/lhmmon/check_mk/view.py?_show_filter_form=0&filled_in=filter&host=${props.server.fqdn}&view_name=host`
    : "#"
);
</script>

<style scoped>
.v-list-item:hover {
  background-color: rgba(124, 124, 124, 0.2);
}
</style>
