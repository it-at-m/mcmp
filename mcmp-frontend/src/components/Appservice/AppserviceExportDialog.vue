<template>
  <common-dialog
    v-model="internalValue"
    title="Daten aus Anwendungsservices exportieren"
    :icon="mdiFileExportOutline"
    max-width="700"
    show-actions
    :submit-activated="canExport"
    @dialog-cancel="close"
    @dialog-confirm="handleExport"
  >
    <div class="position-relative">
      <v-overlay
        :model-value="exporting"
        contained
        persistent
        class="align-center justify-center"
      >
        <div class="d-flex flex-column align-center ga-3">
          <v-progress-circular
            :model-value="exportProgressPercent"
            :size="72"
            :width="6"
            color="primary"
          >
            {{ exportProgressPercent }}%
          </v-progress-circular>
          <div class="text-body-2">Exportiere Daten…</div>
        </div>
      </v-overlay>

      <v-row>
        <v-col cols="12">
          <div class="d-flex align-center justify-space-between mb-2">
            <div class="text-subtitle-1 font-weight-bold">
              Anwendungsservices
            </div>
            <div class="d-flex ga-1">
              <v-tooltip
                :text="selectFavoritesTooltip"
                location="top"
              >
                <template #activator="{ props: tooltipProps }">
                  <span
                    v-bind="tooltipProps"
                    class="d-inline-block"
                  >
                    <v-btn
                      :icon="mdiStar"
                      size="small"
                      variant="text"
                      color="warning"
                      :loading="loadingOptions"
                      :disabled="selectFavoritesDisabled"
                      aria-label="Favoriten auswählen"
                      @click="selectFavorites"
                    />
                  </span>
                </template>
              </v-tooltip>
              <v-tooltip
                :text="selectAllTooltip"
                location="top"
              >
                <template #activator="{ props: tooltipProps }">
                  <span
                    v-bind="tooltipProps"
                    class="d-inline-block"
                  >
                    <v-btn
                      :icon="mdiSelectAll"
                      size="small"
                      variant="text"
                      :loading="loadingOptions"
                      :disabled="selectAllDisabled"
                      aria-label="Alle auswählen"
                      @click="selectAll"
                    />
                  </span>
                </template>
              </v-tooltip>
            </div>
          </div>
          <v-autocomplete
            v-model="selectedAppserviceIds"
            :items="appserviceOptions"
            :loading="loadingOptions"
            item-title="name"
            item-value="id"
            label="Anwendungsservices auswählen"
            placeholder="Appservice suchen..."
            multiple
            chips
            closable-chips
            clearable
            variant="outlined"
            rounded
            hide-details
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12">
          <div class="text-subtitle-1 font-weight-bold mb-2">Daten</div>
          <v-checkbox
            v-model="includeServer"
            label="Server"
            density="compact"
            hide-details
          />
          <v-checkbox
            v-model="includeLoadbalancer"
            label="Loadbalancer"
            density="compact"
            hide-details
          />
          <v-checkbox
            v-model="includeStorage"
            label="Storage"
            density="compact"
            hide-details
          />
          <v-checkbox
            v-model="includeOpenshift"
            label="Openshift"
            density="compact"
            hide-details
          />
        </v-col>
      </v-row>

      <v-alert
        v-if="exportError"
        type="error"
        class="mt-2"
        density="compact"
      >
        {{ exportError }}
      </v-alert>
    </div>
  </common-dialog>
</template>

<script setup lang="ts">
import type AppserviceListItem from "@/types/AppserviceList";
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem";
import type { OpenshiftNamespaceRef } from "@/types/OpenshiftNamespaceListItem";
import type { ServerListExtended } from "@/types/ServerListExtended";
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";

import { mdiFileExportOutline, mdiSelectAll, mdiStar } from "@mdi/js";
import { computed, inject, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService";
import loadbalancerService from "@/api/loadbalancerService";
import openshiftService from "@/api/openshiftService";
import storageService from "@/api/storageService";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useFormatter } from "@/composables/formatter";

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [val: boolean];
}>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const formatter = useFormatter();

const internalValue = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

const loadingOptions = ref(false);
const appserviceOptions = ref<AppserviceListItem[]>([]);
const selectedAppserviceIds = ref<number[]>([]);

