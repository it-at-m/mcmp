<template>
  <v-container class="links">
    <v-row>
      <v-col>
        <strong>CPUs*</strong>
        <v-number-input
          v-model="instlServerDetails.cpu"
          :min="instlServerDetails.category?.label == 'PostgreSQL' ? 4 : 2"
          :max="8"
          :hint="
            'Erlaubte Werte ' +
            (instlServerDetails.category?.label == 'PostgreSQL' ? 4 : 2) +
            ' bis 8'
          "
          persistent-hint
          control-variant="split"
          variant="solo"
        >
        </v-number-input>
      </v-col>
      <v-col>
        <strong>Arbeitsspeicher in GB*</strong>
        <v-number-input
          v-model="instlServerDetails.memory"
          :min="minMemory"
          :max="64"
          :hint="'Erlaubte Werte ' + minMemory + ' bis 64'"
          persistent-hint
          control-variant="split"
          variant="solo"
          @click="console.log(instlServerDetails)"
        >
        </v-number-input>
      </v-col>

      <v-col
        v-if="
          instlServerDetails.osType == 'Windows' &&
          instlServerDetails.categoryType == 'Standard'
        "
      >
        <disk-size-input
          v-model="
            instlServerDetails.disk[OsType.Windows][categoryType.Standard][0]
              .size
          "
          title="Größe Festplatte C:\"
          :min-size-in-g-b="
            instlServerDetails.disk[OsType.Windows][categoryType.Standard][0]
              .min_size
          "
          :max-size-in-g-b="
            instlServerDetails.disk[OsType.Windows][categoryType.Standard][0]
              .max_size
          "
        />
      </v-col>
      <v-col
        v-if="
          instlServerDetails.osType == 'Windows' &&
          instlServerDetails.category?.label == 'MSSQL'
        "
      >
        <div
          v-for="diskConfig in instlServerDetails.disk[OsType.Windows]?.[
            categoryType.DB
          ]"
          :key="diskConfig.drive_number"
        >
          <disk-size-input
            v-model="diskConfig.size"
            :title="`Größe Festplatte ${diskConfig.label}`"
            :min-size-in-g-b="diskConfig.min_size"
            :max-size-in-g-b="diskConfig.max_size"
          />
        </div>
      </v-col>
    </v-row>
    <strong>Netzwerkgruppe*</strong>&nbsp;
    <a
      href="https://go.muenchen.de/sp/KB0023952"
      target="_blank"
      rel="noopener noreferrer"
    >
      Hilfe
      <v-icon
        :icon="mdiOpenInNew"
        size="16"
        style="vertical-align: baseline; position: relative; top: 2px"
      />
    </a>
    <v-autocomplete
      v-model="instlServerDetails.networkGroup"
      :items="networkgroups"
      item-title="name"
      dense
      clearable
      return-object
      rounded
      variant="outlined"
    />
    <common-alert
      color="info"
      class="mt-2"
    >
      Sollten Ihnen ein bestimmtes Netz fehlen, können Sie dieses ganz einfach
      über das Ticket
      <a
        href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=4567ec71835d3ed0eba660e0deaad35d"
        target="_blank"
        rel="noopener noreferrer"
      >
        Netzwerkgruppe in MCMP zuordnen
        <v-icon
          :icon="mdiOpenInNew"
          size="16"
          style="vertical-align: baseline; position: relative; top: 2px"
        />
      </a>
      beantragen.
    </common-alert>
  </v-container>
</template>

<script setup lang="ts">
import type NetworkGroup from "@/types/NetworkGroup.ts";

import { mdiOpenInNew } from "@mdi/js";
import { computed, onMounted, ref, watch } from "vue";

import networkService from "@/api/networkService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import DiskSizeInput from "@/components/install/DiskSizeInput.vue";
import installServerDetails, {
  categoryType,
  OsType,
} from "@/types/installServerDetails";

const instlServerDetails = defineModel<installServerDetails>(
  "instlServerDetails",
  { required: true }
);
const minMemory = computed(() =>
  ["OracleDB", "MSSQL"].includes(instlServerDetails.value.category?.label ?? "")
    ? 6
    : 4
);

watch(
  minMemory,
  (min) => {
    if (instlServerDetails.value.memory < min) {
      instlServerDetails.value.memory = min;
    }
  },
  { immediate: true }
);
const loading = ref(false);
const networkgroups = ref<NetworkGroup[]>([]);

function getNetworkGroups() {
  const appserviceId = instlServerDetails.value.appservice?.id;
  if (appserviceId) {
    networkService
      .getFilteredNetworkGroups(
        loading,
        appserviceId,
        instlServerDetails.value.isDatabase()
      )
      .then((networkGroups) => {
        networkgroups.value = networkGroups;
      });
  }
}

onMounted(async () => {
  getNetworkGroups();
});

watch(
  () => [
    instlServerDetails.value.category,
    instlServerDetails.value.appservice,
    instlServerDetails.value.applications.length,
  ],
  ([category, applicationServiceClass, applicationCount]) => {
    if ((category || applicationCount) && applicationServiceClass) {
      getNetworkGroups();
      instlServerDetails.value.networkGroup = null;
    }
  }
);
</script>
