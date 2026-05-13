<template>
  <CommonCard title="Allgemein">
    <div v-if="!selectedServer.patchnightIncluded">
      Server ist nicht in der Patchnight
    </div>
    <div v-if="selectedServer.patchnightIncluded">
      <v-row>
        <v-col cols="4">
          <h3>Umgebung</h3>
        </v-col>
        <v-col cols="4">
          <h3>Gruppe</h3>
        </v-col>
        <v-col cols="4">
          <h3>Startzeit Gruppe</h3>
        </v-col>
      </v-row>
      <v-row>
        <v-col
          cols="4"
          class="pt-0"
        >
          <p>
            {{
              selectedServer.patchnightEnvironment
                ? selectedServer.patchnightEnvironment + "-Patchnight"
                : "-"
            }}
          </p>
        </v-col>
        <v-col
          cols="4"
          class="pt-0"
        >
          <p>
            {{ formatter.ifEmptyReturnDash(selectedServer.patchnightGroup) }}
          </p>
        </v-col>
        <v-col
          cols="4"
          class="pt-0"
        >
          <p>
            {{ formatter.ifEmptyReturnDash(selectedServer.patchnightTime) }}
          </p>
        </v-col>
      </v-row>
    </div>
  </CommonCard>
  <CommonCard
    v-if="selectedServer.patchnightIncluded"
    title="Nächste Patchnight"
    topMargin="0"
  >
    <template #toolbar-actions>
      <EditPatchnightTime
        :selectedServer="props.selectedServer"
        @save="change_patchnight_time"
        v-if="props.selectedServer.canEdit && props.selectedServer.roleLinux"
      />
    </template>
    <v-row>
      <v-col cols="4">
        <h3>Start</h3>
      </v-col>
      <v-col cols="4">
        <h3>Ende</h3>
      </v-col>
      <v-col cols="4">
        <h3>Change</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="4"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatToGermanLocalTime(
              selectedServer.patchnightStartDate
            )
          }}
        </p>
      </v-col>
      <v-col
        cols="4"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatToGermanLocalTime(selectedServer.patchnightEndDate)
          }}
        </p>
      </v-col>
      <v-col
        cols="4"
        class="pt-0"
        v-if="Number(selectedServer.patchnightChangeNumber) != 0"
      >
        {{ selectedServer.patchnightChangeNumber }}
      </v-col>
    </v-row>
  </CommonCard>
  <CommonCard
    v-if="selectedServer.patchnightIncluded"
    title="Vergangene Patchnight"
    topMargin="0"
  >
    <v-row>
      <v-col>
        <h3 class="d-flex align-center ga-2">
          Abschlussergebnis
          <v-icon
            v-if="Number(props.selectedServer.patchnightExitcode) === 0"
            :icon="mdiCheckCircle"
            color="_green"
            size="small"
          />
          <v-icon
            v-else
            :icon="mdiAlertCircle"
            color="_red"
            size="small"
          />
        </h3>
      </v-col>
    </v-row>

    <v-row v-if="Number(props.selectedServer.patchnightExitcode) !== 0">
      <v-col class="pt-0">
        <h4>
          Fehlermeldung vom
          {{
            formatter.formatToBerlinDate(
              props.selectedServer.patchnightExitcodeChangeDate
            )
          }}
        </h4>
        <div
          class="text-body-2"
          style="white-space: pre-line"
        >
          {{ selectedServer.patchnightExitstring }}
        </div>
      </v-col>
    </v-row>
  </CommonCard>
</template>

<script setup lang="ts">
import { mdiAlertCircle, mdiCheckCircle } from "@mdi/js";
import { ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import EditPatchnightTime from "@/components/Server/EditPatchnightTime.vue";
import { useFormatter } from "@/composables/formatter";
import Server from "@/types/Server";

const jobLoading = ref(false);
const props = defineProps<{
  selectedServer: Server;
}>();

const emit = defineEmits<{
  (e: "changed"): void;
}>();

const formatter = useFormatter();

function change_patchnight_time(time: string) {
  jobService
    .startJob(
      jobLoading,
      "LINUX_PATCHNIGHT_TIME_CHANGE",
      props.selectedServer.id,
      {
        time: time.replace(":", ""),
      }
    )
    .then(() => {
      emit("changed");
    });
}
</script>
