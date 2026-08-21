<template>
  <common-card title="Informationen">
    <v-row>
      <v-col cols="3">
        <h3>Protokoll</h3>
      </v-col>
      <v-col cols="3">
        <h3>Redundanz</h3>
      </v-col>
      <v-col cols="3">
        <h3>Festplattentyp</h3>
      </v-col>
      <v-col
        cols="3"
        class="links"
      >
        <h3>
          Anwendungsservice{{
            props.selectedStorageItem.appservices &&
            props.selectedStorageItem.appservices.length > 1
              ? "s"
              : ""
          }}<info-tooltip>
            <div class="pa-1">
              <strong>MCMP Anwendungservice-Ansicht</strong>
              <p class="text-caption mt-2 mb-1">
                Öffnet die Detailseite des Anwendungsservice direkt hier in der
                MCMP.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ props.selectedStorageItem.protocol }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ getMirrored() }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ getDiskClass() }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div
          v-if="
            props.selectedStorageItem.appservices &&
            props.selectedStorageItem.appservices.length > 1
          "
        >
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="appservice in props.selectedStorageItem.appservices"
              :key="appservice.id"
              class="mb-1"
            >
              <router-link :to="`/appservice/${appservice.id}`">
                {{ appservice.name }}
              </router-link>
            </li>
          </ul>
        </div>
        <div
          v-else-if="
            props.selectedStorageItem.appservices &&
            props.selectedStorageItem.appservices.length === 1
          "
        >
          <router-link :to="`/appservice/${firstAppservice?.id}`">
            {{ firstAppservice?.name }}
          </router-link>
        </div>
        <p v-else>-</p>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="3">
        <h3 v-if="selectedStorageItem.protocol != 'S3'">
          WORM<info-tooltip>
            <div class="pa-1">
              <strong>Write Once Read Many</strong>
              <p class="text-caption mt-2 mb-1">
                Schützt eine erstellte und committete Datei vor Änderungen und
                Löschung. Der Schutz ist zeitlich befristet. Das Lesen der
                geschützten Datei, sowie das Erstellen und committen weitere
                Dateien ist beliebig möglich.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3 v-if="selectedStorageItem.protocol != 'S3'">
          Clone<info-tooltip>
            <div class="pa-1">
              <strong>Clone</strong>
              <p class="text-caption mt-2 mb-1">
                Eine effiziente Kopie eines Speicherbereichs, die Speicherplatz
                spart durch Copy-on-Write. Änderungen beeinflussen nicht das
                Original.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>Typ</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="3">
        <p v-if="selectedStorageItem.protocol != 'S3'">
          {{ formatter.formatBooleanToJaNein(selectedStorageItem.isWorm) }}
        </p>
      </v-col>
      <v-col cols="3">
        <p v-if="selectedStorageItem.protocol != 'S3'">
          {{ formatter.formatBooleanToJaNein(selectedStorageItem.isFlexClone) }}
        </p>
      </v-col>
      <v-col cols="3">
        <p>
          {{ formatStorageCategory(selectedStorageItem.storageCategory) }}
        </p>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    v-if="props.selectedStorageItem.isWorm"
    title="WORM"
    top-margin="0"
  >
    <v-row>
      <v-col cols="2">
        <h3>Minimum Retention</h3>
      </v-col>
      <v-col cols="2">
        <h3>Default Retention</h3>
      </v-col>
      <v-col cols="2">
        <h3>Maximum Retention</h3>
      </v-col>
      <v-col cols="2">
        <h3>Autocommit-Period</h3>
      </v-col>
      <v-col cols="2">
        <h3>Append Mode</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="2">
        <p>
          {{ turnToDate(selectedStorageItem.minRetention) }}
        </p>
      </v-col>
      <v-col cols="2">
        <p>
          {{ turnToDate(selectedStorageItem.defaultRetention) }}
        </p>
      </v-col>
      <v-col cols="2">
        <p>
          {{ turnToDate(selectedStorageItem.maxRetention) }}
        </p>
      </v-col>
      <v-col cols="2">
        <p>
          {{ turnToDate(selectedStorageItem.autocommitPeriod) }}
        </p>
      </v-col>
      <v-col cols="2">
        <p>
          {{ formatter.formatBooleanToJaNein(selectedStorageItem.appendMode) }}
        </p>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    v-if="props.selectedStorageItem.isFlexClone"
    title="Clone"
    top-margin="0"
  >
    <v-row>
      <v-col cols="3">
        <h3>Basisvolume</h3>
      </v-col>
      <v-col cols="3">
        <h3>Basissnapshot</h3>
      </v-col>
      <v-col cols="6" />
    </v-row>
    <v-row>
      <v-col cols="3">
        <p>
          {{ props.selectedStorageItem.parentVolumeName }}
        </p>
      </v-col>
      <v-col cols="3">
        <p>
          {{ props.selectedStorageItem.parentSnapshotName }}
        </p>
      </v-col>
      <v-col cols="6" />
    </v-row>
  </common-card>
  <common-card
    title="Ressourcen"
    top-margin="0"
  >
    <template #toolbar-actions>
      <storage-change-share
        :selected-storage-item="selectedStorageItem"
        @save="startChangeShareJob"
      />
    </template>
    <v-row>
      <v-col cols="3">
        <h3>
          Gesamtgröße<info-tooltip v-if="selectedStorageItem.protocol === 'S3'">
            <div class="pa-1">
              <strong>Tenant-Gesamtgröße</strong>
              <p class="text-caption mt-2 mb-1">
                Diese Größe ist nicht die Größe dieses einzelnen Buckets,
                sondern die Gesamtkapazität des gesamten S3-Tenants da es
                zurzeit keine Daten über die einzelnen Bucket größen gibt..
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col
        v-if="
          (selectedStorageItem.type == 'NFS' ||
            selectedStorageItem.type == 'CIFS') &&
          !selectedStorageItem.isWorm
        "
        cols="3"
      >
        <h3>Snapshotanteil</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="3">
        <p>
          {{ formatter.formatBytesSmart(selectedStorageItem.size) }}
        </p>
      </v-col>
      <v-col
        v-if="
          (selectedStorageItem.type == 'NFS' ||
            selectedStorageItem.type == 'CIFS') &&
          !selectedStorageItem.isWorm
        "
        cols="3"
      >
        <p>
          {{
            selectedStorageItem.spaceSnapshotReservePercent
              ? selectedStorageItem.spaceSnapshotReservePercent
              : "0"
          }}
          %
        </p>
      </v-col>
    </v-row>
    <v-row>
      <!-- vue echarts pie chart -->
      <v-col cols="12">
        <storage-charts :selected-storage-item="selectedStorageItem" />
      </v-col>
    </v-row>
  </common-card>
  <common-card
    title="CMDB"
    top-margin="0"
    :is-default-expanded="false"
  >
    <v-row>
      <v-col cols="6">
        <h3>Storage CI</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="6"
        class="pt-0 links"
      >
        <a
          v-if="selectedStorageItem.snowSysId"
          :href="`https://it-services.muenchen.de/now/sgw/record/${selectedStorageItem.snowSysClass}/${selectedStorageItem.snowSysId}/`"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Storage CI in ServiceNow öffnen"
        >
          {{ selectedStorageItem.snowName }}
        </a>
        <p v-else>-</p>
      </v-col>
    </v-row>
  </common-card>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";

