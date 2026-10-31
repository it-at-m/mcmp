<template>
  <common-card
    title="Unmanaged Server (Virtual Appliances ohne vCenter-Tag oder Server ohne Foreman-Eintrag)"
  >
    <template #append-title>
      <div class="d-flex align-center ga-2">
        <count-badge :count="items.length" />
        <v-btn
          size="small"
          variant="tonal"
          color="green-darken-2"
          :prepend-icon="mdiMicrosoftExcel"
          :disabled="items.length === 0"
          @click.stop="exportCsv"
          @mousedown.stop
        >
          CSV Export
        </v-btn>
      </div>
    </template>

    <v-card
      variant="tonal"
      class="mb-3 pa-3"
    >
      <div class="d-flex align-center ga-2 mb-3">
        <v-icon :icon="mdiFilterVariant" />
        <span class="text-subtitle-2">Filter</span>
        <v-spacer />
        <v-btn
          size="small"
          variant="text"
          :prepend-icon="mdiRestore"
          @click="resetFilters"
        >
          Standard wiederherstellen
        </v-btn>
      </div>

      <div
        v-for="group in filterGroups"
        :key="group.key"
        class="mb-3"
      >
        <div class="text-subtitle-2 mb-1">{{ group.label }}</div>

        <div class="d-flex flex-wrap align-center ga-2 mb-2">
          <v-chip
            v-for="pattern in filters[group.key]"
            :key="pattern"
            size="small"
            label
            closable
            @click:close="removeFilter(group.key, pattern)"
          >
            {{ pattern }}
          </v-chip>
          <span
            v-if="filters[group.key].length === 0"
            class="text-body-2 text-medium-emphasis"
          >
            Keine Filter aktiv
          </span>
        </div>

        <div class="d-flex align-center">
          <v-text-field
            :model-value="drafts[group.key]"
            :placeholder="group.placeholder"
            density="compact"
            variant="outlined"
            hide-details="auto"
            class="filter-input"
            :error-messages="draftErrors[group.key]"
            @update:model-value="onDraftInput(group.key, $event)"
            @keydown="blockComma"
            @keyup.enter="addFilter(group.key)"
          >
            <template #append-inner>
              <v-btn
                size="small"
                variant="text"
                :disabled="!drafts[group.key].trim()"
                @click="addFilter(group.key)"
              >
                Hinzufügen
              </v-btn>
            </template>
          </v-text-field>
        </div>
      </div>
    </v-card>

    <v-data-table
      :headers="headers"
      :items="tableItems"
      class="elevation-1 links"
      :loading="loading"
      :sort-by="sortBy"
      :items-per-page="25"
    >
      <template #no-data>
        <div
          v-if="!loading"
          class="wrapping-cell py-4 text-center"
        >
          Keine nicht verwalteten VMs gefunden!
        </div>
      </template>

      <template #item.cloudName="{ item }">
        <div class="wrapping-cell">
          <a
            v-if="cloudLink(item)"
            :href="cloudLink(item) ?? undefined"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ item.cloudName }}
          </a>
          <template v-else>{{ item.cloudName }}</template>
        </div>
      </template>

      <template #item.cluster="{ item }">
        <div class="wrapping-cell">{{ item.cluster }}</div>
      </template>

      <template #item.serverName="{ item }">
        <div class="d-flex align-center">
          <div class="power-state-icon-inline mr-2">
            <v-icon
              :color="powerStateColor(item.serverPowerState)"
              size="20"
              @mouseenter="
                onRowTooltipHover($event, powerStateText(item.serverPowerState))
              "
              @mouseleave="onRowTooltipLeave"
              @focus="
                onRowTooltipHover($event, powerStateText(item.serverPowerState))
              "
              @blur="onRowTooltipLeave"
            >
              {{ powerStateIcon(item.serverPowerState) }}
            </v-icon>
          </div>
          <span class="font-weight-bold">
            <router-link :to="`/server/${item.serverID}`">
              {{ item.serverName }}
            </router-link>
          </span>
        </div>
      </template>

      <template #item.serverOS="{ item }">
        <div
          class="wrapping-cell"
          :title="item.serverOS"
        >
          {{ truncateOs(item.serverOS) }}
        </div>
      </template>

      <template #item.remark="{ item }">
        <div class="wrapping-cell">{{ item.remark }}</div>
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
import type UnmanagedServerDTO from "@/types/UnmanagedServerDTO.ts";
import type { CsvColumn } from "@/util/csv";
import type { DataTableHeader } from "vuetify/framework";

