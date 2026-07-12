<!-- eslint-disable @intlify/vue-i18n/no-raw-text -->
<template>
  <common-card title="Informationen">
    <v-row>
      <v-col cols="3">
        <h3>Betriebssystem</h3>
      </v-col>
      <v-col
        v-if="props.selectedServer.powerState === 'poweredOn'"
        cols="3"
      >
        <h3>Letzter Reboot</h3>
      </v-col>
      <v-col
        v-if="props.selectedServer.powerState === 'poweredOff'"
        cols="3"
      >
        <h3>Abschaltgrund</h3>
      </v-col>
      <v-col cols="3">
        <h3>FQDN<info-tooltip text="Fully Qualified Domain Name" /></h3>
      </v-col>
      <v-col cols="3">
        <h3>
          MCMP-Anwendungsservice{{
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length > 1
              ? "s"
              : ""
          }}<info-tooltip>
            <div class="pa-1">
              <strong>MCMP Anwendungsservice-Ansicht</strong>
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
          {{ formatter.ifEmptyReturnDash(props.selectedServer.os) }}
        </p>
      </v-col>
      <v-col
        v-if="props.selectedServer.powerState === 'poweredOn'"
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.ifEmptyReturnDash(
              formatter.formatToGermanLocalTime(props.selectedServer.bootTime)
            )
          }}
        </p>
      </v-col>
      <v-col
        v-if="props.selectedServer.powerState === 'poweredOff'"
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.ifEmptyReturnDash(
              props.selectedServer.serverCustomAttributes?.["A_turned_off_note"]
            )
          }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ formatter.ifEmptyReturnDash(props.selectedServer.fqdn) }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div
          v-if="
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length > 1
          "
        >
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="appservice in props.selectedServer.appservices"
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
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length === 1
          "
        >
          <router-link :to="`/appservice/${firstAppservice?.id}`">
            {{ firstAppservice?.name }}
          </router-link>
        </div>
        <p v-else>-</p>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    title="Ressourcen"
    top-margin="0"
  >
    <template #toolbar-actions>
      <edit-resources
        v-if="
          selectedServer.canEdit && selectedServer.cloud?.cloudType == 'VCENTER'
        "
        :server="props.selectedServer"
        :rightsize="false"
        @save="
          (cpus, ram, scheduleTime, schedulePatchnight) =>
            change_cpu_ram(cpus, ram, scheduleTime, schedulePatchnight)
        "
      />
    </template>
    <v-row>
      <v-col cols="3">
        <h3>
          <v-tooltip
            v-if="
              !selectedServer.numCpuRecommended ||
              selectedServer.numCpuRecommended == 0 ||
              isCpuInCooldown
            "
            location="bottom"
            :text="
              isCpuInCooldown &&
              selectedServer.numCpuRecommended &&
              selectedServer.numCpuRecommended != 0
                ? `Empfehlung pausiert – Neue Empfehlung verfügbar ab ${cpuCooldownUntil}`
                : 'Keine Empfehlung möglich'
            "
          >
            <template #activator="{ props: statusProps }">
              <v-icon
                v-bind="statusProps"
                :icon="
                  isCpuInCooldown &&
                  selectedServer.numCpuRecommended &&
                  selectedServer.numCpuRecommended != 0
                    ? mdiPauseCircle
                    : mdiHelpCircle
                "
                color="grey"
                size="small"
              />
            </template>
          </v-tooltip>
          <v-icon
            v-else-if="
              selectedServer.numCpuRecommended === selectedServer.numCpu
            "
            :icon="mdiCheckCircle"
            color="_green"
            size="small"
          />
          <v-tooltip
            v-else-if="
              selectedServer.numCpuRecommended != selectedServer.numCpu
            "
            location="bottom"
            :text="'Empfehlung: ' + selectedServer.numCpuRecommended + ' CPUs'"
          >
            <template #activator="{ props: statusProps }">
              <v-icon
                v-bind="statusProps"
                :icon="mdiAlertCircle"
                color="orange"
                size="small"
              />
            </template>
          </v-tooltip>

          CPU<info-tooltip text="Central Processing Unit" />
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>
          <v-tooltip
            v-if="
              !selectedServer.memoryMbRecommended ||
              selectedServer.memoryMbRecommended == 0 ||
              isMemoryInCooldown
            "
            location="bottom"
            :text="
              isMemoryInCooldown &&
              selectedServer.memoryMbRecommended &&
              selectedServer.memoryMbRecommended != 0
                ? `Empfehlung pausiert – Neue Empfehlung verfügbar ab ${memoryCooldownUntil}`
                : 'Keine Empfehlung möglich'
            "
          >
            <template #activator="{ props: statusProps }">
              <v-icon
                v-bind="statusProps"
                :icon="
                  isMemoryInCooldown &&
                  selectedServer.memoryMbRecommended &&
                  selectedServer.memoryMbRecommended != 0
                    ? mdiPauseCircle
                    : mdiHelpCircle
                "
                color="grey"
                size="small"
              />
            </template>
          </v-tooltip>
          <v-icon
            v-else-if="
              selectedServer.memoryMbRecommended === selectedServer.memoryMb
            "
            :icon="mdiCheckCircle"
            color="_green"
            size="small"
          />
          <v-tooltip
            v-else-if="
              selectedServer.memoryMbRecommended != selectedServer.memoryMb
            "
            location="bottom"
            :text="
              'Empfehlung: ' +
              formatter.formatMBtoGB(selectedServer.memoryMbRecommended) +
              ' GB'
            "
          >
            <template #activator="{ props: statusProps }">
              <v-icon
                v-bind="statusProps"
                :icon="mdiAlertCircle"
                color="orange"
                size="small"
              />
            </template>
          </v-tooltip>
          Arbeitsspeicher
        </h3>
      </v-col>
      <v-col
        v-if="props.selectedServer.serverType === 'VM_VCENTER'"
        cols="3"
      >
        <h3>Festplattengröße</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <linear-progress-with-colors
          v-if="props.selectedServer.cpuUtil != null"
          :value="props.selectedServer.cpuUtil ?? 0"
          :show-percentage="true"
          :title="`CPU Auslastung bei ${props.selectedServer.cpuUtil ?? 0}%`"
          :tooltip-text="'Quelle: Checkmk, Abfrageintervall: 1x pro Minute'"
        />
        <p v-else>Checkmk CPU-Metriken nicht verfügbar</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <linear-progress-with-colors
          v-if="props.selectedServer.memUsedPercent != null"
          :value="props.selectedServer.memUsedPercent ?? 0"
          :show-percentage="true"
          :title="`Arbeitsspeicher Auslastung bei ${props.selectedServer.memUsedPercent ?? 0}%`"
          :tooltip-text="'Quelle: Checkmk, Abfrageintervall: 1x pro Minute'"
        />
        <p v-else>Checkmk RAM-Metriken nicht verfügbar</p>
      </v-col>
      <v-col
        v-if="props.selectedServer.serverType === 'VM_VCENTER'"
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatBtoGB(props.selectedServer.vdisksCapacityInBytes)
          }}
          GB
        </p>
      </v-col>
      <v-col cols="3"> </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p v-if="props.selectedServer.serverType === 'VM_VCENTER'">
          {{ props.selectedServer.numCpu }} CPUs
        </p>
        <p
          v-else-if="
            ['CISCO_RACK_UNIT', 'CISCO_BLADE', 'VM_OLVM'].includes(
              props.selectedServer.serverType
            )
          "
        >
          {{ props.selectedServer.numCpu }} CPUs
          <span v-if="props.selectedServer.numCoresPerSocket > 1">
            ({{ props.selectedServer.numCoresPerSocket }} Kerne je CPU)
          </span>
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p v-if="props.selectedServer.serverType === 'VM_VCENTER'">
          {{ formatter.formatMBtoGB(props.selectedServer.memoryMb) }} GB
        </p>
        <p
          v-else-if="
            ['CISCO_RACK_UNIT', 'CISCO_BLADE'].includes(
              props.selectedServer.serverType
            )
          "
        >
          {{ formatter.formatMBtoGB(props.selectedServer.memoryMb) }} GB
          <span
            v-if="
              props.selectedServer.memoryMbAvailable !==
              props.selectedServer.memoryMb
            "
          >
            (aktiv:
            {{ formatter.formatMBtoGB(props.selectedServer.memoryMbAvailable) }}
            GB)
          </span>
        </p>
        <p v-else-if="props.selectedServer.serverType === 'VM_OLVM'">
          {{ formatter.formatMBtoGB(props.selectedServer.memoryMb) }} GB
        </p>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        v-if="
          !isCpuInCooldown &&
          props.selectedServer.numCpuRecommended &&
          props.selectedServer.numCpuRecommended != 0 &&
          selectedServer.numCpuRecommended != selectedServer.numCpu
        "
        cols="3"
        class="pt-0 d-flex align-center"
      >
        <edit-resources
          v-if="
            selectedServer.canEdit &&
            selectedServer.cloud?.cloudType == 'VCENTER'
          "
          :server="props.selectedServer"
          :rightsize="true"
          @save="
            (cpus, ram, scheduleTime, schedulePatchnight) =>
              change_cpu_ram(cpus, ram, scheduleTime, schedulePatchnight)
          "
        />
        <h4>&nbsp;{{ EMPFEHLUNG }}&nbsp;</h4>
        {{ props.selectedServer.numCpuRecommended }} CPUs
      </v-col>
      <v-col
        v-else
        cols="3"
        class="pt-0"
      ></v-col>
      <v-col
        v-if="
          !isMemoryInCooldown &&
          selectedServer.memoryMbRecommended &&
          selectedServer.memoryMbRecommended != 0 &&
          selectedServer.memoryMbRecommended != selectedServer.memoryMb
        "
        cols="3"
        class="pt-0 d-flex align-center"
      >
        <edit-resources
          v-if="
            selectedServer.canEdit &&
            selectedServer.cloud?.cloudType == 'VCENTER'
          "
          :server="props.selectedServer"
          :rightsize="true"
          @save="
            (cpus, ram, scheduleTime, schedulePatchnight) =>
              change_cpu_ram(cpus, ram, scheduleTime, schedulePatchnight)
          "
        />
        <h4>&nbsp;{{ EMPFEHLUNG }}&nbsp;</h4>
        {{ formatter.formatMBtoGB(props.selectedServer.memoryMbRecommended) }}
        GB
      </v-col>
    </v-row>
  </common-card>
  <common-card
    title="CMDB"
    top-margin="0"
  >
    <v-row>
      <v-col
        v-if="selectedServer.cloud?.cloudType == 'VCENTER'"
        cols="3"
      >
        <h3>
          VMware Instanz<info-tooltip>
            <div class="pa-1">
              <strong>Virtuelle Infrastruktur (vCenter-Sicht)</strong>
              <p class="text-caption mt-2 mb-1">
                Dieses Objekt repräsentiert die "Hülle" der virtuellen Maschine.
              </p>
              <ul
                class="mt-2 text-body-2"
                style="padding-left: 1.2rem"
              >
                <li>
                  <strong>Ressourcen:</strong> Definition von vCPU, RAM und
                  Festplatten-Limitierung.
                </li>
                <li>
                  <strong>Hosting:</strong> Verknüpfung zum physischen ESXi-Host
                  und Datastore.
                </li>
                <li>
                  <strong>Management:</strong> Basis für Hardware-Änderungen
                  (Resize) und Snapshots.
                </li>
              </ul>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Server Instanz<info-tooltip>
            <div class="pa-1">
              <strong>Betriebssystem & Laufzeit (OS-Sicht)</strong>
              <p class="text-caption mt-2 mb-1">
                Dieses Objekt repräsentiert das installierte System "innerhalb"
                der VM.
              </p>
              <ul
                class="mt-2 text-body-2"
                style="padding-left: 1.2rem"
              >
                <li>
                  <strong>Identität:</strong> FQDN, Betriebssystem-Version
                  (Kernel/Patchlevel).
                </li>
                <li>
                  <strong>Inhalt:</strong> Installierte Software, aktive
                  Prozesse und gemountete Dateisysteme.
                </li>
                <li>
                  <strong>Betrieb:</strong> Bezugspunkt für Monitoring-Alarme
                  und das Patch-Management.
                </li>
              </ul>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Anwendungsservice{{
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length > 1
              ? "s"
              : ""
          }}<info-tooltip>
            <div class="pa-1">
              <strong
                >Logische Geschäftsanwendung (Anwendungsservice-Sicht)</strong
              >
              <p class="text-caption mt-2 mb-1">
                Repräsentiert die Gruppierung aller CIs, die für einen Dienst
                zusammenarbeiten.
              </p>
              <ul
                class="mt-2 text-body-2"
                style="padding-left: 1.2rem"
              >
                <li>
                  <strong>Zweck:</strong> Verknüpft die IT-Infrastruktur
                  (Server, DBs) mit einem konkreten Business-Nutzen.
                </li>
                <li>
                  <strong>Impact:</strong> Hilft bei der Analyse, welche Dienste
                  bei einem Serverausfall betroffen sind.
                </li>
                <li>
                  <strong>Eigentumsverantwortung:</strong> Hier sind
                  Verantwortlichkeiten (Owner), Support-Gruppen und SLAs
                  hinterlegt.
                </li>
              </ul>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Anwendungsservice Map{{
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length > 1
              ? "s"
              : ""
          }}<info-tooltip>
            <div class="pa-1">
              <strong>Service-Abhängigkeiten (Top-Down Map)</strong>
              <p class="text-caption mt-2 mb-1">
                Visualisiert die hierarchische Struktur und Beziehungen des
                Dienstes.
              </p>
              <ul
                class="mt-2 text-body-2"
                style="padding-left: 1.2rem"
              >
                <li>
                  <strong>Visualisierung:</strong> Grafische Darstellung aller
                  Komponenten (Server, DBs, LB), die den Service bilden.
                </li>
                <li>
                  <strong>Beziehungen:</strong> Zeigt direkt, welche
                  Infrastruktur-CIs miteinander kommunizieren oder voneinander
                  abhängen.
                </li>
                <li>
                  <strong>Fehlersuche:</strong> Ideal zur Identifikation von
                  "Single Points of Failure" innerhalb der Service-Architektur.
                </li>
              </ul>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        v-if="selectedServer.cloud?.cloudType == 'VCENTER'"
        cols="3"
        class="pt-0 links"
      >
        <p v-if="props.selectedServer.snowInstanceSysId">
          <a
            :href="`https://it-services.muenchen.de/now/sgw/record/${props.selectedServer.snowInstanceSysClass}/${props.selectedServer.snowInstanceSysId}/`"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="VMware Instanz in ServiceNow öffnen"
          >
            {{ props.selectedServer.snowInstanceName }}
          </a>
        </p>
        <p v-else>-</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <p v-if="props.selectedServer.snowServerSysId">
          <a
            :href="`https://it-services.muenchen.de/now/sgw/record/${props.selectedServer.snowServerSysClass}/${props.selectedServer.snowServerSysId}/`"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Server Instanz in ServiceNow öffnen"
          >
            {{ props.selectedServer.snowServerName }}
          </a>
        </p>
        <p v-else>-</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div
          v-if="
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length > 1
          "
        >
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="appservice in props.selectedServer.appservices"
              :key="appservice.sysId"
              class="mb-1"
            >
              <a
                :href="`https://it-services.muenchen.de/nav_to.do?uri=cmdb_ci_service_discovered.do?sys_id=${appservice.sysId}%26sysparm_view=EAM`"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Anwendungsservice in ServiceNow öffnen"
              >
                {{ appservice.name }}
              </a>
            </li>
          </ul>
        </div>
        <div
          v-else-if="
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length === 1
          "
        >
          <a
            :href="`https://it-services.muenchen.de/nav_to.do?uri=cmdb_ci_service_discovered.do?sys_id=${firstAppservice?.sysId}%26sysparm_view=EAM`"
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Anwendungsservice Map in ServiceNow öffnen"
          >
            {{ firstAppservice?.name }}
          </a>
        </div>
        <p v-else>-</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div
          v-if="
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length > 1
          "
        >
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="appservice in props.selectedServer.appservices"
              :key="appservice.sysId"
              class="mb-1"
            >
              <a
                :href="`https://it-services.muenchen.de/now/sgw/record/cmdb_ci_service/${appservice.sysId}/sub/unifiedmap/params/root-node/${appservice.sysId}/`"
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Appservice MAP in ServiceNow öffnen"
              >
                {{ appservice.name }}
              </a>
            </li>
          </ul>
        </div>
        <div
          v-else-if="
            props.selectedServer.appservices &&
            props.selectedServer.appservices.length === 1
          "
        >
          <a
            :href="`https://it-services.muenchen.de/now/sgw/record/cmdb_ci_service/${firstAppservice?.sysId}/sub/unifiedmap/params/root-node/${firstAppservice?.sysId}/`"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ firstAppservice?.name }}
          </a>
        </div>
        <p v-else>-</p>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type Price from "@/types/Price";

