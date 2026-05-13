<template>
  <CommonCard title="Actions">
    <template #toolbar-actions>
      <actionAddEdit
        title="Action hinzufügen"
        :icon="mdiPlus"
        :awxConfigs="awxConfigs"
        :snowConfigs="snowConfigs"
        @save="saveAction"
        :all-actions="items"
      />
    </template>

    <v-data-table
      :headers="headers"
      :items="items"
      :items-per-page="-1"
      :loading="loading"
      hide-default-footer
    >
      <template v-slot:item.enabled="{ item }">
        <v-chip
          :color="item.enabled ? '_green' : '_red'"
          dark
        >
          {{ item.enabled ? "Aktiv" : "Inaktiv" }}
        </v-chip>
      </template>
      <template v-slot:item.changeRequired="{ item }">
        {{ formatter.formatBooleanToGerman(item.changeRequired) }}
      </template>
      <template v-slot:item.createIncidents="{ item }">
        <span :class="{ 'text-error': item.createIncidents === false }">
          {{ formatter.formatBooleanToGerman(item.createIncidents) }}
        </span>
      </template>
      <template v-slot:item.awxJobEnabled="{ item }">
        {{ formatter.formatBooleanToGerman(item.awxJobEnabled) }}
      </template>
      <template v-slot:item.edit="{ item }">
        <actionAddEdit
          title="Action bearbeiten"
          :icon="mdiPencil"
          :action="item"
          :awxConfigs="awxConfigs"
          :snowConfigs="snowConfigs"
          @save="editItem"
          :all-actions="items"
        />
      </template>
    </v-data-table>
  </CommonCard>
</template>

<script setup lang="ts">
import type Action from "@/types/Action";
import type { AwxConfig } from "@/types/AwxConfig";
import type { SnowConfig } from "@/types/SnowConfig";

import { mdiPencil, mdiPlus } from "@mdi/js";
import { onMounted, ref } from "vue";

import actionService from "@/api/actionService";
import awxConfigService from "@/api/awxConfigService";
import snowConfigService from "@/api/snowConfigService";
import CommonCard from "@/components/common/CommonCard.vue";
import actionAddEdit from "@/components/Settings/actionAddEdit.vue";
import { useFormatter } from "@/composables/formatter.js";

const loading = ref(false);
const items = ref<Action[]>([]);
const formatter = useFormatter();
const awxConfigs = ref<AwxConfig[]>([]);
const snowConfigs = ref<SnowConfig[]>([]);

const headers = [
  { title: "Identifier", key: "identifier" },
  { title: "Status", key: "enabled" },
  { title: "Change benötigt", key: "changeRequired" },
  { title: "Incidents", key: "createIncidents" },
  { title: "ServiceNow", key: "snowConfig.apiDescription" },
  { title: "AWX Job", key: "awxJobEnabled" },
  { title: "AWX", key: "awxConfig.apiDescription" },
  { title: "Title", key: "title" },
  { title: "Bearbeiten", key: "edit", sortable: false, align: "end" },
] as const;

onMounted(() => {
  getActions();
  getAwxConfigs();
  getSnowConfigs();
});

function getActions() {
  actionService.getActions(loading).then((response) => {
    items.value = response;
  });
}

function editItem(action: Action) {
  actionService.updateAction(action, loading).then(() => {
    getActions();
  });
}

function saveAction(action: Action) {
  actionService.saveAction(action, loading).then(() => {
    getActions();
  });
}

function getAwxConfigs() {
  awxConfigService.getAwxConfigs(loading).then((response) => {
    awxConfigs.value = response;
  });
}

function getSnowConfigs() {
  snowConfigService.getSnowConfigs(loading).then((response) => {
    snowConfigs.value = response;
  });
}
</script>