const includeServer = ref(true);
const includeLoadbalancer = ref(true);
const includeStorage = ref(true);
const includeOpenshift = ref(true);

const exporting = ref(false);
const exportError = ref("");
const exportProgressDone = ref(0);
const exportProgressTotal = ref(0);

const exportProgressPercent = computed(() =>
  exportProgressTotal.value > 0
    ? Math.round((exportProgressDone.value / exportProgressTotal.value) * 100)
    : 0
);

const MAX_BULK_SELECT = 100;
const THROTTLE_APPSERVICE_THRESHOLD = 25;
const THROTTLE_DELAY_MS = 200;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const favoriteOptions = computed(() =>
  appserviceOptions.value.filter((item) => item.isFavorite)
);

const selectAllDisabled = computed(
  () => loadingOptions.value || appserviceOptions.value.length > MAX_BULK_SELECT
);

const selectFavoritesDisabled = computed(
  () => loadingOptions.value || favoriteOptions.value.length > MAX_BULK_SELECT
);

const selectAllTooltip = computed(() =>
  !loadingOptions.value && appserviceOptions.value.length > MAX_BULK_SELECT
    ? `Zu viele Anwendungsservices (>${MAX_BULK_SELECT}) für die Mehrfachauswahl`
    : "Alle auswählen"
);

const selectFavoritesTooltip = computed(() =>
  !loadingOptions.value && favoriteOptions.value.length > MAX_BULK_SELECT
    ? `Zu viele Favoriten (>${MAX_BULK_SELECT}) für die Mehrfachauswahl`
    : "Favoriten auswählen"
);

const canExport = computed(
  () =>
    selectedAppserviceIds.value.length > 0 &&
    (includeServer.value ||
      includeLoadbalancer.value ||
      includeStorage.value ||
      includeOpenshift.value) &&
    !exporting.value
);

async function loadAppserviceOptions() {
  loadingOptions.value = true;
  try {
    const res = await appserviceService.getAppservices(
      ref(false),
      0,
      100000,
      "asc",
      ""
    );
    appserviceOptions.value = res.content.slice().sort((a, b) => {
      const favDiff = (b.isFavorite ? 1 : 0) - (a.isFavorite ? 1 : 0);
      if (favDiff !== 0) return favDiff;
      return a.name.localeCompare(b.name);
    });
  } finally {
    loadingOptions.value = false;
  }
}

function selectAll() {
  selectedAppserviceIds.value = appserviceOptions.value.map((item) => item.id);
}

function selectFavorites() {
  selectedAppserviceIds.value = favoriteOptions.value.map((item) => item.id);
}

function resetForm() {
  selectedAppserviceIds.value = [];
  includeServer.value = true;
  includeLoadbalancer.value = true;
  includeStorage.value = true;
  includeOpenshift.value = true;
  exportError.value = "";
  exportProgressDone.value = 0;
  exportProgressTotal.value = 0;
}

function close() {
  internalValue.value = false;
  resetForm();
  unregisterOpenDialog?.();
}

watch(internalValue, (val) => {
  if (val) {
    registerOpenDialog?.();
    void loadAppserviceOptions();
  }
});

function csvCell(value: unknown): string {
  if (value === null || value === undefined) return "";
  return `"${String(value).replace(/"/g, '""')}"`;
}

function buildTable(
  title: string,
  columns: string[],
  rows: string[][]
): string[] {
  const lines: string[] = [];
  lines.push(csvCell(title));
  lines.push(columns.map(csvCell).join(";"));
  rows.forEach((row) => lines.push(row.map(csvCell).join(";")));
  return lines;
}

function serverKindText(kind?: string | null): string {
  if (!kind) return "";
  switch (String(kind).toUpperCase()) {
    case "HARDWARE":
      return "Hardware Server";
    case "VIRTUAL":
      return "Virtuelle Maschine";
    default:
      return "";
  }
}

function powerStateText(state?: string | null): string {
  if (state === "poweredOn") return "Ein";
  if (state === "poweredOff") return "Aus";
  return "Standby";
}