import {
  mdiAlertCircle,
  mdiCheckCircle,
  mdiHelpCircle,
  mdiPauseCircle,
} from "@mdi/js";
import { computed, onMounted, ref } from "vue";

import jobService from "@/api/jobService.ts";
import priceService from "@/api/priceService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import LinearProgressWithColors from "@/components/common/LinearProgressWithColors.vue";
import EditResources from "@/components/Server/EditResources.vue";
import { useFormatter } from "@/composables/formatter.js";
import { STATUS_INDICATORS } from "@/constants.ts";
import { useSnackbarStore } from "@/stores/snackbar.ts";
import Server from "@/types/Server";

const loading = ref(true);
const prices = ref<Price[]>([]);
const CPU_PRICE_PER_CORE = ref(0);
const MEMORY_PRICE_PER_MB = ref(0);
const DISK_PRICE_PER_GB = ref(0);
const EMPFEHLUNG = "Empfehlung:";

const props = defineProps<{
  selectedServer: Server;
}>();

const firstAppservice = computed(
  () => props.selectedServer.appservices?.[0] ?? null
);

function getPrices() {
  priceService.getPrices(loading).then((response) => {
    prices.value = response;
    CPU_PRICE_PER_CORE.value =
      prices.value.find((price) => price.name === "CPU_PRICE_PER_CORE")
        ?.pricePerUnit || 0;
    MEMORY_PRICE_PER_MB.value =
      prices.value.find((price) => price.name === "MEMORY_PRICE_PER_MB")
        ?.pricePerUnit || 0;
    DISK_PRICE_PER_GB.value =
      prices.value.find((price) => price.name === "DISK_PRICE_PER_GB")
        ?.pricePerUnit || 0;
    if (
      !CPU_PRICE_PER_CORE.value ||
      !MEMORY_PRICE_PER_MB.value ||
      !DISK_PRICE_PER_GB.value
    ) {
      useSnackbarStore().showMessage({
        message:
          "Preise für CPU, Arbeitsspeicher oder Festplattenspeicher nicht gefunden. Bitte kontaktiere einen Admin.",
        level: STATUS_INDICATORS.ERROR,
      });
    }
  });
}

