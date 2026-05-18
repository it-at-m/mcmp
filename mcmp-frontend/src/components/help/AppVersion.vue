<template>
  <CommonCard title="Version">
    <v-alert
      v-if="versionError"
      type="error"
      variant="tonal"
      class="mb-4"
    >
      AppVersion konnte nicht geladen werden.
      <div class="text-caption mt-1">{{ versionError }}</div>
    </v-alert>

    <div
      v-else-if="versionLoading"
      class="text-body-2"
    >
      Lädt Versionsinformationen…
    </div>

    <v-data-table
      v-else
      :headers="headers"
      :items="rows"
      class="elevation-1 version-table"
      :items-per-page="-1"
      item-value="key"
      no-data-text="Keine Versionsinformationen gefunden"
      hide-default-footer
      hide-default-header
      density="comfortable"
    >
      <template v-slot:[`item.value`]="{ item, value }">
        <span :class="{ 'version-mono': item.mono }">
          <template
            v-if="item.key === 'gitCommitIdFull' && value && value !== '—'"
          >
            <a
              class="links"
              :href="`https://github.com/it-at-m/mcmp/commit/${encodeURIComponent(String(value))}`"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ value }}
            </a>
          </template>
          <template v-else>
            {{ value ?? "—" }}
          </template>
        </span>
      </template>
    </v-data-table>
  </CommonCard>
  <CommonCard
    title="Verwendete Technologien"
    class="mt-4"
  >
    <v-data-table
      :headers="headers"
      :items="techRows"
      class="elevation-1 version-table"
      :items-per-page="-1"
      item-value="key"
      hide-default-footer
      hide-default-header
      density="comfortable"
    >
      <template v-slot:[`item.value`]="{ value }">
        <a
          class="links d-inline-flex align-center"
          :href="value"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ value }}
          <v-icon
            :icon="mdiOpenInNew"
            size="x-small"
            class="ml-1"
          />
        </a>
      </template>
    </v-data-table>
  </CommonCard>
</template>

<script setup lang="ts">
import type { AppVersion } from "@/types/AppVersion.ts";
import type { DataTableHeader } from "vuetify/framework";

import { mdiOpenInNew } from "@mdi/js";
import { computed, onMounted, ref } from "vue";

import appVersionService from "@/api/appVersionService.ts";
import CommonCard from "@/components/common/CommonCard.vue";

const props = defineProps({
  isAdmin: {
    type: Boolean,
    default: false,
  },
});

type VersionRow = {
  key: string;
  label: string;
  value: string;
  mono?: boolean;
};

const version = ref<AppVersion | null>(null);
const versionLoading = ref(false);
const versionError = ref<string | null>(null);

const headers = ref<DataTableHeader[]>([
  { title: "Feld", key: "label", width: "150px" },
  { title: "Wert", key: "value" },
]);

const rows = computed<VersionRow[]>(() => {
  const v = version.value;

  const allRows: VersionRow[] = [
    { key: "appVersion", label: "App-Version", value: v?.version ?? "—" },
    {
      key: "buildTime",
      label: "Build-Zeit",
      value: formatDateTime(v?.buildTime),
    },
    { key: "javaVersion", label: "Java-Version", value: v?.javaVersion ?? "—" },
    { key: "gitBranch", label: "Git-Branch", value: v?.gitBranch ?? "—" },
    {
      key: "gitCommitId",
      label: "Commit (kurz)",
      value: v?.gitCommitId ?? "—",
    },
    {
      key: "gitCommitIdFull",
      label: "Commit (voll)",
      value: v?.gitCommitIdFull ?? "—",
      mono: true,
    },
    {
      key: "gitCommitTime",
      label: "Commit-Zeit",
      value: formatDateTime(v?.gitCommitTime),
    },
    {
      key: "gitDirty",
      label: "Working Tree",
      value: v?.gitDirty == null ? "—" : v.gitDirty ? "dirty" : "clean",
    },
  ];

  if (props.isAdmin) {
    return allRows;
  }

  const adminOnlyKeys = ["gitCommitIdFull", "buildTime", "gitCommitTime"];
  return allRows.filter((row) => !adminOnlyKeys.includes(row.key));
});

const techRows = ref([
  {
    key: "templates",
    label: "Referenzarchitektur",
    value: "https://github.com/it-at-m/refarch-templates",
  },
]);

function formatDateTime(value: string | number | null | undefined): string {
  if (value == null || value === "") return "—";

  const raw = String(value).trim();
  const unescaped = raw.replace(/\\:/g, ":").replace(/\\/g, "");

  const isDigitsOnly = /^[0-9]+$/.test(unescaped);
  const epochMs = isDigitsOnly
    ? unescaped.length === 10
      ? Number(unescaped) * 1000
      : Number(unescaped)
    : null;

  const d =
    epochMs != null && Number.isFinite(epochMs)
      ? new Date(epochMs)
      : new Date(unescaped);

  if (Number.isNaN(d.getTime())) return raw;

  return new Intl.DateTimeFormat("de-DE", {
    timeZone: "Europe/Berlin",
    dateStyle: "medium",
    timeStyle: "medium",
  }).format(d);
}

async function loadVersion() {
  versionError.value = null;
  try {
    version.value = await appVersionService.getVersion(versionLoading);
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : String(e);
    version.value = null;
  }
}

onMounted(() => {
  loadVersion();
});
</script>
<style scoped>
.version-mono {
  font-family:
    ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono",
    "Courier New", monospace;
  word-break: break-all;
}

/* Weniger Abstand zwischen den Spalten (horizontaler Zell-Innenabstand) */
.version-table :deep(th),
.version-table :deep(td) {
  padding-inline: 8px; /* Standard ist meist deutlich größer */
}

/* Optional: noch enger */
@media (max-width: 600px) {
  .version-table :deep(th),
  .version-table :deep(td) {
    padding-inline: 6px;
  }
}

:deep(a.links),
:deep(a.links:visited),
:deep(a.links:hover),
:deep(a.links:active) {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
