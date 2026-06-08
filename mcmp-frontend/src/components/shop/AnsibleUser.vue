<template>
  <CommonDialog
    v-model="dialog"
    :loading="loading"
    title="Ansible User Aktionen"
    max-width="600"
    show-actions
    submitActivated
    :icon="mdiAccount"
    @dialog-cancel="close"
    @dialog-confirm="save"
    showChangeWarning
    :checkForEnabledActions="['ANSIBLE_USER_ADD', 'ANSIBLE_USER_REMOVE']"
  >
  <CommonAlert
          color="info"
          class="mb-6"
          v-if="isAdd"
        >
          <div>
        Neuen Ansible User erstellen und/oder bestehenden User auf weitere Server berechtigen.
                <br>Infos unter <a href="https://go.muenchen.de/sp/KB0014700" target="_blank" rel="noopener" class="text-primary font-weight-bold text-decoration-none">KB0014700</a>
          </div>
        </CommonAlert>

    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        flat
        @click="registerOpenDialog"
        >Ansible User Aktionen
      </v-btn>
    </template>

    <v-radio-group v-model="isAdd">
      <v-row>
        <v-radio
          :value="true"
          label="Hinzufügen"
        />
        <!--
        <v-radio
          :value="false"
          label="Entfernen"
        />
        -->
      </v-row>
    </v-radio-group>
    <br />
    <v-form ref="form">
      <v-row>
        <v-text-field
          v-model="accountName"
          label="Ansible Account Name"
          placeholder="svc-ans-..."
          variant="outlined"
          :rules="[
            rules.notEmptyRule('Ansible Account Name ist erforderlich'),
            rules.regexRule(
              /^svc-ans-[a-z0-9-]{1,12}$/,
              'Der Name muss mit svc-ans- beginnen und darf insgesamt max. 20 Zeichen umfassen'
            ),
          ]"
          class="mb-2"
        ></v-text-field>
      </v-row>
      <v-row v-if="isAdd">
        <v-autocomplete
          v-model="selectedServers"
          v-model:search="searchText"
          :items="serverList"
          :loading="loading"
          label="Server auswählen"
          variant="outlined"
          item-title="name"
          item-value="id"
          multiple
          chips
          clearable
          outlined
          :rules="[
            rules.notEmptySelectRule(
              'Mindestens ein Server muss ausgewählt werden'
            ),
          ]"
          @click="getServers"
        >
          <template #no-data
            ><a class="ml-2">Keine Server gefunden</a></template
          >
        </v-autocomplete>
      </v-row>
    </v-form>
    <CommonAlert
      color="info"
      class="mt-4"
      v-if="!isAdd"
    >
      <div>
        Der Ansible User wird von allen Servern entfernt, welche der Abteilung
        des Benutzers zugeordnet sind.
      </div>
    </CommonAlert>
  </CommonDialog>
</template>
<script setup lang="ts">
import type { ServerList } from "@/types/ServerList.ts";

import { mdiAccount } from "@mdi/js";
import { inject, ref, watch } from "vue";

import jobService from "@/api/jobService.ts";
import serverService from "@/api/serverService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const dialog = ref(false);
const loading = ref(false);
const rules = useRules();
const isAdd = ref(true);
const form = ref<HTMLFormElement>();
const accountName = ref("svc-ans-");
const selectedServers = ref([]);
const searchText = ref("");
const serverList = ref<ServerList[]>([]);
const requestedAlready = ref(false);

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

watch(selectedServers, () => {
  searchText.value = "";
});

function reset() {
  isAdd.value = true;
  accountName.value = "svc-ans-";
  selectedServers.value = [];
  searchText.value = "";
  requestedAlready.value = false;
}

function close() {
  unregisterOpenDialog?.();
  reset();
  dialog.value = false;
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      if (isAdd.value) {
        jobService
          .startJob(loading, "ANSIBLE_USER_ADD", -1, {
            account_name: accountName.value,
            server_ids: selectedServers.value,
          })
          .then(() => {
            close();
          });
      } else {
        jobService
          .startJob(loading, "ANSIBLE_USER_REMOVE", -1, {
            account_name: accountName.value,
          })
          .then(() => {
            close();
          });
      }
    }
  });
}

function getServers() {
  if (requestedAlready.value) {
    return;
  }
  requestedAlready.value = true;
  serverService
    .getVisibleServers(loading, 0, -1, "name", "asc", "", [], "")
    .then((response) => {
      serverList.value = response.content;
    });
}
</script>
