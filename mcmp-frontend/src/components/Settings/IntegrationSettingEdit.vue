<template>
  <v-card>
    <v-card-title>{{ generateTitle }}</v-card-title>
    <v-card-text>
      <v-form ref="form">
        <template v-if="toEditCopy?.type == 'cloud'">
          <strong>Cloud Type</strong>
          <v-autocomplete
            v-model="toEditCopy.cloudType"
            :items="['VMWARE', 'PROXMOX', 'UCS_MANAGER', 'UCS_CIMC', 'OLVM']"
            clearable
          />
        </template>

        <strong>Beschreibung</strong>
        <v-text-field
          v-model="toEditCopy.apiDescription"
          :rules="[
            validationRules.notEmptyRule(
              'Die Beschreibung darf nicht leer sein.'
            ),
          ]"
        />

        <template v-if="toEditCopy?.type == 'cloud'">
          <strong>Name</strong>
          <v-text-field v-model="toEditCopy.name" />
          <strong>FQDN</strong>
          <v-text-field
            v-model="toEditCopy.fqdn"
            :rules="[
              toEditCopy.id != undefined ||
                validationRules.notEmptyRule('Der FQDN darf nicht leer sein.'),
            ]"
          />
        </template>

        <template
          v-if="
            toEditCopy?.type == 'infoblox' ||
            toEditCopy?.type == 'awx' ||
            toEditCopy?.type == 'cloud'
          "
        >
          <strong>Loginname</strong>
          <v-text-field
            v-model="toEditCopy.apiUsername"
            :rules="[
              validationRules.notEmptyRule(
                'Der Loginname darf nicht leer sein.'
              ),
            ]"
          />

          <strong>Passwort</strong>
          <v-text-field
            v-model="toEditCopy.apiPassword"
            :rules="[
              toEditCopy.id != undefined ||
                validationRules.notEmptyRule(
                  'Das Passwort darf nicht leer sein.'
                ),
            ]"
            :placeholder="toEditCopy.id == undefined ? '' : '********'"
          />
        </template>

        <template v-if="toEditCopy?.type != 'cloud'">
          <strong>API Endpoint</strong>
          <v-text-field
            v-model="toEditCopy.apiEndpoint"
            :rules="[
              validationRules.notEmptyRule('Der Enpoint darf nicht leer sein.'),
            ]"
          />
        </template>

        <template v-if="toEditCopy?.type == 'snow'">
          <strong>Client Authentifizierungs URL</strong>
          <v-text-field
            v-model="toEditCopy.apiClientAuthUrl"
            :rules="[
              validationRules.notEmptyRule('Die URL darf nicht leer sein.'),
            ]"
          />

          <strong>Client ID</strong>
          <v-text-field
            v-model="toEditCopy.apiClientId"
            :rules="[
              validationRules.notEmptyRule('Die ID darf nicht leer sein.'),
            ]"
          />

          <strong>Secret</strong>
          <v-text-field
            v-model="toEditCopy.apiClientSecret"
            :rules="[
              toEditCopy.id != undefined ||
                validationRules.notEmptyRule(
                  'Das Secret darf nicht leer sein.'
                ),
            ]"
            :placeholder="toEditCopy.id == undefined ? '' : '********'"
          />

          <strong>Proxy</strong>
          <v-text-field v-model="toEditCopy.proxy" />
          <strong>Proxy Verwenden</strong>
          <v-switch
            v-model="toEditCopy.useProxy"
            :color="toEditCopy.useProxy ? '_green' : '_red'"
          />
        </template>

        <template v-if="toEditCopy?.type == 'cloud'">
          <strong>Infoblox</strong>
          <v-autocomplete
            v-model="toEditCopy.configInfobloxId"
            :items="infobloxConfigs"
            item-title="apiDescription"
            item-value="id"
            :loading="loadingInfobloxConfig"
            clearable
            @update:model-value="getInfobloxConfigs"
          />

          <strong>Backup System</strong>
          <v-autocomplete
            v-model="toEditCopy.configBaasId"
            :items="baasConfigs"
            item-title="apiDescription"
            item-value="id"
            :loading="loadingBaasConfig"
            clearable
            @update:model-value="getBaasConfigs"
          />

          <strong>Server bearbeitbar</strong>
          <v-switch
            v-model="toEditCopy.locked"
            :color="toEditCopy.locked ? '_green' : '_red'"
          />
        </template>

        <template v-if="toEditCopy?.type != 'infoblox'">
          <strong>Status</strong>
          <v-switch
            v-model="toEditCopy.enabled"
            :color="toEditCopy.enabled ? '_green' : '_red'"
          />
        </template>
      </v-form>
    </v-card-text>
    <v-card-actions>
      <v-spacer />
      <v-btn
        color="cancel"
        text
        @click="cancelEdit"
        >Abbrechen</v-btn
      >
      <v-btn
        variant="flat"
        color="do"
        @click="saveEdit"
        >Speichern</v-btn
      >
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import type { AwxConfig } from "@/types/AwxConfig";
import type { BaasConfig } from "@/types/BaasConfig";
import type { Cloud } from "@/types/Cloud";
import type { InfobloxConfig } from "@/types/InfobloxConfig";
import type { SnowConfig } from "@/types/SnowConfig";

