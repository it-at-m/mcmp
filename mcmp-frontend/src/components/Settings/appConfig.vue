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
            label="Systemmodus"
            inline
            :disabled="loading"
          >
            <v-radio
              v-for="option in systemModeOptions"
              :key="option.value"
              :label="option.title"
              :value="option.value"
            />
          </v-radio-group>
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="12">
          <v-textarea
            v-model="status.maintenanceMessage"
            label="Wartungsnachricht"
            :loading="loading"
            rows="3"
            required
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col
          cols="12"
          class="text-right"
        >
          <v-btn
            type="submit"
            color="primary"
            :loading="loading"
            :prepend-icon="mdiContentSave"
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
import { useAppStore } from "@/stores/app";
import { SystemMode } from "@/types/AppConfig";

const appStore = useAppStore();
const status = ref<SystemStatus>({
  systemMode: SystemMode.NORMAL,
  maintenanceMessage: "",
});
const isInitialLoading = ref(true);
const loading = ref(false);

const systemModeOptions = [
  { title: "Normal", value: SystemMode.NORMAL },
  { title: "Nur lesen", value: SystemMode.READ_ONLY },
  { title: "Gesperrt", value: SystemMode.LOCKED },
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
      status.value.maintenanceMessage
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