onMounted(() => {
  getPrices();
});

const formatter = useFormatter();

function change_cpu_ram(
  cpus: number,
  ram: number,
  scheduleTime: string | null,
  schedulePatchnight: boolean
) {
  jobService
    .startJob(loading, "VMWARE_CHANGE_CPU_RAM", props.selectedServer.id, {
      cpu: cpus,
      ram: ram,
      scheduleTime: scheduleTime != null ? scheduleTime : undefined,
      schedulePatchnight: schedulePatchnight,
    })
}

const isCpuInCooldown = computed(() => {
  if (!props.selectedServer.numCpuChangeDate) return false;

  const changeDate = new Date(props.selectedServer.numCpuChangeDate);
  const cooldownUntil = new Date(changeDate);
  cooldownUntil.setDate(cooldownUntil.getDate() + 7);

  return new Date() < cooldownUntil;
});

const cpuCooldownUntil = computed(() => {
  if (!props.selectedServer.numCpuChangeDate) return "";

  const changeDate = new Date(props.selectedServer.numCpuChangeDate);
  const cooldownUntil = new Date(changeDate);
  cooldownUntil.setDate(cooldownUntil.getDate() + 7);

  return cooldownUntil.toLocaleDateString("de-DE");
});

const isMemoryInCooldown = computed(() => {
  if (!props.selectedServer.memoryMbChangeDate) return false;

  const changeDate = new Date(props.selectedServer.memoryMbChangeDate);
  const endsAt = new Date(changeDate);
  endsAt.setDate(endsAt.getDate() + 7);

  return new Date() < endsAt;
});

const memoryCooldownUntil = computed(() => {
  if (!props.selectedServer.memoryMbChangeDate) return "";

  const changeDate = new Date(props.selectedServer.memoryMbChangeDate);
  const endsAt = new Date(changeDate);
  endsAt.setDate(endsAt.getDate() + 7);

  return endsAt.toLocaleDateString("de-DE");
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
