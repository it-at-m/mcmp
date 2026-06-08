<template>
  <v-tooltip
    location="bottom"
    text="Export-Policy ändern"
    :open-on-hover="true"
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          icon
          variant="flat"
          aria-label="Export-Policy ändern"
          @click="openDialog"
        >
          <v-icon>{{ mdiPencil }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    v-model="dialog"
    :loading="loading"
    title="Export-Policy ändern"
    max-width="600"
    show-actions
    :submit-activated="validated"
    :icon="mdiPencil"
    show-change-warning
    :check-for-enabled-actions="['STORAGE_CHANGE_NFS_EXPORT_POLICY']"
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <v-form v-model="validated">
      <v-row class="mb-4">
        <v-col cols="12">
          <v-autocomplete
            v-model="selectedServer"
            v-model:search="searchText"
            :items="serverList"
            :loading="loadingServers"
            label="Server auswählen"
            variant="outlined"
            item-title="name"
            item-value="fqdn"
            clearable
            :rules="[
              rules.notEmptySelectRule('Ein Server muss ausgewählt werden'),
            ]"
            @click="getServers"
          >
            <template #no-data
              ><a class="ml-2">Keine Server gefunden</a></template
            >
          </v-autocomplete>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12">
          <v-select
            v-model="selectedPermission"
            label="Berechtigungen"
            variant="outlined"
            :items="permissionOptions"
            item-title="label"
            item-value="value"
            :rules="[
              rules.notEmptySelectRule(
                'Eine Berechtigung muss ausgewählt werden'
              ),
            ]"
          />
        </v-col>
      </v-row>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type { ServerList } from "@/types/ServerList.ts";

import { mdiPencil } from "@mdi/js";
import { ref, watch } from "vue";

import jobService from "@/api/jobService.ts";
import serverService from "@/api/serverService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const validated = ref(false);
const dialog = ref(false);
const loading = ref(false);
const loadingServers = ref(false);
const rules = useRules();
const selectedServer = ref("");
const selectedPermission = ref("");
const searchText = ref("");
const serverList = ref<ServerList[]>([]);
const requestedAlready = ref(false);

const permissionOptions = [
  { label: "read-write (rw)", value: "rw" },
  { label: "read-only (ro)", value: "ro" },
];

const props = defineProps<{
  storageUuid: string;
  mountPath: string;
}>();

watch(selectedServer, () => {
  searchText.value = "";
});


function openDialog() {
  validated.value = false;
  dialog.value = true;
}

function reset() {
  selectedServer.value = "";
  selectedPermission.value = "";
  searchText.value = "";
  requestedAlready.value = false;
}

function close() {
  reset();
  dialog.value = false;
}

function save() {
  if (validated.value && selectedServer.value && selectedPermission.value) {
      jobService
        .startJob(loading, "STORAGE_CHANGE_NFS_EXPORT_POLICY", -1, {
          uuid: props.storageUuid,
          fqdn: selectedServer.value,
          permission: selectedPermission.value,
        })
        .then(() => {
          close();
        });
  }
}

function getServers() {
  if (requestedAlready.value) {
    return;
  }
  requestedAlready.value = true;
  loadingServers.value = true;
  serverService
    .getVisibleServers(loadingServers, 0, -1, "name", "asc", "", [], "")
    .then((response) => {
      serverList.value = response.content;
    })
    .finally(() => {
      loadingServers.value = false;
    });
}
</script>
