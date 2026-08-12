<template>
  <common-dialog
    v-model="dialog"
    :title="
      rightsize
        ? 'Ressourcenanpassungsempfehlung umsetzen'
        : 'Ressourcen bearbeiten'
    "
    max-width="1000"
    :icon="rightsize ? mdiAlertCircleCheck : mdiPencil"
    show-actions
    :submit-activated="validated"
    show-change-warning
    :check-for-enabled-actions="[server.cloud.cloudType + '_CHANGE_CPU_RAM']"
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <template #activator="{ props: iconProps }">
      <v-tooltip
        :text="rightsize ? 'Empfehlung umsetzen' : 'Ressourcen bearbeiten'"
        location="bottom"
      >
        <template #activator="{ props: tooltipProps }">
          <v-btn
            v-bind="{ ...iconProps, ...tooltipProps }"
            icon
            variant="flat"
            :aria-label="
              rightsize
                ? 'Anpassungsempfehlung umsetzen'
                : 'Ressourcen bearbeiten'
            "
            :color="rightsize ? '_green' : ''"
            :size="rightsize ? 'x-small' : ''"
            ><v-icon>{{ rightsize ? mdiAlertCircleCheck : mdiPencil }} </v-icon>
          </v-btn>
        </template>
      </v-tooltip>
    </template>

    <v-form ref="form">
      <v-row class="mb-1">
        <v-col
          v-if="
            server.cpuAllocationExpandableReservation || server.memoryAllocationExpandableReservation
          "
          cols="12"
        >
          <common-alert color="notice_red">
            <h4>Hinweis:</h4>
            Bei diesem Server liegt eine Reservierung des Arbeitsspeichers oder der CPU vor. Diese wird automatisch auf den neu bestellten Wert geändert. Bei Fragen wenden Sie sich an IBS41.
          </common-alert>
        </v-col>
        <v-col
          v-if="
            server.dbPostgres &&
            ram != formatter.calculateMBtoGB(server.memoryMb)
          "
          cols="12"
        >
          <common-alert color="notice_red">
            <h4>Hinweis:</h4>
            Die Speicherparameter der DB werden erst in der darauffolgenden
            Nacht angepasst. Hierfür ist ein neustart der DB notwendig.
          </common-alert>
        </v-col>
        <v-col
          v-if="!isNonOracleUser && (server.dbAdabas || server.dbMssql)"
          cols="12"
        >
          <common-alert color="notice_red">
            <h4>Hinweis:</h4>
            Bei Adabas und MSSQL Servern ist eine automatisierte Anpassung der
            CPUs aus lizenztechnischen Gründen nicht möglich. Bei Rückfragen
            wenden Sie sich bitte an IBS46.
          </common-alert>
        </v-col>
        <v-col
          v-if="
            ram < formatter.calculateMBtoGB(server.memoryMb) ||
            ram > formatter.calculateMBtoGB(server.hotPlugMemoryLimit) ||
            (ram > formatter.calculateMBtoGB(server.memoryMb) &&
              !server.memoryHotAddEnabled) ||
            cpus < server.numCpu ||
            (cpus > server.numCpu && !server.cpuHotAddEnabled)
          "
          cols="12"
        >
          <common-alert color="notice_red">
            <h4>Hinweis:</h4>
            Bei der Anpassung der Ressourcen kommt es zu einer
            Serviceunterbrechung.

            <!--- v-if="(ram > formatter.calculateMBtoGB(server.memoryMb) || cpus > server.numCpu) && (server.memoryHotAddEnabled && server.cpuHotAddEnabled) ||
            (ram > formatter.calculateMBtoGB(server.memoryMb) && cpus == server.numCpu) && (server.memoryHotAddEnabled) ||
            (ram == formatter.calculateMBtoGB(server.memoryMb) && cpus > server.numCpu) && (server.cpuHotAddEnabled)
            ">Bei der Anpassung der Ressourcen ist keine Downtime notwendig. --->
          </common-alert>
          <v-col cols="12"></v-col>
        </v-col>
        <v-col
          v-if="!rightsize && (
            (ram >= 100 &&
              (formatter.calculateMBtoGB(server.memoryMb) < 100 ||
                ram > formatter.calculateMBtoGB(server.memoryMb))) ||
            cpus >= 72 && (server.numCpu < 72 || cpus > server.numCpu))
          "
          cols="12"
        >
          <common-alert color="notice_red">
            <div class="links">
              <h4>Hinweis:</h4>
              Zur Ressourcenerweiterung über die maximalen Werte von 72 CPUs
              und/oder 100 GB RAM bitte
              <a
                href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=f2385ce61b76a050e52dfddacd4bcb3e"
                target="_blank"
                rel="noopener noreferrer"
              >
                Ticket
              </a>
              an IBS48 Linux-Server oder IBS49 Windows-Server
            </div>
          </common-alert>
        </v-col>
        <v-col cols="6">
          <linear-progress-with-colors
            v-if="server.cpuUtil != null"
            :value="
              server.numCpu != undefined
                ? (server.numCpu / cpus) * (server.cpuUtil ?? 0)
                : 0
            "
            :show-percentage="true"
          />
        </v-col>
        <v-col cols="6">
          <linear-progress-with-colors
            v-if="server.memUsedPercent != null"
            :value="
              server.memoryMb != undefined
                ? (formatter.calculateMBtoGB(server.memoryMb) / ram) *
                  (server.memUsedPercent ?? 0)
                : 0
            "
            :show-percentage="true"
          />
        </v-col>
        <v-col cols="6">
          <v-slider
            v-model="cpus"
            label="CPU"
            :min="1"
            :max="server.numCpu > 72 ? server.numCpu : 72"
            step="1"
            :disabled="!isNonOracleUser && (server.dbAdabas || server.dbMssql)"
          />
        </v-col>
        <v-col cols="6">
          <v-slider
            v-model="ram"
            label="RAM (GB)"
            :min="2"
            :max="currentRam > 100 ? currentRam : 100"
            step="1"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="cpus"
            label="Anzahl CPUs"
            type="number"
            :min="1"
            :max="server.numCpu > 72 ? server.numCpu : 72"
            step="1"
            :rules="[
              (v) => v >= 1 || 'CPU darf nicht kleiner 1 sein.',
              (v) =>
                v <= (server.numCpu > 72 ? server.numCpu : 72) ||
                'CPU darf nicht größer ' +
                  (server.numCpu > 72 ? server.numCpu : 72) +
                  ' sein.',
            ]"
            :disabled="!isNonOracleUser && (server.dbAdabas || server.dbMssql)"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="ram"
            label="Arbeitsspeicher (GB)"
            type="number"
            :min="2"
            :max="currentRam > 100 ? currentRam : 100"
            :rules="[
              (v) => v >= 2 || 'RAM darf nicht kleiner 2 sein.',
              (v) =>
                v <= (currentRam > 100 ? currentRam : 100) ||
                'RAM darf nicht größer ' +
                  (currentRam > 100 ? currentRam : 100) +
                  ' sein.',
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-checkbox
            v-model="schedule"
            label="Durchführungszeitpunkt anpassen"
            :disabled="schedulePatchnight"
            @change="changeToSchedule"
          />
          <common-time-picker
            v-if="schedule"
            v-model:raw-date-in="rawDate"
            lable-text="Durchführungs"
            :time-rules="[
              validationRules.notEmptyRule(
                'Endzeitpunkt darf nicht leer sein.'
              ),
              validationRules.isNotPastTime(
                new Date(),
                rawDate,
                'Endzeitpunkt darf nicht in der Vergangenheit liegen.'
              ),
            ]"
          />
          <v-tooltip
            v-if="!rightsize"
            text="Server ist nicht Teil der Patchnight!"
            location="top"
            :open-on-hover="!server.patchnightIncluded"
          >
            <template #activator="{ props: tooltipProps }">
              <span v-bind="tooltipProps">
                <v-checkbox
                  v-model="schedulePatchnight"
                  :label="
                    'Durchführungszeitpunkt im Rahmen der nächsten Patchnight am: ' +
                    formatter
                      .formatToGermanLocalTime(server.patchnightStartDate)
                      .split(',')[0]
                  "
                  :disabled="schedule || !server.patchnightIncluded"
                  @change="setTimePatchnight"
                />
              </span>
            </template>
          </v-tooltip>
        </v-col>
      </v-row>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Server from "@/types/Server";

