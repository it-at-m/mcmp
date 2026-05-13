<template>
  <common-card
    :title="title"
    :topMargin="title == 'AWX' ? '4' : '0'"
  >
    <template #toolbar-actions>
      <v-btn
        :icon="mdiPlus"
        @click="addItem(title)"
        :aria-label="'Neuen ' + title + ' Eintrag hinzufügen'"
      />
    </template>
    <v-data-table
      :headers="
        title == 'Infoblox' || title == 'Clouds'
          ? specialHeaders[title]
          : headers
      "
      :items="configsLocal"
      :items-per-page="-1"
      hide-default-footer
    >
      <template v-slot:[`item.enabled`]="{ item }">
        <v-chip
          v-if="hasEnabled(item)"
          :color="item.enabled ? '_green' : '_red'"
          dark
        >
          {{ item.enabled ? "Aktiv" : "Inaktiv" }}
        </v-chip>
      </template>

      <template v-slot:[`item.edit`]="{ item }">
        <v-btn
          :icon="mdiPencil"
          @click="editItem(item)"
          :aria-label="`Eintrag ${item.apiDescription} bearbeiten`"
        />
        <v-btn
          :icon="mdiDelete"
          @click="askDeleteItem(item)"
          :aria-label="`Eintrag ${item.apiDescription} löschen`"
        />
      </template>
    </v-data-table>
  </common-card>
  <v-dialog
    v-model="editDialog"
    max-width="500px"
  >
    <integration-setting-edit
      :toEdit="toEdit"
      @editDialog="(dialog: boolean) => (editDialog = dialog)"
      @edit="(item: Config) => updateItem(item, 'edit')"
    />
  </v-dialog>
  <v-dialog
    v-model="deleteDialog"
    max-width="500px"
  >
    <v-card>
      <v-card-title>{{ toEdit.apiDescription }}</v-card-title>
      <v-card-text>Wollen sie den Eintrag wirklich löschen?</v-card-text>
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
          @click="deleteItem"
          >Bestätigen</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type { AwxConfig } from "@/types/AwxConfig";
import type { BaasConfig } from "@/types/BaasConfig";
import type { Cloud } from "@/types/Cloud";
import type { InfobloxConfig } from "@/types/InfobloxConfig";
import type { SnowConfig } from "@/types/SnowConfig";

import { mdiDelete, mdiPencil, mdiPlus } from "@mdi/js";
import { ref, watch } from "vue";

import awxConfigService from "@/api/awxConfigService";
import baasConfigService from "@/api/baasConfigService";
import cloudService from "@/api/cloudService";
import infobloxConfigService from "@/api/infobloxConfigService";
import snowConfigService from "@/api/snowConfigService";
import CommonCard from "@/components/common/CommonCard.vue";
import IntegrationSettingEdit from "@/components/Settings/IntegrationSettingEdit.vue";

export type Config =
  | SnowConfig
  | AwxConfig
  | InfobloxConfig
  | BaasConfig
  | Cloud;

const props = defineProps<{
  title: string;
  configs: Config[];
}>();

const emit = defineEmits<(e: "updateTable", updateTable: boolean) => void>();

const headers = [
  { title: "Beschreibung", key: "apiDescription" },
  { title: "Status", key: "enabled", align: "center" },
  { title: "Bearbeiten/Löschen", key: "edit", sortable: false, align: "end" },
] as const;

const specialHeaders = {
  Infoblox: [
    { title: "Beschreibung", key: "apiDescription" },
    { title: "Bearbeiten/Löschen", key: "edit", sortable: false, align: "end" },
  ],
  Clouds: [
    { title: "Beschreibung", key: "apiDescription" },
    { title: "Cloud Type", key: "cloudType" },
    { title: "Status", key: "enabled", align: "center" },
    { title: "Bearbeiten/Löschen", key: "edit", sortable: false, align: "end" },
  ],
} as const;

const editDialog = ref(false);
const deleteDialog = ref(false);
const toEdit = ref<Config>({} as Config);
const configsLocal = ref<Config[]>([...props.configs]);

watch(
  () => props.configs,
  (value) => {
    configsLocal.value = [...value];
  },
  { deep: true, immediate: true }
);

function hasEnabled(item: Config) {
  return "enabled" in item;
}

function editItem(item: Config) {
  editDialog.value = true;
  toEdit.value = item;
}

function askDeleteItem(item: Config) {
  deleteDialog.value = true;
  toEdit.value = item;
}

