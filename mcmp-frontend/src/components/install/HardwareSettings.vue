<template>
  <v-container class="links">
    <v-row>
      <v-col>
        <strong>CPUs*</strong>
        <v-number-input
          v-model="instlServerDetails.cpu"
          :min="1"
          :max="8"
          hint="Erlaubte Werte 1 bis 8"
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
          :min="instlServerDetails.category?.label == 'OracleDB' ? 6 : 2"
          :max="64"
          :hint="
            'Erlaubte Werte ' +
            (instlServerDetails.category?.label == 'OracleDB' ? 6 : 2) +
            ' bis 64'
          "
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
import { onMounted, ref, watch } from "vue";

import networkService from "@/api/networkService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import DiskSizeInput from "@/components/install/DiskSizeInput.vue";
import installServerDetails, {
  categoryType,
  OsType,
} from "@/types/installServerDetails";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
const loading = ref(false);
const networkgroups = ref<NetworkGroup[]>([]);

function getNetworkGroups() {
  if (props.instlServerDetails.appservice?.id) {
    networkService
      .getFilteredNetworkGroups(
        loading,
        props.instlServerDetails.appservice?.id!,
        props.instlServerDetails.isDatabase()!
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
    props.instlServerDetails.category,
    props.instlServerDetails.appservice,
  ],
  ([category, applicationServiceClass]) => {
    if (category && applicationServiceClass) {
      getNetworkGroups();
      props.instlServerDetails.networkGroup = null;
    }
  }
);
</script>
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
