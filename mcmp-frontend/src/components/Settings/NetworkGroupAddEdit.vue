<template>
  <v-dialog
    v-model="dialog"
    width="70%"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        :icon="icon"
        class="mr-2"
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
                v-model="networkGroupTmp.name"
                :rules="[rules.notEmptyRule('Darf nicht leer sein.')]"
              />
            </v-col>
            <v-col cols="6">
              <v-select
                label="Umgebung"
                v-model="networkGroupTmp.environment"
                :items="Object.values(EnvironmentType)"
                :rules="[rules.notEmptySelectRule('Darf nicht leer sein.')]"
                :menu-props="{ persistent: true, closeOnContentClick: true }"
              />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="6">
              <v-select
                label="Typ"
                :items="['application', 'database', 'storage']"
                v-model="selectedType"
                @update:modelValue="onTypeChange"
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
          >Abbrechen
        </v-btn>
        <v-btn
          color="do"
          @click="save"
          >Speichern
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type NetworkGroup from "@/types/NetworkGroup.ts";

import { mdiClose } from "@mdi/js";
import { onMounted, ref } from "vue";

import { useRules } from "@/composables/rules.js";
import { EnvironmentType } from "@/types/EnvironmentType.ts";

const props = defineProps<{
  title: string;
  icon: string;
  networkGroup?: NetworkGroup;
}>();

const ariaLabel = props.networkGroup
  ? `Netzwerkgruppe ${props.networkGroup.name} bearbeiten`
  : "Neue Netzwerkgruppe hinzufügen";
const form = ref<HTMLFormElement>();
const rules = useRules();
const dialog = ref(false);
const networkGroupTmp = ref<NetworkGroup>({
  id: props.networkGroup?.id || undefined,
  name: props.networkGroup?.name !== undefined ? props.networkGroup.name : "",
  application: props.networkGroup?.application || false,
  database: props.networkGroup?.database || false,
  storage: props.networkGroup?.storage || false,
  environment: props.networkGroup?.environment || EnvironmentType.C,
});
const selectedType = ref<"application" | "database" | "storage" | null>(null);

const emit = defineEmits<{
  (e: "save", networkGroup: NetworkGroup): void;
}>();

onMounted(() => {
  if (props.networkGroup) {
    if (props.networkGroup.application) selectedType.value = "application";
    else if (props.networkGroup.database) selectedType.value = "database";
    else if (props.networkGroup.storage) selectedType.value = "storage";
  }
});

function onTypeChange(type: "application" | "database" | "storage") {
  networkGroupTmp.value.application = type === "application";
  networkGroupTmp.value.database = type === "database";
  networkGroupTmp.value.storage = type === "storage";
}

function close() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  networkGroupTmp.value = {
    id: undefined,
    name: "",
    application: false,
    database: false,
    storage: false,
    environment: EnvironmentType.C,
  };
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emit("save", networkGroupTmp.value);
      dialog.value = false;
      resetForm();
    }
  });
}
</script>
