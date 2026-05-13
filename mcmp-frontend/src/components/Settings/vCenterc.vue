<template>
  <CommonCard title="vCenter C Berechtigungen Verwalten">
    <template #toolbar-actions>
      <v-centerc-add
        @toAdd="changeAppservice"
        :appservices="appservicesvCentercDisabled"
        :loading="loading"
      />
    </template>
    <v-data-table
      :headers="headers"
      :items="appservicesvCentercEnabled"
      :items-per-page="-1"
      hide-default-footer
    >
      <template v-slot:item.enableVcenterc="{ item }">
        <v-chip
          :color="item.enableVcenterc ? '_green' : '_red'"
          dark
        >
          {{ item.enableVcenterc ? "Berechtigt" : "Nicht Berechtigt" }}
        </v-chip>
      </template>
      <template v-slot:item.edit="{ item }">
        <v-btn
          :icon="mdiDelete"
          @click="askDeletePermission(item)"
          :aria-label="`vCenter C Berechtigung für ${item.name} entfernen`"
        />
      </template>
    </v-data-table>
  </CommonCard>
  <v-dialog
    v-model="deleteDialog"
    max-width="500px"
  >
    <v-card>
      <v-toolbar>
        <v-toolbar-title class="text-h5"
          >vCenter C Berechtigung Entfernen
        </v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          @click="cancelDelete"
        />
      </v-toolbar>
      <v-card-text
        >Wollen Sie dem Service die vCenter C Berechtigung Entfernen?
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="cancel"
          @click="cancelDelete"
          >Abbrechen</v-btn
        >
        <v-btn
          variant="flat"
          color="do"
          @click="changeAppservice(toEdit)"
          >Entfernen
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { mdiClose, mdiDelete } from "@mdi/js";
import { onMounted, ref } from "vue";

import appserviceService from "@/api/appserviceService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import VCentercAdd from "@/components/Settings/vCentercAdd.vue";
import AppserviceList from "@/types/AppserviceList.ts";

const headers = [
  { title: "Name", key: "name" },
  { title: "vCenter C Berechtigt", key: "enableVcenterc" },
  { title: "Entfernen", key: "edit", sortable: false, align: "center" },
] as const;

const loading = ref(false);
const appservicesvCentercEnabled = ref<AppserviceList[]>([]);
const appservicesvCentercDisabled = ref<AppserviceList[]>([]);
const deleteDialog = ref(false);
const toEdit = ref();

function getAllvCenterCenabledAppservices() {
  appserviceService
    .getAppservices(loading, 0, -1, "asc", "")
    .then((response) => {
      appservicesvCentercEnabled.value = response.content.filter(
        (appservice) => appservice.enableVcenterc
      );
      appservicesvCentercDisabled.value = response.content.filter(
        (appservice) =>
          !appservice.enableVcenterc && appservice.environment == "C"
      );
    });
}

function askDeletePermission(appservice: AppserviceList) {
  deleteDialog.value = true;
  toEdit.value = appservice;
  toEdit.value.enableVcenterc = false;
}
function cancelDelete() {
  deleteDialog.value = false;
  toEdit.value.enableVcenterc = true;
}

function changeAppservice(appservice: AppserviceList) {
  appserviceService.updatevCenterc(loading, appservice).then(() => {
    getAllvCenterCenabledAppservices();
    deleteDialog.value = false;
  });
}

onMounted(() => {
  getAllvCenterCenabledAppservices();
});
</script>
