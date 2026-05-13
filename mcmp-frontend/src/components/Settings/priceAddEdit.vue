<template>
  <v-dialog
    v-model="dialog"
    width="70%"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        :icon="icon"
        :aria-label="ariaLabel"
      >
      </v-btn>
    </template>
    <v-card>
      <v-toolbar>
        <v-toolbar-title class="text-h5">{{ props.title }}</v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          @click="close()"
        />
      </v-toolbar>
      <v-card-text>
        <v-form ref="form">
          <v-row>
            <v-col cols="6">
              <v-text-field
                label="Name"
                v-model="priceTmp.name"
                :rules="[rules.notEmptyRule('Darf nicht leer sein.')]"
                :disabled="props.price?.name !== undefined"
              />
            </v-col>
            <v-col cols="6">
              <v-textarea
                label="Beschreibung"
                v-model="priceTmp.description"
              />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="6">
              <v-number-input
                label="Preis pro Einheit"
                v-model="priceTmp.pricePerUnit"
                :rules="[rules.notEmptyRule('Darf nicht leer sein.')]"
                :min="0"
                :step="0.01"
                :precision="2"
              />
            </v-col>
            <v-col cols="6">
              <v-select
                label="Währung"
                v-model="priceTmp.currency"
                :items="['EURO', 'USD', 'CHF']"
                :rules="[rules.notEmptyRule('Darf nicht leer sein.')]"
                :menu-props="{ persistent: true, closeOnContentClick: true }"
              />
            </v-col>
          </v-row>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-btn
          color="cancel"
          @click="close"
          >Abbrechen</v-btn
        >
        <v-btn
          color="do"
          variant="flat"
          @click="save"
          >Speichern</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type Price from "@/types/Price";

import { mdiClose } from "@mdi/js";
import { ref } from "vue";

import { useRules } from "@/composables/rules.js";

const props = defineProps<{
  title: string;
  icon: string;
  price?: Price;
}>();

const ariaLabel = props.price?.name
  ? `Preis ${props.price.name} bearbeiten`
  : "Neuen Preis anlegen";
const form = ref<HTMLFormElement>();
const rules = useRules();
const dialog = ref(false);
const priceTmp = ref<Price>({
  name: props.price?.name !== undefined ? props.price.name : "",
  pricePerUnit: props.price?.pricePerUnit || 0,
  currency: props.price?.currency !== undefined ? props.price.currency : "EURO",
  description:
    props.price?.description !== undefined ? props.price.description : "",
});

const emit = defineEmits<{
  (e: "save", price: Price): void;
}>();

function close() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  priceTmp.value = {
    name: "",
    pricePerUnit: 0,
    currency: "EURO",
    description: "",
  };
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emit("save", priceTmp.value);
      dialog.value = false;
      resetForm();
    }
  });
}
</script>
