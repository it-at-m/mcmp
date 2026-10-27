<template>
  <common-card title="Foreman IP-Abweichungen">
    <template #append-title>
      <div class="d-flex align-center ga-2">
        <count-badge :count="items.length" />
        <v-btn
          size="small"
          variant="tonal"
          color="green-darken-2"
          :prepend-icon="mdiMicrosoftExcel"
          :disabled="filteredItems.length === 0"
          @click.stop="exportCsv"
          @mousedown.stop
        >
          CSV Export
        </v-btn>
      </div>
    </template>

    <div class="d-flex align-center flex-wrap ga-2 mb-3">
      <span class="text-body-2 text-medium-emphasis">Power-Status:</span>
      <v-chip-group
        v-model="powerStateFilter"
        mandatory
        class="power-state-filter"
      >
        <v-chip
          v-for="option in powerStateFilterOptions"
          :key="option.value"
          :value="option.value"
          :prepend-icon="option.icon"
          :color="option.color"
          variant="tonal"
          size="small"
        >
          {{ option.title }}
          <span class="ms-1 font-weight-bold">({{ option.count }})</span>
        </v-chip>
      </v-chip-group>
    </div>

    <div class="d-flex align-center flex-wrap ga-2 mb-3">
      <span class="text-body-2 text-medium-emphasis">Abweichende IPs:</span>
      <v-chip-group
        v-model="ipCountFilter"
        mandatory
        class="ip-count-filter"
      >
        <v-chip
          v-for="option in ipCountFilterOptions"
          :key="option.value"
          :value="option.value"
          :prepend-icon="option.icon"
          :color="option.color"
          variant="tonal"
          size="small"
        >
          {{ option.title }}
          <span class="ms-1 font-weight-bold">({{ option.count }})</span>
        </v-chip>
      </v-chip-group>
    </div>

    <v-data-table
      :headers="headers"
      :items="filteredItems"
      item-value="server_name"
      class="elevation-1 links mismatch-table"
      :loading="loading"
      :sort-by="sortBy"
      :items-per-page="25"
      show-expand
    >
      <template #no-data>
        <div
          v-if="!loading"
          class="wrapping-cell py-4 text-center"
        >
          {{ noDataText }}
        </div>
      </template>

      <template #item.server_name="{ item }">
        <div class="d-flex align-center">
          <div class="power-state-icon-inline mr-2">
            <v-icon
              :color="powerStateColor(item.power_state)"
              size="20"
              @mouseenter="
                onRowTooltipHover($event, powerStateText(item.power_state))
              "
              @mouseleave="onRowTooltipLeave"
              @focus="
                onRowTooltipHover($event, powerStateText(item.power_state))
              "
              @blur="onRowTooltipLeave"
            >
              {{ powerStateIcon(item.power_state) }}
            </v-icon>
          </div>
          <span class="font-weight-bold">
            <router-link :to="`/server/${item.server_id}`">
              {{ item.server_name }}
            </router-link>
          </span>
        </div>
      </template>

      <template #item.foreman_host_name="{ item }">
        <div class="wrapping-cell">
          <span class="font-weight-bold">
            <a
              v-if="getForemanHost(item)"
              :href="`https://${item.foreman_fqdn}/new/hosts/${item.foreman_host_name}`"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ getHostname(item.foreman_host_name) }}
            </a>
            <template v-else>
              {{ getHostname(item.foreman_host_name) }}
            </template>
          </span>
        </div>
      </template>

      <template #item.ips="{ item }">
        <v-chip
          size="small"
          variant="tonal"
          color="error"
        >
          {{ item.ips.length }}
        </v-chip>
      </template>

      <template #expanded-row="{ item }">
        <tr class="mismatch-expanded-row">
          <td
            :colspan="headers.length + 1"
            class="pa-0 mismatch-expanded-cell"
          >
            <div class="mismatch-detail-wrapper">
              <v-table
                density="compact"
                class="mismatch-detail"
              >
                <thead>
                  <tr>
                    <th class="text-left mismatch-col-if">MCMP Interface</th>
                    <th class="text-left mismatch-col-ip">MCMP IP</th>
                    <th class="text-left mismatch-col-net">MCMP Netzwerk</th>
                    <th class="text-left mismatch-col-if">Foreman Interface</th>
                    <th class="text-left mismatch-col-ip">Foreman IP</th>
                    <th class="text-left mismatch-col-net">Foreman Netzwerk</th>
                    <th class="text-left mismatch-col-status">Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(ip, index) in item.ips"
                    :key="index"
                  >
                    <td class="mismatch-col-if">
                      {{ formatValue(ip.server_if) }}
                    </td>
                    <td class="mismatch-col-ip">
                      {{ formatIp(ip.server_ip) }}
                    </td>
                    <td class="mismatch-col-net">
                      {{ formatValue(ip.server_network) }}
                    </td>
                    <td class="mismatch-col-if">
                      {{ formatValue(ip.foreman_if) }}
                    </td>
                    <td class="mismatch-col-ip">
                      {{ formatIp(ip.foreman_ip) }}
                    </td>
                    <td class="mismatch-col-net">
                      {{ formatValue(ip.foreman_network) }}
                    </td>
                    <td class="mismatch-col-status">
                      <v-chip
                        size="small"
                        :variant="statusChipVariant"
                        :color="statusColor(ip.status)"
                      >
                        {{ ip.status }}
                      </v-chip>
                    </td>
                  </tr>
                  <tr v-if="item.ips.length === 0">
                    <td
                      colspan="7"
                      class="wrapping-cell py-2 text-center"
                    >
                      Keine Details vorhanden.
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </div>
          </td>
        </tr>
      </template>
    </v-data-table>
    <v-tooltip
      :model-value="!!rowTooltipActivator"
      :activator="rowTooltipActivator"
      :text="rowTooltipText"
    />
  </common-card>