async function handleExport() {
  if (!canExport.value) return;
  exporting.value = true;
  exportError.value = "";

  try {
    const selectedAppservices = appserviceOptions.value.filter((item) =>
      selectedAppserviceIds.value.includes(item.id)
    );

    const includedCategoryCount = [
      includeServer.value,
      includeLoadbalancer.value,
      includeStorage.value,
      includeOpenshift.value,
    ].filter(Boolean).length;

    exportProgressDone.value = 0;
    exportProgressTotal.value =
      selectedAppservices.length * includedCategoryCount;

    const shouldThrottle =
      selectedAppservices.length > THROTTLE_APPSERVICE_THRESHOLD;

    async function runRequest<T>(fn: () => Promise<T>): Promise<T> {
      try {
        return await fn();
      } finally {
        exportProgressDone.value++;
        if (shouldThrottle) await sleep(THROTTLE_DELAY_MS);
      }
    }

    const serverRows: string[][] = [];
    const loadbalancerRows: string[][] = [];
    const storageRows: string[][] = [];
    const openshiftRows: string[][] = [];

    for (const appservice of selectedAppservices) {
      if (includeServer.value) {
        const detail = await runRequest(() =>
          appserviceService.getAppservice(ref(false), appservice.id)
        );
        (detail.servers ?? []).forEach((server: ServerListExtended) => {
          serverRows.push([
            appservice.name,
            serverKindText(server.serverKind),
            server.name,
            powerStateText(server.powerState),
            server.os ?? "",
            server.appserviceNames ?? "",
            String(server.numCpu ?? ""),
            `${formatter.formatMBtoGB(server.memoryMb ?? 0)} GB`,
            `${formatter.formatBtoGB(server.vdisksCapacityInBytes ?? 0)} GB`,
          ]);
        });
      }

      if (includeLoadbalancer.value) {
        const loadbalancers: LoadbalancerListItem[] = await runRequest(() =>
          loadbalancerService.getLoadbalancersByAppserviceId(
            ref(false),
            appservice.id
          )
        );
        loadbalancers.forEach((lb) => {
          loadbalancerRows.push([
            appservice.name,
            lb.name,
            lb.domain ?? "",
            lb.listen,
            String(lb.port),
          ]);
        });
      }

      if (includeStorage.value) {
        const storages: UnifiedStorageItemList[] = await runRequest(() =>
          storageService.getUnifiedStorageByAppserviceId(
            ref(false),
            appservice.id
          )
        );
        storages.forEach((storage) => {
          storageRows.push([
            appservice.name,
            storage.name,
            storage.type,
            storage.protocol,
          ]);
        });
      }

      if (includeOpenshift.value) {
        const namespaces: OpenshiftNamespaceRef[] = await runRequest(() =>
          openshiftService.getNamespacesByAppserviceId(
            ref(false),
            appservice.id
          )
        );
        namespaces.forEach((ns) => {
          openshiftRows.push([
            appservice.name,
            ns.name,
            formatter.formatOpenshiftClusterEnvironment(
              ns.clusterEnvironment ?? ""
            ),
          ]);
        });
      }
    }

    const sections: string[] = [];

    if (includeServer.value) {
      sections.push(
        ...buildTable(
          "Server",
          [
            "Appservice",
            "Typ",
            "Servername",
            "Status",
            "Betriebssystem",
            "Anwendungsservice",
            "CPUs",
            "RAM",
            "Disks",
          ],
          serverRows
        )
      );
    }

    if (includeLoadbalancer.value) {
      if (sections.length) sections.push("");
      sections.push(
        ...buildTable(
          "Loadbalancer",
          ["Appservice", "Name", "Domain", "Listen", "Port"],
          loadbalancerRows
        )
      );
    }

    if (includeStorage.value) {
      if (sections.length) sections.push("");
      sections.push(
        ...buildTable(
          "Storage",
          ["Appservice", "Name", "Typ", "Protokoll"],
          storageRows
        )
      );
    }

    if (includeOpenshift.value) {
      if (sections.length) sections.push("");
      sections.push(
        ...buildTable(
          "Openshift",
          ["Appservice", "Name", "Cluster"],
          openshiftRows
        )
      );
    }

    const csvContent = sections.join("\n");
    const blob = new Blob(["\uFEFF" + csvContent], {
      type: "text/csv;charset=utf-8;",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    const timestamp = new Date().toISOString().slice(0, 10);
    link.download = `appservice-export_${timestamp}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    close();
  } catch {
    exportError.value = "Export fehlgeschlagen. Bitte versuchen Sie es erneut.";
  } finally {
    exporting.value = false;
  }
}
</script>