import {
  mdiFilterVariant,
  mdiMicrosoftExcel,
  mdiPauseCircle,
  mdiPlayCircle,
  mdiRestore,
  mdiStopCircle,
} from "@mdi/js";
import { computed, onMounted, reactive, ref, watch } from "vue";

import reportService from "@/api/reportService";
import CommonCard from "@/components/common/CommonCard.vue";
import CountBadge from "@/components/common/CountBadge.vue";
import { buildCsv, downloadCsv } from "@/util/csv";

type FilterKey = "excludeName" | "excludeCluster";
type UnmanagedServerRow = UnmanagedServerDTO & { remark: string };
type PowerStateKey = "poweredOn" | "poweredOff" | "standby";

const FOREMAN_MISSING_OS_PATTERNS: readonly string[] = [
  "Windows",
  "Red Hat",
  "SUSE",
];
const VIRTUAL_APPLIANCE_OS_PATTERNS: readonly string[] = [
  "Debian",
  "Photon",
  "Ubuntu",
  "Other",
  "Rocky",
  "Forti",
  "CentOS",
  "flatcar",
];
const REMARK_FOREMAN_MISSING = "🤔 Foreman?";
const REMARK_VIRTUAL_APPLIANCE_TAG = "🏷️ Virtual Appliance?";
const DEFAULT_EXCLUDE_NAMES: readonly string[] = [
  "esxi%",
  "pve%",
  "%-master-%",
  "%-infra-%",
  "%-ingress-%",
  "%-worker-%",
  "vSAN File Service Node %",
];
const DEFAULT_EXCLUDE_CLUSTERS: readonly string[] = [
  "%-IAAS%",
  "CL-TL-LAB001",
  "%-CTX%",
  "%-HSD%",
  "%-VDI%",
  "%-LAB%",
];
const OS_MAX_DISPLAY_LENGTH = 40;
const FORBIDDEN_FILTER_CHARACTER = ",";
const FORBIDDEN_FILTER_MESSAGE = 'Das Zeichen "," ist nicht erlaubt.';

const loading = ref(false);
const items = ref<UnmanagedServerDTO[]>([]);

const rowTooltipActivator = ref<HTMLElement | undefined>(undefined);
const rowTooltipText = ref("");

const filters = reactive<Record<FilterKey, string[]>>({
  excludeName: [...DEFAULT_EXCLUDE_NAMES],
  excludeCluster: [...DEFAULT_EXCLUDE_CLUSTERS],
});

const drafts = reactive<Record<FilterKey, string>>({
  excludeName: "",
  excludeCluster: "",
});

const draftErrors = reactive<Record<FilterKey, string>>({
  excludeName: "",
  excludeCluster: "",
});

const filterGroups: {
  key: FilterKey;
  label: string;
  placeholder: string;
}[] = [
  {
    key: "excludeName",
    label: "Exclude Namensfilter",
    placeholder: "Namensfilter hinzufügen",
  },
  {
    key: "excludeCluster",
    label: "Exclude Clusterfilter",
    placeholder: "Clusterfilter hinzufügen",
  },
];

const sortBy = ref([{ key: "serverName", order: "asc" as "asc" | "desc" }]);

const headers = ref<DataTableHeader[]>([
  {
    title: "Cloud",
    key: "cloudName",
    align: "start",
    sortable: true,
  },
  {
    title: "Cluster",
    key: "cluster",
    align: "start",
    sortable: true,
  },
  {
    title: "Name",
    key: "serverName",
    align: "start",
    sortable: true,
  },
  {
    title: "Betriebssystem",
    key: "serverOS",
    align: "start",
    sortable: true,
  },
  {
    title: "Bemerkung",
    key: "remark",
    align: "start",
    sortable: true,
  },
]);

const csvColumns: CsvColumn<UnmanagedServerRow>[] = [
  { header: "Cloud", value: (item) => item.cloudName },
  { header: "vCenter GUI", value: (item) => item.vCenterServerGUI },
  { header: "Cluster", value: (item) => item.cluster },
  { header: "Power State", value: (item) => item.serverPowerState },
  { header: "vCenter VM-ID", value: (item) => item.serverVMID },
  { header: "Server Name", value: (item) => item.serverName },
  { header: "Server FQDN", value: (item) => item.serverFQDN },
  { header: "Betriebssystem", value: (item) => item.serverOS },
  { header: "Bemerkung", value: (item) => item.remark },
];

