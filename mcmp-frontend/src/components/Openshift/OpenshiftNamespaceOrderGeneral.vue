<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col>
        <v-autocomplete
          v-model="order.appservice"
          v-model:search="search"
          label="Anwendungsservice*"
          :items="applicationServices"
          item-title="name"
          :loading="loading"
          return-object
          rounded
          clearable
          variant="outlined"
          :rules="[
            rules.notEmptySelectRule(
              'Ein Anwendungsservice muss ausgewählt werden'
            ),
          ]"
          class="mt-2"
          @update:search="onSearchUpdate"
        >
          <template #no-data
            ><a class="ml-2">Keine Anwendungsservices gefunden</a></template
          >
          <template #append-item>
            <div
              v-if="hasMore"
              v-intersect="onIntersect"
              class="pa-4 text-center"
            >
              <v-progress-circular
                indeterminate
                size="24"
                color="primary"
              />
            </div>
          </template>
        </v-autocomplete>
      </v-col>
    </v-row>
    <v-row v-if="order.appservice">
      <v-col>
        <common-alert type="info">
          Cluster: <strong>{{ clusterEnvironmentLabel }}</strong>
        </common-alert>
      </v-col>
    </v-row>
  </v-form>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList.ts";
import type OpenshiftNamespaceOrder from "@/types/OpenshiftNamespaceOrder.ts";

import { computed, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";
import { useRules } from "@/composables/rules.ts";
import { EnvironmentType } from "@/types/EnvironmentType.ts";

const order = defineModel<OpenshiftNamespaceOrder>("order", { required: true });
const rules = useRules();
const isValid = ref(false);
const emit = defineEmits(["validation-change"]);
const applicationServices = ref<AppserviceList[]>([]);
const loading = ref(false);
const search = ref("");
const offset = ref(0);
const limit = 50;
const hasMore = ref(true);

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});

const clusterEnvironmentLabel = computed(() => {
  const env = order.value.appservice?.environment;
  if (env === EnvironmentType.C || env === EnvironmentType.K) return "CAP-Test";
  if (env === EnvironmentType.P) return "CAP-Prod";
  return "-";
});

async function getApplicationServiceClasses(isNewSearch = false) {
  if (loading.value) return;

  if (isNewSearch) {
    offset.value = 0;
    applicationServices.value = [];
    hasMore.value = true;
  }

  loading.value = true;
  try {
    const res = await appserviceService.getAppservices(
      loading,
      offset.value,
      limit,
      "asc",
      search.value
    );

    const newItems = res.content;
    applicationServices.value.push(...newItems);
    offset.value += newItems.length;

    hasMore.value = newItems.length === limit;
  } finally {
    loading.value = false;
  }
}

function onSearchUpdate(val: string) {
  if (val === null) return;

  // Suche nicht auslösen, wenn der Suchwert dem bereits ausgewählten Service entspricht
  if (order.value.appservice && val === order.value.appservice.name) {
    return;
  }

  // Suche erst ab 2 Zeichen oder wenn das Feld geleert wird
  if (val.length >= 2 || val.length === 0) {
    getApplicationServiceClasses(true);
  }
}

function onIntersect(isIntersecting: boolean) {
  if (isIntersecting && hasMore.value && !loading.value) {
    getApplicationServiceClasses();
  }
}
</script>
