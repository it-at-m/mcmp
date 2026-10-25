<template>
  <div class="d-flex align-center">
    <div class="power-state-icon-inline mr-2">
      <v-icon
        :color="display.color"
        size="20"
      >
        {{ display.icon }}
      </v-icon>
    </div>
    <span>{{ display.label }}</span>
  </div>
</template>

<script setup lang="ts">
import { mdiPauseCircle, mdiPlayCircle, mdiStopCircle } from "@mdi/js";
import { computed } from "vue";

const props = defineProps<{
  powerState: string | null | undefined;
}>();

const display = computed(() => {
  if (props.powerState === "poweredOn")
    return { icon: mdiPlayCircle, color: "btn_green", label: "Ein" };
  if (props.powerState === "poweredOff")
    return { icon: mdiStopCircle, color: "btn_red", label: "Aus" };
  return { icon: mdiPauseCircle, color: "accent", label: "Standby" };
});
</script>

<style scoped>
.power-state-icon-inline {
  display: flex;
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
}
</style>
