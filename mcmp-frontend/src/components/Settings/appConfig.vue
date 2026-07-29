<template>
  <common-card title="Wartung">
    <v-form
      v-if="!isInitialLoading"
      @submit.prevent="saveChanges"
    >
      <v-row>
        <v-col cols="12">
          <v-radio-group
            v-model="status.systemMode"
            :disabled="loading"
            label="Systemmodus"
          >
            <v-radio
              v-for="option in systemModeOptions"
              :key="option.value"
              :value="option.value"
            >
              <template #label>
                {{ option.title }}
                <info-tooltip
                  :text="option.tooltip"
                  class="ml-1"
                />
              </template>
            </v-radio>
          </v-radio-group>
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="12">
          <v-textarea
            v-model="status.maintenanceMessageMarkdown"
            :loading="loading"
            label="Wartungsnachricht (Markdown)"
            hint="Verwenden Sie Markdown für die Formatierung. Die Nachricht wird im Backend validiert und in HTML umgewandelt."
            required
            rows="3"
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col
          class="text-right"
          cols="12"
        >
          <v-btn
            :loading="loading"
            :prepend-icon="mdiContentSave"
            color="primary"
            type="submit"
          >
            Speichern
          </v-btn>
        </v-col>
      </v-row>
    </v-form>
  </common-card>
</template>

<script setup lang="ts">
import type { SystemStatus } from "@/types/AppConfig";

import { mdiContentSave } from "@mdi/js";
import { onMounted, ref } from "vue";

import appConfigService from "@/api/appConfigService";
import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import { useAppStore } from "@/stores/app";
import { SystemMode } from "@/types/AppConfig";

const appStore = useAppStore();
const status = ref<SystemStatus>({
  systemMode: SystemMode.NORMAL,
  maintenanceMessage: "",
  maintenanceMessageMarkdown: "",
});
const isInitialLoading = ref(true);
const loading = ref(false);

const systemModeOptions = [
  {
    title: "Normal",
    value: SystemMode.NORMAL,
    tooltip:
      "Alle Funktionen der MCMP sind uneingeschränkt aktiviert. Es wird kein Info-Banner angezeigt.",
  },
  {
    title: "Info",
    value: SystemMode.INFO,
    tooltip:
      "Alle Funktionen der MCMP sind aktiviert. Die eingegebene Wartungsnachricht wird als Info-Banner auf jeder Seite angezeigt.",
  },
  {
    title: "Frontend read-only",
    value: SystemMode.FRONTEND_READ_ONLY,
    tooltip:
      "Schreibende Zugriffe (DELETE, PATCH, POST, PUT) über die Frontend-API sind gesperrt. Im Frontend werden ändernde Funktionen ausgeblendet. Die Wartungsnachricht wird als Info-Banner angezeigt.",
  },
  {
    title: "Frontend und Backend read-only",
    value: SystemMode.READ_ONLY,
    tooltip:
      "Sämtliche API-Endpunkte sind für schreibende Zugriffe gesperrt (einschließlich EAI-Datenimporte). Im Frontend werden keine ändernden Funktionen angezeigt. Die Wartungsnachricht wird als Info-Banner angezeigt.",
  },
  {
    title: "Gesperrt",
    value: SystemMode.LOCKED,
    tooltip:
      "Nur Administratoren können die MCMP im Read-Only-Modus weiterhin nutzen. Alle anderen Benutzer sehen lediglich eine Wartungsseite mit dem entsprechenden Hinweistext.",
  },
];

async function loadCurrentValues() {
  try {
    isInitialLoading.value = true;
    const result = await appConfigService.getSystemStatus(loading);
    if (result) {
      status.value = result;
    }
  } catch (error) {
    console.debug("Error loading system status", error);
  } finally {
    isInitialLoading.value = false;
  }
}

async function saveChanges() {
  try {
    await appConfigService.updateSystemStatus(loading, status.value);

    appStore.setSystemStatus(
      status.value.systemMode,
      status.value.maintenanceMessage,
      status.value.maintenanceMessageMarkdown
    );

    await appStore.fetchSystemStatus();
  } catch (error) {
    console.debug("Error saving changes", error);
  }
}

onMounted(() => {
  loadCurrentValues();
});
</script>