import { computed, onMounted, ref, toRaw } from "vue";

import baasConfigService from "@/api/baasConfigService";
import infobloxConfigService from "@/api/infobloxConfigService";
import { useRules } from "@/composables/rules";

export type Config =
  SnowConfig | AwxConfig | InfobloxConfig | BaasConfig | Cloud;

const props = defineProps<{
  toEdit: Config;
}>();

const emit = defineEmits<{
  (e: "editDialog", dialog: boolean): void;
  (e: "edit", edit: Config): void;
}>();

const toEditCopy = ref<Config>({ ...toRaw(props.toEdit) });
const validationRules = useRules();
const form = ref<HTMLFormElement>();

// Infoblox
const infobloxConfigs = ref<InfobloxConfig[]>([]);
const loadingInfobloxConfig = ref(false);

function getInfobloxConfigs() {
  infobloxConfigService
    .getInfobloxConfigs(loadingInfobloxConfig)
    .then((res) => {
      infobloxConfigs.value = res.map((item) => ({
        ...item,
        type: "infoblox",
      }));
    });
}

// Baas
const baasConfigs = ref<BaasConfig[]>([]);
const loadingBaasConfig = ref(false);

function getBaasConfigs() {
  baasConfigService.getBaasConfigs(loadingBaasConfig).then((res) => {
    baasConfigs.value = res.map((item) => ({ ...item, type: "baas" }));
  });
}

function saveEdit() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emit("edit", toEditCopy.value);
      emit("editDialog", false);
      form.value?.resetValidation();
    }
  });
}

function cancelEdit() {
  form.value?.resetValidation();
  emit("editDialog", false);
}

const generateTitle = computed(() => {
  if (toEditCopy.value.id == undefined) {
    if (toEditCopy.value.type == "infoblox") {
      return "Neue Infoblox Integration";
    } else if (toEditCopy.value.type == "awx") {
      return "Neue AWX Integration";
    } else if (toEditCopy.value.type == "snow") {
      return "Neue ServiceNow Integration";
    } else if (toEditCopy.value.type == "baas") {
      return "Neue Backup Integration";
    } else if (toEditCopy.value.type == "cloud") {
      return "Neue Cloud Integration";
    }
    return;
  } else {
    return toEditCopy.value.apiDescription + " bearbeiten";
  }
});

onMounted(async () => {
  if (toEditCopy.value.type == "cloud") {
    getInfobloxConfigs();
    getBaasConfigs();
  }
});
</script>
