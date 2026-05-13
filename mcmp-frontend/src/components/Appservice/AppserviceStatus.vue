<template>
  <v-container
    v-if="selectedAppservice"
    fluid
  >
    <v-row>
      <v-col
        cols="12"
        class="d-flex align-center"
      >
        <v-icon class="IconSize">{{ mdiAccountGroup }}</v-icon>
        <h2 class="ml-2">
          {{ selectedAppservice == null ? "" : selectedAppservice.name }}
        </h2>
      </v-col>
    </v-row>
  </v-container>
</template>
<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";

import { mdiAccountGroup } from "@mdi/js";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();
</script>
<style>
.IconSize {
  width: 40px;
  height: 40px;
}
</style>