</template>

<script setup lang="ts">
import type ForemanIpMismatchServer from "@/types/ForemanIpMismatchServer";
import type { CsvColumn } from "@/util/csv";
import type { DataTableHeader } from "vuetify/framework";

import {
  mdiAlertCircleOutline,
  mdiFormatListBulleted,
  mdiMicrosoftExcel,
  mdiPauseCircle,
  mdiPlayCircle,
  mdiPower,
  mdiStopCircle,
} from "@mdi/js";
import { computed, onMounted, ref } from "vue";
import { useTheme } from "vuetify";

import reportService from "@/api/reportService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import { buildCsv, downloadCsv } from "@/util/csv";

const theme = useTheme();
const statusChipVariant = computed<"flat" | "tonal">(() =>
  theme.current.value.dark ? "tonal" : "flat"
);
const loading = ref(false);
const items = ref<ForemanIpMismatchServer[]>([]);

type PowerStateKey = "poweredOn" | "poweredOff" | "standby";
type PowerStateFilter = "all" | PowerStateKey;
type IpCountFilter = "all" | number;

const powerStateFilter = ref<PowerStateFilter>("all");
const ipCountFilter = ref<IpCountFilter>("all");

const sortBy = ref([{ key: "server_name", order: "asc" as "asc" | "desc" }]);

const rowTooltipActivator = ref<HTMLElement | undefined>(undefined);
const rowTooltipText = ref("");

const headers = ref<DataTableHeader[]>([
  {
    title: "Server",
    key: "server_name",
    align: "start",
    sortable: true,
    width: 320,
  },
  {
    title: "Foreman-Host",
    key: "foreman_host_name",
    align: "start",
    sortable: true,
    width: 320,
  },
  {
    title: "Abweichende IPs",
    key: "ips",
    align: "start",
    sortable: true,
    // Nach Anzahl der abweichenden IPs sortieren, nicht nach dem Array-Inhalt
    sortRaw: (a, b) =>
      (a as ForemanIpMismatchServer).ips.length -
      (b as ForemanIpMismatchServer).ips.length,
  },
]);

interface CsvRow {
  server_name: string;
  foreman_host_name: string;
  power_state: string;
  server_if: string;
  server_ip: string;
  server_network: string;
  foreman_if: string;
  foreman_ip: string;
  foreman_network: string;
  status: string;
}

type CsvIpDetail = Omit<
  CsvRow,
  "server_name" | "foreman_host_name" | "power_state"
>;

const emptyIpDetail: CsvIpDetail = {
  server_if: "",
  server_ip: "",
  server_network: "",
  foreman_if: "",
  foreman_ip: "",
  foreman_network: "",
  status: "",
};

const csvColumns: CsvColumn<CsvRow>[] = [
  { header: "Server", value: (item) => item.server_name },
  { header: "Foreman-Host", value: (item) => item.foreman_host_name },
  { header: "Power-Status", value: (item) => item.power_state },
  { header: "MCMP Interface", value: (item) => item.server_if },
  { header: "MCMP IP", value: (item) => item.server_ip },
  { header: "MCMP Netzwerk", value: (item) => item.server_network },
  { header: "Foreman Interface", value: (item) => item.foreman_if },
  { header: "Foreman IP", value: (item) => item.foreman_ip },
  { header: "Foreman Netzwerk", value: (item) => item.foreman_network },
  { header: "Status", value: (item) => item.status },
];