function addItem(title: string) {
  editDialog.value = true;

  if (title == "ServiceNow") {
    toEdit.value = {
      id: undefined,
      apiDescription: "",
      apiClientAuthUrl: "",
      apiClientId: "",
      apiClientSecret: "",
      apiEndpoint: "",
      enabled: false,
      type: "snow",
      proxy: "",
      useProxy: false,
    } as unknown as SnowConfig;
  }
  if (title == "Infoblox") {
    toEdit.value = {
      id: undefined,
      apiDescription: "",
      apiUsername: "",
      apiPassword: "",
      apiEndpoint: "",
      type: "infoblox",
    } as unknown as InfobloxConfig;
  }
  if (title == "AWX") {
    toEdit.value = {
      id: undefined,
      apiDescription: "",
      apiUsername: "",
      apiPassword: "",
      apiEndpoint: "",
      enabled: false,
      type: "awx",
    } as unknown as AwxConfig;
  }
  if (title == "Backup Systeme") {
    toEdit.value = {
      id: undefined,
      apiDescription: "",
      apiEndpoint: "",
      enabled: false,
      type: "baas",
    } as unknown as BaasConfig;
  }
  if (title == "Clouds") {
    toEdit.value = {
      id: undefined,
      apiDescription: "",
      apiUsername: "",
      apiPassword: "",
      apiEndpoint: "",
      enabled: false,
      type: "cloud",
      name: "",
      fqdn: "",
      serverGui: "",
      cloudType: "",
      locked: false,
      configInfobloxId: undefined,
      configBaasId: undefined,
    } as unknown as Cloud;
  }
}

function deleteItem() {
  updateItem(toEdit.value, "delete");
  deleteDialog.value = false;
}

async function updateItem(updatedItem: Config, action: "edit" | "delete") {
  const loading = ref(false);
  const index = configsLocal.value.findIndex((i) => i.id == updatedItem.id);

  try {
    if (updatedItem.type == "snow") {
      if (action == "edit") {
        if (index != -1) {
          configsLocal.value[index] = { ...updatedItem };
          await snowConfigService.updateConfig(
            updatedItem as SnowConfig,
            loading
          );
        } else {
          configsLocal.value.push({ ...updatedItem });
          await snowConfigService.createConfig(
            updatedItem as SnowConfig,
            loading
          );
        }
      }
      if (action == "delete") {
        configsLocal.value.splice(index, 1);
        await snowConfigService.deleteConfig(updatedItem.id, loading);
      }
    }
    if (updatedItem.type == "infoblox") {
      if (action == "edit") {
        if (index != -1) {
          configsLocal.value[index] = { ...updatedItem };
          await infobloxConfigService.updateConfig(
            updatedItem as InfobloxConfig,
            loading
          );
        } else {
          configsLocal.value.push({ ...updatedItem });
          await infobloxConfigService.createConfig(
            updatedItem as InfobloxConfig,
            loading
          );
        }
      }
      if (action == "delete") {
        configsLocal.value.splice(index, 1);
        await infobloxConfigService.deleteConfig(updatedItem.id, loading);
      }
    }
    if (updatedItem.type == "awx") {
      if (action == "edit") {
        if (index != -1) {
          configsLocal.value[index] = { ...updatedItem };
          await awxConfigService.updateConfig(
            updatedItem as AwxConfig,
            loading
          );
        } else {
          configsLocal.value.push({ ...updatedItem });
          await awxConfigService.createConfig(
            updatedItem as AwxConfig,
            loading
          );
        }
      }
      if (action == "delete") {
        configsLocal.value.splice(index, 1);
        await awxConfigService.deleteConfig(updatedItem.id, loading);
      }
    }
    if (updatedItem.type == "baas") {
      if (action == "edit") {
        if (index != -1) {
          configsLocal.value[index] = { ...updatedItem };
          await baasConfigService.updateConfig(
            updatedItem as BaasConfig,
            loading
          );
        } else {
          configsLocal.value.push({ ...updatedItem });
          await baasConfigService.createConfig(
            updatedItem as BaasConfig,
            loading
          );
        }
      }
      if (action == "delete") {
        configsLocal.value.splice(index, 1);
        await baasConfigService.deleteConfig(updatedItem.id, loading);
      }
    }
    if (updatedItem.type == "cloud") {
      if (action == "edit") {
        if (index != -1) {
          configsLocal.value[index] = { ...updatedItem };
          await cloudService.updateConfig(updatedItem as Cloud, loading);
        } else {
          configsLocal.value.push({ ...updatedItem });
          await cloudService.createConfig(updatedItem as Cloud, loading);
        }
      }
      if (action == "delete") {
        configsLocal.value.splice(index, 1);
        await cloudService.deleteConfig(updatedItem.id, loading);
      }
    }
  } catch (err) {
    console.debug(err);
  } finally {
    loading.value = false;
    emit("updateTable", true);
  }
}
</script>
