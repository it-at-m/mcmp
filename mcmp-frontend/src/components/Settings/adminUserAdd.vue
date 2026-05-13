<template>
  <v-card>
    <v-toolbar>
      <v-toolbar-title class="text-h5"
        >Admin Berechtigung hinzufügen</v-toolbar-title
      >
      <v-btn
        :icon="mdiClose"
        @click="cancelAdd()"
      />
    </v-toolbar>
    <v-card-text>
      <v-form ref="form">
        Admin Berechtigung für:
        <v-autocomplete
          v-model="toAddUser"
          :items="users"
          :item-title="(item) => item.username"
          return-object
          :rules="[
            validationRules.notEmptyRule('Der Loginname darf nicht leer sein.'),
          ]"
        />
      </v-form>
    </v-card-text>
    <v-card-actions>
      <v-spacer />
      <v-btn
        color="cancel"
        text
        @click="cancelAdd"
        >Abbrechen</v-btn
      >
      <v-btn
        variant="flat"
        color="do"
        @click="saveEdit"
        >Hinzufügen</v-btn
      >
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import type { AdminUser } from "@/types/AdminUser";

import { mdiClose } from "@mdi/js";
import { onMounted, ref } from "vue";

import userService from "@/api/userService";
import { useRules } from "@/composables/rules";

const emit = defineEmits<{
  (e: "addDialog", dialog: boolean): void;
  (e: "toAdd", toAdd: AdminUser): void;
}>();

const validationRules = useRules();
const form = ref<HTMLFormElement>();
const toAddUser = ref<AdminUser>();

function saveEdit() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      toAddUser.value.admin = true;
      emit("toAdd", toAddUser.value);
      emit("addDialog", false);
      form.value?.resetValidation();
    }
  });
}

function cancelAdd() {
  form.value?.resetValidation();
  emit("addDialog", false);
}

const users = ref<AdminUser[]>([]);
const loadingUsers = ref(false);

function getNotAdminUsers() {
  userService.getNotAdminUsers(loadingUsers).then((res) => {
    users.value = res;
  });
}

onMounted(() => {
  getNotAdminUsers();
});
</script>
