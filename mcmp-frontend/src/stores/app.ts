import { defineStore } from "pinia";
import { computed, ref } from "vue";

import appConfigService from "@/api/appConfigService";
import { SystemMode } from "@/types/AppConfig";

export const useAppStore = defineStore("app", () => {
  const systemMode = ref<SystemMode>(SystemMode.NORMAL);
  const maintenanceMessage = ref("");
  const maintenanceMessageMarkdown = ref("");
  const loading = ref(false);

  const isReadOnly = computed(
    () =>
      systemMode.value === SystemMode.READ_ONLY ||
      systemMode.value === SystemMode.FRONTEND_READ_ONLY
  );
  const isLocked = computed(() => systemMode.value === SystemMode.LOCKED);
  const isInfo = computed(() => systemMode.value === SystemMode.INFO);

  async function fetchSystemStatus() {
    try {
      const status = await appConfigService.getSystemStatus(loading);
      systemMode.value = status.systemMode;
      maintenanceMessage.value = status.maintenanceMessage;
      maintenanceMessageMarkdown.value = status.maintenanceMessageMarkdown;
    } catch (error) {
      console.debug("Fehler beim Laden des System-Status:", error);
    }
  }

  function setSystemStatus(
    mode: SystemMode,
    message: string,
    messageMarkdown: string
  ) {
    systemMode.value = mode;
    maintenanceMessage.value = message;
    maintenanceMessageMarkdown.value = messageMarkdown;
  }

  return {
    systemMode,
    maintenanceMessage,
    maintenanceMessageMarkdown,
    isReadOnly,
    isLocked,
    isInfo,
    fetchSystemStatus,
    setSystemStatus,
  };
});