import { computed, ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import StorageChangeShare from "@/components/Storage/StorageChangeShare.vue";
import StorageCharts from "@/components/Storage/StorageCharts.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
}>();

const firstAppservice = computed(
  () => props.selectedStorageItem.appservices?.[0] ?? null
);

const emit = defineEmits<(e: "changed") => void>();

const formatter = useFormatter();
const loading = ref(false);

function startChangeShareJob(payload: {
  sizeGb: number;
  snapshotReservePercent: number;
}) {
  jobService
    .startJob(
      loading,
      props.selectedStorageItem.type == "NFS"
        ? "STORAGE_MODIFY_NFS"
        : "STORAGE_MODIFY_CIFS",
      -1,
      {
        uuid: props.selectedStorageItem.uuid,
        new_size: payload.sizeGb,
        new_snapshot_percent: payload.snapshotReservePercent,
      }
    )
    .then(() => {
      emit("changed");
    });
}

function getMirrored(): string {
  if (props.selectedStorageItem.protocol === "S3") {
    return "gespiegelt in 2 Rechenzentren";
  }
  if (props.selectedStorageItem.mirrorEnabled) {
    return "gespiegelt in 2 Rechenzentren";
  } else {
    return "lokal";
  }
}

function getDiskClass(): string {
  if (props.selectedStorageItem.protocol === "S3") {
    return "HDD";
  }
  if (props.selectedStorageItem.diskClass === "capacity") {
    return "HDD";
  } else if (props.selectedStorageItem.diskClass === "solid_state") {
    return "SSD";
  } else {
    return "-";
  }
}

function formatStorageCategory(category: string | undefined): string {
  if (!category) return "-";
  return category
    .split("_")
    .map((word) =>
      word.length <= 4
        ? word.toUpperCase()
        : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    )
    .join(" ");
}

function turnToDate(isoString: string | undefined): string {
  // Zeitangeben vom ISO8601 in normales Format umwandeln: PT0S - PT65535S fuer Sekunden, PT0M - PT60M fuer Minuten, PT0H - PT24H fuer Stunden, P0D - P36500D fuer Tage, P0M - P1200M fuer Monate oder P0Y - P100Y für Jahre; max = maximal; min = minimal; none = keine
  if (!isoString) {
    return "-";
  }
  if (isoString === "max") {
    return "maximal";
  }
  if (isoString === "none") {
    return "keine";
  }
  const regex =
    /P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/;
  const matches = isoString.match(regex);
  if (!matches) {
    return isoString;
  }
  const years = parseInt(matches[1] || "0", 10);
  const months = parseInt(matches[2] || "0", 10);
  const days = parseInt(matches[3] || "0", 10);
  const hours = parseInt(matches[4] || "0", 10);
  const minutes = parseInt(matches[5] || "0", 10);
  const seconds = parseInt(matches[6] || "0", 10);
  let result = "";
  if (years > 0) {
    result += `${years} Jahr${years > 1 ? "e" : ""} `;
  }
  if (months > 0) {
    result += `${months} Monat${months > 1 ? "e" : ""} `;
  }
  if (days > 0) {
    result += `${days} Tag${days > 1 ? "e" : ""} `;
  }
  if (hours > 0) {
    result += `${hours} Stunde${hours > 1 ? "n" : ""} `;
  }
  if (minutes > 0) {
    result += `${minutes} Minute${minutes > 1 ? "n" : ""} `;
  }
  if (seconds > 0) {
    result += `${seconds} Sekunde${seconds > 1 ? "n" : ""} `;
  }
  return result.trim() || "0 Sekunden";
}
</script>