function addFilter(key: FilterKey): void {
  const rawPattern = drafts[key];
  const pattern = stripForbiddenCharacters(rawPattern).trim();

  if (rawPattern !== stripForbiddenCharacters(rawPattern)) {
    draftErrors[key] = FORBIDDEN_FILTER_MESSAGE;
    drafts[key] = pattern;
    return;
  }

  if (!pattern) {
    return;
  }
  if (!filters[key].includes(pattern)) {
    filters[key].push(pattern);
  }
  drafts[key] = "";
  draftErrors[key] = "";
}

function stripForbiddenCharacters(value: string): string {
  return value.split(FORBIDDEN_FILTER_CHARACTER).join("");
}

function onDraftInput(key: FilterKey, value: string | number | null): void {
  const raw = String(value ?? "");
  const sanitized = stripForbiddenCharacters(raw);

  draftErrors[key] = sanitized === raw ? "" : FORBIDDEN_FILTER_MESSAGE;
  drafts[key] = sanitized;
}

/** Blockiert die Komma-Taste, damit das Zeichen gar nicht erst erscheint. */
function blockComma(event: KeyboardEvent): void {
  if (event.key === FORBIDDEN_FILTER_CHARACTER) {
    event.preventDefault();
  }
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

function cloudLink(item: UnmanagedServerDTO): string | null {
  const cloudName = item.cloudName?.trim();
  const serverVMID = item.serverVMID?.trim();
  const serverGui = item.vCenterServerGUI?.trim();

  if (!cloudName || !serverVMID || !serverGui) {
    return null;
  }

  return `https://${cloudName}/ui/app/vm;nav=v/urn:vmomi:VirtualMachine:${serverVMID}:${serverGui}/summary`;
}

function onRowTooltipHover(event: Event, text: string) {
  rowTooltipActivator.value = event.currentTarget as HTMLElement;
  rowTooltipText.value = text;
}

function onRowTooltipLeave() {
  rowTooltipActivator.value = undefined;
}

function removeFilter(key: FilterKey, pattern: string): void {
  filters[key] = filters[key].filter((entry) => entry !== pattern);
}

function resetFilters(): void {
  filters.excludeName = [...DEFAULT_EXCLUDE_NAMES];
  filters.excludeCluster = [...DEFAULT_EXCLUDE_CLUSTERS];
  drafts.excludeName = "";
  drafts.excludeCluster = "";
  draftErrors.excludeName = "";
  draftErrors.excludeCluster = "";
}

function getUnmanagedVms() {
  reportService
    .getUnmanagedVms(loading, filters.excludeName, filters.excludeCluster)
    .then((response) => {
      items.value = response;
    })
    .catch((error) => {
      console.debug("Fehler beim Laden der nicht verwalteten VMs:", error);
    });
}

function truncateOs(os: string): string {
  if (!os) {
    return "";
  }
  return os.length > OS_MAX_DISPLAY_LENGTH
    ? `${os.slice(0, OS_MAX_DISPLAY_LENGTH)}…`
    : os;
}

function buildRemark(os: string): string {
  if (!os) {
    return "";
  }

  const normalized = os.toLowerCase();

  if (
    FOREMAN_MISSING_OS_PATTERNS.some((pattern) =>
      normalized.includes(pattern.toLowerCase())
    )
  ) {
    return REMARK_FOREMAN_MISSING;
  }

  if (
    VIRTUAL_APPLIANCE_OS_PATTERNS.some((pattern) =>
      normalized.includes(pattern.toLowerCase())
    )
  ) {
    return REMARK_VIRTUAL_APPLIANCE_TAG;
  }

  return "";
}

const tableItems = computed<UnmanagedServerRow[]>(() =>
  items.value.map((item) => ({
    ...item,
    remark: buildRemark(item.serverOS),
  }))
);

function exportCsv() {
  if (items.value.length === 0) {
    return;
  }

  const date = new Date().toISOString().slice(0, 10);
  const filename = `nicht-verwaltete-vms_${date}.csv`;

  downloadCsv(filename, buildCsv(items.value, csvColumns));
}

// Reload whenever filters are added, removed or reset.
watch(filters, () => getUnmanagedVms(), { deep: true });

onMounted(() => {
  getUnmanagedVms();
});
</script>

<style scoped>
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

.filter-input {
  min-width: 320px;
  max-width: 420px;
}

.filter-group {
  width: 100%;
}
</style>
