<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col>
        <v-autocomplete
          label="Anwendungsservice*"
          v-model="ldblOrder.appservice"
          v-model:search="search"
          :items="applicationServices"
          item-title="name"
          :loading="loading"
          return-object
          rounded
          clearable
          variant="outlined"
          @update:search="onSearchUpdate"
          :rules="[
            rules.notEmptySelectRule(
              'Ein Anwendungsservice muss ausgewählt werden'
            ),
            hasServers ||
              'Der ausgewählte Anwendungsservice hat keine zugewiesenen Server',
          ]"
          class="mt-2"
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
    <v-row>
      <v-col>
        <v-text-field
          v-model="dns"
          label="DNS Eintrag*"
          rounded
          variant="outlined"
          :rules="[
            rules.notEmptyRule('Ein DNS Eintrag muss angegeben werden'),
            rules.minLengthRule(
              2,
              'Der DNS Eintrag muss mindestens 2 Zeichen lang sein'
            ),
            rules.maxLengthRule(
              64,
              'Der DNS Eintrag darf maximal 64 Zeichen lang sein'
            ),
            rules.regexRule(
              /^[a-z0-9-]+$/,
              'Der DNS Eintrag darf nur Kleinbuchstaben, Zahlen und Bindestriche enthalten'
            ),
            dnsAvailable == true,
          ]"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <CommonAlert type="info">
          Vorraussichtlicher DNS Eintrag:
          <v-progress-circular
            indeterminate
            v-if="dnsLoading"
            size="16"
          />
          <strong v-else>{{ expectedDnsEntry }}</strong>
        </CommonAlert>
      </v-col>
    </v-row>
  </v-form>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList.ts";
import type LoadbalancerOrder from "@/types/LoadbalancerOrder.ts";

import { nextTick, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService.ts";
import infobloxFQDNService from "@/api/infobloxFQDNService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";
import { useRules } from "@/composables/rules.ts";

const props = defineProps<{
  ldblOrder: LoadbalancerOrder;
  hasServers: boolean;
}>();
const rules = useRules();
const isValid = ref(false);
const emit = defineEmits(["validation-change"]);
const applicationServices = ref<AppserviceList[]>([]);
const loading = ref(false);
const search = ref("");
const offset = ref(0);
const limit = 50;
const hasMore = ref(true);
const expectedDnsEntry = ref("");
const dnsLoading = ref(false);
const debounceTimer = ref<number | null>(null);
const DEBOUNCE_MS = 500;
const dnsAvailable = ref(true);
const dns = ref("");

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
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
  if (props.ldblOrder.appservice && val === props.ldblOrder.appservice.name) {
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

function debouncedGenerateDnsEntry() {
  if (debounceTimer.value !== null) {
    clearTimeout(debounceTimer.value);
  }
  debounceTimer.value = window.setTimeout(() => {
    generateDnsEntry();
    debounceTimer.value = null;
  }, DEBOUNCE_MS);
}

function generateDnsEntry() {
  nextTick(() => {
    if (!dns.value || !props.ldblOrder.appservice) {
      expectedDnsEntry.value = "";
      return;
    }

    const dnsName = dns.value.trim();

    // Validate format before calling backend
    if (dnsName.length < 3 || dnsName.length > 64) {
      expectedDnsEntry.value = "";
      return;
    }

    if (!/^[a-z0-9-]+$/.test(dnsName)) {
      expectedDnsEntry.value = "";
      return;
    }

    expectedDnsEntry.value = "";

    infobloxFQDNService
      .getFreeDnsEntry(dnsLoading, dnsName, props.ldblOrder.appservice.id)
      .then((responseDnsEntry) => {
        expectedDnsEntry.value = responseDnsEntry;
        props.ldblOrder.dns = responseDnsEntry;
      })
      .catch(() => {
        expectedDnsEntry.value =
          "Fehler bei der DNS-Abfrage / DNS Eintrag bereits vergeben";
      });
  });
}

watch(
  () => [dns.value, props.ldblOrder.appservice],
  ([dns, appservice]) => {
    if (dns && appservice) {
      debouncedGenerateDnsEntry();
    } else {
      expectedDnsEntry.value = "";
    }
  }
);
</script>
