<!-- IntegrationSettings.vue -->
<template>
  <integration-setting
    :title="'AWX'"
    :configs="awxConfigs"
    @update-table="getTableData"
  />
  <integration-setting
    :title="'ServiceNow'"
    :configs="snowConfigs"
    @update-table="getTableData"
  />
  <integration-setting
    :title="'Infoblox'"
    :configs="infobloxConfigs"
    @update-table="getTableData"
  />
  <integration-setting
    :title="'Backup Systeme'"
    :configs="baasConfigs"
    @update-table="getTableData"
  />
  <integration-setting
    :title="'Clouds'"
    :configs="clouds"
    @update-table="getTableData"
  />
</template>

<script setup lang="ts">
import type { AwxConfig } from "@/types/AwxConfig";
import type { BaasConfig } from "@/types/BaasConfig";
import type { Cloud } from "@/types/Cloud";
import type { InfobloxConfig } from "@/types/InfobloxConfig";
import type { SnowConfig } from "@/types/SnowConfig";

import { onMounted, ref } from "vue";

import awxConfigService from "@/api/awxConfigService";
import baasConfigService from "@/api/baasConfigService";
import cloudService from "@/api/cloudService";
import infobloxConfigService from "@/api/infobloxConfigService";
import snowConfigService from "@/api/snowConfigService";
import IntegrationSetting from "@/components/Settings/IntegrationSetting.vue";

// AWX
const awxConfigs = ref<AwxConfig[]>([]);
const loadingAwxConfig = ref(false);

function getAwxConfigs() {
  awxConfigService.getAwxConfigs(loadingAwxConfig).then((res) => {
    awxConfigs.value = res.map((item) => ({ ...item, type: "awx" }));
  });
}

// SNOW
const snowConfigs = ref<SnowConfig[]>([]);
const loadingSnowConfig = ref(false);

function getSnowConfigs() {
  snowConfigService.getSnowConfigs(loadingSnowConfig).then((res) => {
    snowConfigs.value = res.map((item) => ({ ...item, type: "snow" }));
  });
}

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

// Cloud
const clouds = ref<Cloud[]>([]);
const loadingClouds = ref(false);

function getClouds() {
  cloudService.getClouds(loadingClouds).then((res) => {
    clouds.value = res.map((item) => ({ ...item, type: "cloud" }));
  });
}

onMounted(() => {
  getTableData();
});

function getTableData() {
  getInfobloxConfigs();
  getAwxConfigs();
  getSnowConfigs();
  getBaasConfigs();
  getClouds();
}
</script>