const powerStateCounts = computed<Record<PowerStateKey, number>>(() => {
  const counts: Record<PowerStateKey, number> = {
    poweredOn: 0,
    poweredOff: 0,
    standby: 0,
  };
  for (const item of itemsMatchingIpCount.value) {
    const key = normalizePowerState(item.power_state);
    if (key === "poweredOn") {
      counts.poweredOn += 1;
    } else if (key === "poweredOff") {
      counts.poweredOff += 1;
    } else {
      counts.standby += 1;
    }
  }
  return counts;
});

const itemsMatchingPowerState = computed<ForemanIpMismatchServer[]>(() =>
  powerStateFilter.value === "all"
    ? items.value
    : items.value.filter(
        (item) =>
          normalizePowerState(item.power_state) === powerStateFilter.value
      )
);

const itemsMatchingIpCount = computed<ForemanIpMismatchServer[]>(() =>
  ipCountFilter.value === "all"
    ? items.value
    : items.value.filter((item) => item.ips.length === ipCountFilter.value)
);

const ipCountCounts = computed<Map<number, number>>(() => {
  const counts = new Map<number, number>();
  for (const item of itemsMatchingPowerState.value) {
    const count = item.ips.length;
    counts.set(count, (counts.get(count) ?? 0) + 1);
  }
  return counts;
});

const ipCountFilterOptions = computed<
  {
    value: IpCountFilter;
    title: string;
    icon: string;
    color: string;
    count: number;
  }[]
>(() => {
  const values = [...ipCountCounts.value.keys()].sort((a, b) => a - b);
  return [
    {
      value: "all",
      title: "Alle",
      icon: mdiFormatListBulleted,
      color: "secondary",
      count: itemsMatchingPowerState.value.length,
    },
    ...values.map((value) => ({
      value,
      title: String(value),
      icon: mdiAlertCircleOutline,
      color: value <= 1 ? "warning" : "error",
      count: ipCountCounts.value.get(value) ?? 0,
    })),
  ];
});

const powerStateFilterOptions = computed<
  {
    value: PowerStateFilter;
    title: string;
    icon: string;
    color: string;
    count: number;
  }[]
>(() => [
  {
    value: "all",
    title: "Alle",
    icon: mdiPower,
    color: "secondary",
    count: items.value.length,
  },
  {
    value: "poweredOn",
    title: "Ein",
    icon: mdiPlayCircle,
    color: "btn_green",
    count: powerStateCounts.value.poweredOn,
  },
  {
    value: "poweredOff",
    title: "Aus",
    icon: mdiStopCircle,
    color: "btn_red",
    count: powerStateCounts.value.poweredOff,
  },
  {
    value: "standby",
    title: "Standby",
    icon: mdiPauseCircle,
    color: "accent",
    count: powerStateCounts.value.standby,
  },
]);

const filteredItems = computed<ForemanIpMismatchServer[]>(() => {
  const powerFilter = powerStateFilter.value ?? "all";
  const countFilter = ipCountFilter.value ?? "all";
  return items.value.filter((item) => {
    if (
      powerFilter !== "all" &&
      normalizePowerState(item.power_state) !== powerFilter
    ) {
      return false;
    }
    if (countFilter !== "all" && item.ips.length !== countFilter) {
      return false;
    }
    return true;
  });
});

const noDataText = computed<string>(() => {
  const powerFilter = powerStateFilter.value ?? "all";
  const countFilter = ipCountFilter.value ?? "all";
  if (powerFilter === "all" && countFilter === "all") {
    return "Keine IP-Abweichungen zwischen MCMP und Foreman gefunden!";
  }
  if (countFilter === "all") {
    return "Keine IP-Abweichungen mit dem gewählten Power-Status gefunden!";
  }
  if (powerFilter === "all") {
    return `Keine Server mit ${ipCountLabel(countFilter)} gefunden!`;
  }
  return `Keine Server mit dem gewählten Power-Status und ${ipCountLabel(
    countFilter
  )} gefunden!`;
});

function ipCountLabel(count: number): string {
  return `${count} abweichende${count === 1 ? "r" : "n"} IP${
    count === 1 ? "" : "s"
  }`;
}

function normalizePowerState(powerState: string): PowerStateKey {
  switch (powerState) {
    case "poweredOn":
      return "poweredOn";
    case "poweredOff":
      return "poweredOff";
    default:
      return "standby";
  }
}

function onRowTooltipHover(event: Event, text: string) {
  rowTooltipActivator.value = event.currentTarget as HTMLElement;
  rowTooltipText.value = text;
}