import { mdiAlertCircleCheck, mdiPencil } from "@mdi/js";
import { computed, inject, ref, watch } from "vue";

import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import LinearProgressWithColors from "@/components/common/LinearProgressWithColors.vue";
import { useFormatter } from "@/composables/formatter.js";
import { useRules } from "@/composables/rules";
import { useUserStore } from "@/stores/user";

const formatter = useFormatter();
const validationRules = useRules();

const props = defineProps<{
  server: Server;
  rightsize: boolean;
}>();

const emit =
  defineEmits<
    (
      e: "save",
      cpus: number,
      ram: number,
      scheduleTime: string | null,
      schedulePatchnight: boolean
    ) => void
  >();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const userStore = useUserStore();
const isNonOracleUser = computed(() =>
  userStore.getUser?.authorities.includes("ROLE_NON-ORACLE")
);

const form = ref<HTMLFormElement>();
const dialog = ref(false);
const cpus = ref<number>(
  props.server.numCpu != undefined ? props.server.numCpu : 1
);
const currentRam = formatter.calculateMBtoGB(props.server.memoryMb);
const ram = ref<number>(
  props.server.memoryMb != undefined
    ? formatter.calculateMBtoGB(props.server.memoryMb)
    : 4
);

// Dialog-Status überwachen
watch(dialog, (newValue) => {
  if (newValue) {
    if (props.rightsize) {
      cpus.value = props.server.numCpuRecommended;
      ram.value = props.server.memoryMbRecommended / 1024;
    } else {
      cpus.value = props.server.numCpu != undefined ? props.server.numCpu : 1;
      ram.value =
        props.server.memoryMb != undefined ? props.server.memoryMb / 1024 : 2;
    }
    registerOpenDialog?.();
  } else {
    unregisterOpenDialog?.();
  }
});

