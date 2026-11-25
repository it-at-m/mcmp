<template>
  <common-card title="Green-IT">
    <template #append-title>
      <!-- Das Effizienz-Blatt mit Tooltip und Skala -->
      <v-tooltip location="top">
        <template #activator="{ props: tooltipProps }">
          <v-icon
            v-bind="tooltipProps"
            :icon="mdiLeaf"
            :color="overallLeafColor"
            size="24"
            class="ml-1"
          />
        </template>
        <div class="text-caption">
          <strong>Ressourceneffizienz:</strong><br />
          0% bis 15% Einsparung: Grün<br />
          über 15% bis 20% Einsparung: Gelb<br />
          über 20% Einsparung: Rot
        </div>
      </v-tooltip>
    </template>
    <template #toolbar-actions>
      <!-- CSV-Export Button -->
      <v-tooltip
        location="top"
        text="Green-IT Bericht als CSV exportieren"
      >
        <template #activator="{ props: tooltipProps }">
          <v-btn
            v-bind="tooltipProps"
            :icon="mdiFileExportOutline"
            variant="flat"
            @click.stop="exportGreenItCsv"
          />
        </template>
      </v-tooltip>
    </template>

    <v-row>
      <!-- CPU Spalte -->
      <!-- CPU Spalte -->
      <v-col cols="6">
        <h3 class="d-flex align-center">
          CPU Ressourcen
          <v-tooltip location="top">
            <template #activator="{ props: tooltipProps }">
              <v-icon
                  v-bind="tooltipProps"
                  :icon="mdiLeaf"
                  :color="cpuLeafColor"
                  size="20"
                  class="ml-2"
              />
            </template>
            <span class="text-caption">
              Zum aktuellen Zeitpunkt maximal mögliche Einsparung inkl. ausgeschalteten Servern <br /> und Servern mit abgelehnten Green-IT Maßnahmen
            </span>
          </v-tooltip>
        </h3>
        <div class="mt-2">
          <p><strong>Gesamt (Ist):</strong> {{ totalCpuCurrent }} Cores</p>
          <p><strong>Gesamt (Soll):</strong> {{ totalCpuTarget }} Cores</p>
          <p class="mt-1 d-flex align-center flex-wrap">
            <span>
              <strong>Mögliche Gesamteinsparung:</strong>
              {{ totalCpuSavings }} Cores
              <span v-if="totalCpuSavings > 0">
                ({{ cpuSavingsPercentage }}%)
              </span>
            </span>
            <v-tooltip location="top">
              <template #activator="{ props: tooltipProps }">
                <v-icon
                  v-bind="tooltipProps"
                  :icon="mdiInformationOutline"
                  size="16"
                  class="ml-1 text-medium-emphasis"
                />
              </template>
              <div class="text-caption">
                <strong>Ressourcenanpassung:</strong>
                {{ resizingOnlyCpuSavings }} Cores<br />
                <span v-if="hasPoweredOffServers">
                  <strong>ggf. abbaubar (poweredOff):</strong>
                  {{ decommissionCpuSavings }} Cores
                </span>
                <span v-else>
                  <strong>ggf. abbaubar (poweredOff):</strong> 0 Cores (keine
                  ausgeschalteten Server)
                </span>
              </div>
            </v-tooltip>
          </p>
        </div>
      </v-col>

      <!-- RAM Spalte -->
      <!-- RAM Spalte -->
      <v-col cols="6">
        <h3 class="d-flex align-center">
          RAM Ressourcen
          <v-tooltip location="top">
            <template #activator="{ props: tooltipProps }">
              <v-icon
                  v-bind="tooltipProps"
                  :icon="mdiLeaf"
                  :color="ramLeafColor"
                  size="20"
                  class="ml-2"
              />
            </template>
            <span class="text-caption">
              Zum aktuellen Zeitpunkt maximal mögliche Einsparung inkl. ausgeschalteten Servern <br /> und Servern mit abgelehnten Green-IT Maßnahmen
            </span>
          </v-tooltip>
        </h3>
        <div class="mt-2">
          <p>
            <strong>Gesamt (Ist):</strong>
            {{ formatter.formatMBtoGB(totalRamCurrentMb) }} GB
          </p>
          <p>
            <strong>Gesamt (Soll):</strong>
            {{ formatter.formatMBtoGB(totalRamTargetMb) }} GB
          </p>
          <p class="mt-1 d-flex align-center flex-wrap">
            <span>
              <strong>Mögliche Gesamteinsparung:</strong>
              {{ formatter.formatMBtoGB(totalRamSavingsMb) }} GB
              <span v-if="totalRamSavingsMb > 0">
                ({{ ramSavingsPercentage }}%)
              </span>
            </span>
            <v-tooltip location="top">
              <template #activator="{ props: tooltipProps }">
                <v-icon
                  v-bind="tooltipProps"
                  :icon="mdiInformationOutline"
                  size="16"
                  class="ml-1 text-medium-emphasis"
                />
              </template>
              <div class="text-caption">
                <strong>Ressourcenanpassung:</strong>
                {{ formatter.formatMBtoGB(resizingOnlyRamSavingsMb) }} GB<br />
                <span v-if="hasPoweredOffServers">
                  <strong>ggf. abbaubar (poweredOff):</strong>
                  {{ formatter.formatMBtoGB(decommissionRamSavingsMb) }} GB
                </span>
                <span v-else>
                  <strong>ggf. abbaubar (poweredOff):</strong> 0 GB (keine
                  ausgeschalteten Server)
                </span>
              </div>
            </v-tooltip>
          </p>
        </div>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";
