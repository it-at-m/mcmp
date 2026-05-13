<template>
  <v-dialog
    v-model="dialog"
    max-width="600px"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        :icon="mdiPlus"
        aria-label="vCenter C Berechtigungen hinzufügen"
      >
      </v-btn>
    </template>
    <v-card>
      <v-toolbar>
        <v-toolbar-title class="text-h5"
          >vCenter C Berechtigungen hinzufügen
        </v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          @click="cancelAdd"
        />
      </v-toolbar>
      <v-card-text>
        <v-form ref="form">
          <v-autocomplete
            v-model="toAddAppservice"
            :items="props.appservices"
            :item-title="(item) => item.name"
            return-object
            :rules="[validationRules.notEmptyRule('Darf nicht leer sein.')]"
            :loading="props.loading"
            aria-label="Appservice welcher vCenter C Berechtigungen erhalten soll auswählen"
            label="vCenter C Berechtigung"
            clearable
            rounded
            variant="outlined"
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
  </v-dialog>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList.ts";

import { mdiClose, mdiPlus } from "@mdi/js";
import { ref } from "vue";

import { useRules } from "@/composables/rules";

const props = defineProps<{
  appservices: AppserviceList[];
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "toAdd", toAdd: AppserviceList): void;
}>();

const dialog = ref(false);
const validationRules = useRules();
const form = ref<HTMLFormElement>();
const toAddAppservice = ref<AppserviceList>();

function saveEdit() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      toAddAppservice.value!.enableVcenterc = true;
      emit("toAdd", toAddAppservice.value!);
      form.value?.resetValidation();
      dialog.value = false;
    }
  });
}

function cancelAdd() {
  form.value?.resetValidation();
  dialog.value = false;
}
</script>