const schedule = ref(false);
const schedulePatchnight = ref(false);
const validated = ref(true);
const rawDate = ref<Date>(new Date());

function changeToSchedule() {
  const nowPlusFive = new Date();
  // Set time to +5 minutes to prevent immediate validation error (past time)
  // and ensure the validation has enough buffer.
  nowPlusFive.setMinutes(nowPlusFive.getMinutes() + 5);
  nowPlusFive.setSeconds(0);
  nowPlusFive.setMilliseconds(0);
  rawDate.value = nowPlusFive;
}

function setTimePatchnight() {
  if (schedulePatchnight.value) {
    rawDate.value = new Date(props.server.patchnightStartDate);
    rawDate.value.setHours(14, 0, 0, 0);
  } else {
    rawDate.value = new Date();
  }
}

function close() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  validated.value = true;
  schedule.value = false;
  schedulePatchnight.value = false;
  rawDate.value = new Date();
  cpus.value = props.server.numCpu != undefined ? props.server.numCpu : 1;
  ram.value =
    props.server.memoryMb != undefined ? props.server.memoryMb / 1024 : 2;
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      const scheduleTime =
        schedule.value || schedulePatchnight.value
          ? rawDate.value.toISOString()
          : null;
      const isPatchnight = schedulePatchnight.value;

      emit("save", cpus.value, ram.value, scheduleTime, isPatchnight);
      dialog.value = false;
      resetForm();
    }
  });
}

watch(
  () => props.server,
  (newServer) => {
    if (newServer) {
      cpus.value = newServer.numCpu || 1;
      ram.value = newServer.memoryMb ? newServer.memoryMb / 1024 : 2;
    }
    if (props.rightsize) {
      cpus.value = props.server.numCpuRecommended;
      ram.value = props.server.memoryMbRecommended / 1024;
    } else {
      cpus.value = props.server.numCpu != undefined ? props.server.numCpu : 1;
      ram.value =
        props.server.memoryMb != undefined ? props.server.memoryMb / 1024 : 2;
    }
  }
);

watch([rawDate], async () => {
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
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