import type { ServerListExtended } from "@/types/ServerListExtended.ts";

import { mdiFileExportOutline, mdiInformationOutline, mdiLeaf } from "@mdi/js";
import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();

const servers = computed(() => props.selectedAppservice?.servers || []);

const isPoweredOff = (server: ServerListExtended) =>
  server.powerState === "poweredOff";

const hasPoweredOffServers = computed(() =>
  servers.value.some((server: ServerListExtended) => isPoweredOff(server))
);

const getResizeSavings = (current: number, recVal: unknown): number => {
  if (recVal != null && recVal !== "" && Number(recVal) !== 0) {
    const recommended = Number(recVal);
    if (recommended < current) {
      return current - recommended;
    }
  }
  return 0;
};

// --- 1. Aktuelle Gesamtzahlen (Ist) berechnen ---
const totalCpuCurrent = computed(() => {
  return servers.value.reduce(
    (sum: number, server: ServerListExtended) =>
      sum + Number(server.numCpu || 0),
    0
  );
});

const totalRamCurrentMb = computed(() => {
  return servers.value.reduce(
    (sum: number, server: ServerListExtended) =>
      sum + Number(server.memoryMb || 0),
    0
  );
});

// --- 2. Einsparungen nach Hebel berechnen ---
// Potenzielle Einsparung durch Abbau ausgeschalteter Server (belegen aktuell Ressourcen)
const decommissionCpuSavings = computed(() => {
  return servers.value.reduce((sum: number, server: ServerListExtended) => {
    if (!isPoweredOff(server)) return sum;
    return sum + Number(server.numCpu || 0);
  }, 0);
});

const decommissionRamSavingsMb = computed(() => {
  return servers.value.reduce((sum: number, server: ServerListExtended) => {
    if (!isPoweredOff(server)) return sum;
    return sum + Number(server.memoryMb || 0);
  }, 0);
});

// Nur über Ressourcenanpassung (bei nicht ausgeschalteten Servern)
const resizingOnlyCpuSavings = computed(() => {
  return servers.value.reduce((sum: number, server: ServerListExtended) => {
    if (isPoweredOff(server)) return sum;
    const current = Number(server.numCpu || 0);
    return sum + getResizeSavings(current, server.numCpuRecommended);
  }, 0);
});

const resizingOnlyRamSavingsMb = computed(() => {
  return servers.value.reduce((sum: number, server: ServerListExtended) => {
    if (isPoweredOff(server)) return sum;
    const current = Number(server.memoryMb || 0);
    return sum + getResizeSavings(current, server.memoryMbRecommended);
  }, 0);
});

// Mögliche Gesamteinsparung (Ressourcenanpassung + Abbau-Potenzial von poweredOff-Servern)
const totalCpuSavings = computed(() => {
  return resizingOnlyCpuSavings.value + decommissionCpuSavings.value;
});

const totalRamSavingsMb = computed(() => {
  return resizingOnlyRamSavingsMb.value + decommissionRamSavingsMb.value;
});

// --- 3. Zielwerte (Soll) berechnen ---
const totalCpuTarget = computed(() => {
  return Math.max(0, totalCpuCurrent.value - totalCpuSavings.value);
});

const totalRamTargetMb = computed(() => {
  return Math.max(0, totalRamCurrentMb.value - totalRamSavingsMb.value);
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

const cpuLeafColor = computed(() =>
  getLeafColor(Number(cpuSavingsPercentage.value))
);
const ramLeafColor = computed(() =>
  getLeafColor(Number(ramSavingsPercentage.value))
);
const overallLeafColor = computed(() => {
  const maxPct = Math.max(
    Number(cpuSavingsPercentage.value),
    Number(ramSavingsPercentage.value)
  );
  return getLeafColor(maxPct);
});

// --- 6. CSV-Export Funktion ---
const exportGreenItCsv = () => {
  const currentServers = servers.value;
  if (currentServers.length === 0) return;

  const headers = [
    "Servername",
    "Status",
    "CPU Ist",
    "CPU Empfohlen",
    "CPU Einsparung (Cores)",
    "RAM Ist (MB)",
    "RAM Empfohlen (MB)",
    "RAM Einsparung (MB)",
  ];

  const rows = currentServers.map((server: ServerListExtended) => {
    const poweredOff = isPoweredOff(server);
    const currentCpu = Number(server.numCpu || 0);
    const recCpu = Number(server.numCpuRecommended || 0);
    const cpuSavings = poweredOff
      ? currentCpu
      : getResizeSavings(currentCpu, server.numCpuRecommended);

    const currentRam = Number(server.memoryMb || 0);
    const recRam = Number(server.memoryMbRecommended || 0);
    const ramSavings = poweredOff
      ? currentRam
      : getResizeSavings(currentRam, server.memoryMbRecommended);

    return [
      `"${server.name || ""}"`,
      `"${server.powerState || ""}"`,
      currentCpu,
      recCpu > 0 ? recCpu : "",
      cpuSavings,
      currentRam,
      recRam > 0 ? recRam : "",
      ramSavings,
    ];
  });

  const csvContent = [
    headers.join(";"),
    ...rows.map((row) => row.join(";")),
  ].join("\r\n");

  const blob = new Blob(["\ufeff" + csvContent], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  const appserviceName = props.selectedAppservice?.name || "appservice";
  link.setAttribute("href", url);
  link.setAttribute("download", `green-it-einsparungen-${appserviceName}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};
</script>
