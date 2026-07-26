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

.v-list-item:hover {
  background-color: rgba(124, 124, 124, 0.2);
}
</style>
