<template>
  <div>
    <v-row>
      <v-col>
        <install-dialog />
      </v-col>
    </v-row>
  </div>
</template>

<script setup lang="ts">
import { provide, ref } from "vue";

import InstallDialog from "@/components/install/InstallDialog.vue";

const hasOpenDialog = ref(false);
provide("registerOpenDialog", () => {
  hasOpenDialog.value = true;
});
provide("unregisterOpenDialog", () => {
  hasOpenDialog.value = false;
});
</script>