function onRowTooltipLeave() {
  rowTooltipActivator.value = undefined;
}

function getForemanIpMismatches() {
  reportService
    .getForemanIpMismatches(loading)
    .then((response) => {
      items.value = response;
    })
    .catch((error) => {
      console.debug("Fehler beim Laden der Foreman-IP-Abweichungen:", error);
    });
}

function getForemanHost(item: ForemanIpMismatchServer): string {
  const host = item.foreman_fqdn?.trim();
  return host ? host : "";
}

function powerStateIcon(powerState: string): string {
  switch (normalizePowerState(powerState)) {
    case "poweredOn":
      return mdiPlayCircle;
    case "poweredOff":
      return mdiStopCircle;
    default:
      return mdiPauseCircle;
  }
}

function powerStateColor(powerState: string): string {
  switch (normalizePowerState(powerState)) {
    case "poweredOn":
      return "btn_green";
    case "poweredOff":
      return "btn_red";
    default:
      return "accent";
  }
}

function powerStateText(powerState: string): string {
  switch (normalizePowerState(powerState)) {
    case "poweredOn":
      return "Ein";
    case "poweredOff":
      return "Aus";
    default:
      return "Standby";
  }
}

function getHostname(fqdn: string): string {
  if (!fqdn) {
    return "";
  }
  const firstDot = fqdn.indexOf(".");
  return firstDot === -1 ? fqdn : fqdn.substring(0, firstDot);
}

function formatIp(ip: string | null): string {
  return ip ? ip : "–";
}

function formatValue(value: string | null): string {
  return value ? value : "–";
}

function statusColor(status: string): string {
  switch (status) {
    case "nur_in_MCMP":
      return "warning";
    case "nur_in_Foreman":
      return "info";
    case "kein_nic":
    case "keine_IPv4":
      return "error";
    default:
      return "secondary";
  }
}

function buildCsvRows(): CsvRow[] {
  const rows: CsvRow[] = [];

  for (const server of filteredItems.value) {
    const base = {
      server_name: server.server_name,
      foreman_host_name: server.foreman_host_name,
      power_state: powerStateText(server.power_state),
    };

    const details: CsvIpDetail[] =
      server.ips.length === 0
        ? [emptyIpDetail]
        : server.ips.map((ip) => ({
            server_if: ip.server_if ?? "",
            server_ip: ip.server_ip ?? "",
            server_network: ip.server_network ?? "",
            foreman_if: ip.foreman_if ?? "",
            foreman_ip: ip.foreman_ip ?? "",
            foreman_network: ip.foreman_network ?? "",
            status: ip.status,
          }));

    for (const detail of details) {
      rows.push({ ...base, ...detail });
    }
  }

  return rows;
}

function exportCsv() {
  const rows = buildCsvRows();
  if (rows.length === 0) {
    return;
  }

  const date = new Date().toISOString().slice(0, 10);
  const filename = `foreman-ip-abweichungen_${date}.csv`;

  downloadCsv(filename, buildCsv(rows, csvColumns));
}

onMounted(() => {
  getForemanIpMismatches();
});
</script>

<style scoped>
.mismatch-table :deep(.v-table__wrapper > table) {
  width: 100%;
  table-layout: fixed;
}

.wrapping-cell {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.35;
}
.power-state-icon-inline {
  display: flex;
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

.mismatch-expanded-row,
.mismatch-expanded-cell {
  /* Gleiche Farbe wie die Server-Details (Kartenhintergrund) statt Grau */
  background-color: transparent !important;
}

.mismatch-detail-wrapper {
  /* Details eingerückt darstellen */
  padding: 8px 16px 12px 56px;
}

.mismatch-detail {
  /* Nicht über die volle Seitenbreite verteilen */
  width: auto !important;
  max-width: 100%;
  background-color: transparent !important;
}

.mismatch-detail :deep(.v-table__wrapper) {
  /* Bei zu wenig Platz horizontal scrollen statt Spalten zu quetschen */
  overflow-x: auto;
}

.mismatch-detail :deep(.v-table__wrapper > table) {
  width: auto;
  min-width: 100%;
  table-layout: auto;
}

/* Zellinhalte nicht umbrechen – Spalten richten sich nach dem Inhalt */
.mismatch-detail :deep(th),
.mismatch-detail :deep(td) {
  white-space: nowrap;
}

.mismatch-col-ip {
  min-width: 130px;
}

.mismatch-col-if {
  min-width: 170px;
}

.mismatch-col-net {
  min-width: 190px;
}

.mismatch-col-status {
  min-width: 140px;
}
</style>
