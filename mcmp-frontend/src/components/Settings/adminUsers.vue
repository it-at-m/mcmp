<template>
  <CommonCard title="Adminbenutzer">
    <template #toolbar-actions>
      <v-btn
        :icon="mdiPlus"
        @click="askAddPermission"
        aria-label="Adminrechte hinzufügen"
      />
    </template>

    <v-data-table
      :headers="headers"
      :items="adminUsers"
      :items-per-page="-1"
      hide-default-footer
    >
      <template v-slot:item.edit="{ item }">
        <v-btn
          :icon="mdiDelete"
          @click="askDeletePermission(item)"
          :aria-label="`Adminrechte für ${item.username} löschen`"
        />
      </template>
    </v-data-table>
  </CommonCard>
  <v-dialog
    v-model="addDialog"
    max-width="500px"
  >
    <admin-user-add
      @addDialog="(dialog) => (addDialog = dialog)"
      @toAdd="(item) => updatePermission(item)"
    />
  </v-dialog>
  <v-dialog
    v-model="deleteDialog"
    max-width="500px"
  >
    <v-card>
      <v-toolbar>
        <v-toolbar-title class="text-h5">Adminrechte löschen </v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          @click="deleteDialog = false"
        />
      </v-toolbar>
      <v-card-text
        >Wollen Sie die Adminrechte für
        <strong>{{ toEdit.username }}</strong> löschen?</v-card-text
      >
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="cancel"
          @click="deleteDialog = false"
          >Abbrechen</v-btn
        >
        <v-btn
          variant="flat"
          color="do"
          @click="deletePermission(toEdit)"
          >Löschen</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type { AdminUser } from "@/types/AdminUser";

import { mdiClose, mdiDelete, mdiPlus } from "@mdi/js";
import { onMounted, ref } from "vue";

import userService from "@/api/userService";
import CommonCard from "@/components/common/CommonCard.vue";
import AdminUserAdd from "./adminUserAdd.vue";

const adminUsers = ref<AdminUser[]>([]);
const loadingAdminUsers = ref(false);

function getAdminUsers() {
  userService.getAdminUsers(loadingAdminUsers).then((res) => {
    adminUsers.value = res;
  });
}

onMounted(() => {
  getAdminUsers();
});

const headers = [
  { title: "Loginname", key: "username" },
  { title: "Abteilung", key: "department" },
  { title: "Löschen", key: "edit", sortable: false, align: "end" },
];

const addDialog = ref(false);
const deleteDialog = ref(false);
const toEdit = ref();

function askDeletePermission(item: AdminUser) {
  deleteDialog.value = true;
  toEdit.value = item;
}

function askAddPermission() {
  addDialog.value = true;
}

function deletePermission(item: AdminUser) {
  deleteDialog.value = false;
  item.admin = false;
  updatePermission(item);
}

const loading = ref(false);
async function updatePermission(item: AdminUser) {
  await userService.updateAdminPermission(item, loading);
  getAdminUsers();
}
</script>
