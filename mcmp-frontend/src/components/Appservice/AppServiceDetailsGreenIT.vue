<template>
  <common-card title="Green-IT">
    <template #append-title>
      <div class="d-flex align-center">
        <!-- CSV-Export Button -->
        <v-tooltip location="top" text="Green-IT Bericht als CSV exportieren">
          <template #activator="{ props: tooltipProps }">
            <v-btn
                v-bind="tooltipProps"
                :icon="mdiDownload"
                size="small"
                variant="text"
                class="mr-2"
                @click.stop="exportGreenItCsv"
            />
          </template>
        </v-tooltip>

        <!-- Das Effizienz-Blatt mit Tooltip und Skala -->
        <v-tooltip location="top">
          <template #activator="{ props: tooltipProps }">
            <v-icon
                v-bind="tooltipProps"
                :icon="mdiLeaf"
                :color="overallLeafColor"
                size="24"
            />
          </template>
          <div class="text-caption">
            <strong>Ressourceneffizienz:</strong><br>
            0% bis 15% Einsparung: Grün<br>
            >15% bis 20% Einsparung: Gelb<br>
            >20% Einsparung: Rot
          </div>
        </v-tooltip>
      </div>
    </template>

    <v-row>
      <!-- CPU Spalte -->
      <v-col cols="6">
        <h3 class="d-flex align-center">
          CPU Ressourcen
          <v-icon
              :icon="mdiLeaf"
              :color="cpuLeafColor"
              size="20"
              class="ml-2"
          />
        </h3>
        <div class="mt-2">
          <p><strong>Gesamt (Ist):</strong> {{ totalCpuCurrent }} Cores</p>
          <p><strong>Gesamt (Soll):</strong> {{ totalCpuTarget }} Cores</p>
          <p class="mt-1">
            <strong>Mögliche Einsparung:</strong>
            {{ totalCpuSavings }} Cores
            <span v-if="totalCpuSavings > 0">
              ({{ cpuSavingsPercentage }}%)
            </span>
          </p>
        </div>
      </v-col>

      <!-- RAM Spalte -->
      <v-col cols="6">
        <h3 class="d-flex align-center">
          RAM Ressourcen
          <v-icon
              :icon="mdiLeaf"
              :color="ramLeafColor"
              size="20"
              class="ml-2"
          />
        </h3>
        <div class="mt-2">
          <p><strong>Gesamt (Ist):</strong> {{ formatter.formatMBtoGB(totalRamCurrentMb) }} GB</p>
          <p><strong>Gesamt (Soll):</strong> {{ formatter.formatMBtoGB(totalRamTargetMb) }} GB</p>
          <p class="mt-1">
            <strong>Mögliche Einsparung:</strong>
            {{ formatter.formatMBtoGB(totalRamSavingsMb) }} GB
            <span v-if="totalRamSavingsMb > 0">
              ({{ ramSavingsPercentage }}%)
            </span>
          </p>
        </div>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";
import { mdiDownload, mdiLeaf } from "@mdi/js";
import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();

const servers = computed(() => props.selectedAppservice?.servers || []);

// --- 1. Aktuelle Gesamtzahlen (Ist) berechnen ---
const totalCpuCurrent = computed(() => {
  return servers.value.reduce((sum: number, server: any) => sum + Number(server.numCpu || 0), 0);
});

const totalRamCurrentMb = computed(() => {
  return servers.value.reduce((sum: number, server: any) => sum + Number(server.memoryMb || 0), 0);
});

// --- 2. Einsparungen berechnen ---
const totalCpuSavings = computed(() => {
  return servers.value.reduce((sum: number, server: any) => {
    const current = Number(server.numCpu || 0);
    const recVal = server.numCpuRecommended;

    if (recVal != null && recVal !== '' && Number(recVal) !== 0) {
      const recommended = Number(recVal);
      if (recommended < current) {
        return sum + (current - recommended);
      }
    }
    return sum;
  }, 0);
});

const totalRamSavingsMb = computed(() => {
  return servers.value.reduce((sum: number, server: any) => {
    const current = Number(server.memoryMb || 0);
    const recVal = server.memoryMbRecommended;

    if (recVal != null && recVal !== '' && Number(recVal) !== 0) {
      const recommended = Number(recVal);
      if (recommended < current) {
        return sum + (current - recommended);
      }
    }
    return sum;
  }, 0);
});

// --- 3. Zielwerte (Soll) berechnen ---
const totalCpuTarget = computed(() => {
  return totalCpuCurrent.value - totalCpuSavings.value;
});

const totalRamTargetMb = computed(() => {
  return totalRamCurrentMb.value - totalRamSavingsMb.value;
});

// --- 4. Prozentuale Einsparung berechnen ---
const cpuSavingsPercentage = computed(() => {
  if (totalCpuCurrent.value === 0) return "0.0";
  return ((totalCpuSavings.value / totalCpuCurrent.value) * 100).toFixed(1);
});

const ramSavingsPercentage = computed(() => {
  if (totalRamCurrentMb.value === 0) return "0.0";
  return ((totalRamSavingsMb.value / totalRamCurrentMb.value) * 100).toFixed(1);
});

// --- 5. Farb-Logik für Blätter ---
const getLeafColor = (percentage: number) => {
  if (percentage <= 15) return "green";
  if (percentage <= 20) return "orange";
  return "red";
};

const cpuLeafColor = computed(() => getLeafColor(Number(cpuSavingsPercentage.value)));
const ramLeafColor = computed(() => getLeafColor(Number(ramSavingsPercentage.value)));
const overallLeafColor = computed(() => {
  const maxPct = Math.max(Number(cpuSavingsPercentage.value), Number(ramSavingsPercentage.value));
  return getLeafColor(maxPct);
});

// --- 6. CSV-Export Funktion ---
const exportGreenItCsv = () => {
  const currentServers = servers.value;
  if (currentServers.length === 0) return;

  const headers = ['Servername', 'CPU Ist', 'CPU Empfohlen', 'CPU Einsparung (Cores)', 'RAM Ist (MB)', 'RAM Empfohlen (MB)', 'RAM Einsparung (MB)'];

  const rows = currentServers.map((server: any) => {
    const currentCpu = Number(server.numCpu || 0);
    const recCpu = Number(server.numCpuRecommended || 0);
    const cpuSavings = recCpu > 0 && recCpu < currentCpu ? currentCpu - recCpu : 0;

    const currentRam = Number(server.memoryMb || 0);
    const recRam = Number(server.memoryMbRecommended || 0);
    const ramSavings = recRam > 0 && recRam < currentRam ? currentRam - recRam : 0;

    return [
      `"${server.name || ''}"`,
      currentCpu,
      recCpu > 0 ? recCpu : '',
      cpuSavings,
      currentRam,
      recRam > 0 ? recRam : '',
      ramSavings
    ];
  });

  const csvContent = [
    headers.join(';'),
    ...rows.map(row => row.join(';'))
  ].join('\r\n');

  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');

  const appserviceName = props.selectedAppservice?.name || 'appservice';
  link.setAttribute('href', url);
  link.setAttribute('download', `green-it-einsparungen-${appserviceName}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};
</script>