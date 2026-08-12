<template>
  <div>
    <!-- v-checkbox
      label="VM soll automatisiert zu einem bestimmten Termin wieder abgebaut werden (max. 2 Wochen in der Zukunft)"
      v-model="instlServerDetails.schedule"
    >
    </v-checkbox -->
    <strong>Anwendungsservice*</strong>
    <v-autocomplete
      v-model="selectedAppService"
      v-model:search="search"
      :items="applicationServices"
      item-title="name"
      :loading="loading"
      return-object
      rounded
      clearable
      variant="outlined"
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

    <strong>Betriebssystem*</strong>
    <v-radio-group
      v-model="instlServerDetails.osType"
      aria-label="Betriebssystem auswählen"
      inline
      @change="
        instlServerDetails.osVersion = null;
        instlServerDetails.categoryType = null;
        instlServerDetails.category = null;
      "
    >
      <v-radio
        v-for="os in OsType"
        :key="os"
        :value="os"
        :label="os"
      />
    </v-radio-group>

    <strong v-if="instlServerDetails.osType != null"
      >Betriebssystem Version*</strong
    >
    <v-radio-group
      v-if="instlServerDetails.osType != null"
      v-model="instlServerDetails.osVersion"
      aria-label="Betriebssystem Version auswählen"
      inline
      @change="
        instlServerDetails.categoryType = null;
        instlServerDetails.category = null;
      "
    >
      <v-radio
        v-for="os in OperatingSystem[
          instlServerDetails.osType as keyof typeof OperatingSystem
        ]"
        :key="os"
        :label="os"
        :value="os"
      />
    </v-radio-group>

    <strong v-if="instlServerDetails.osVersion != null">Server Typ*</strong>
    <v-radio-group
      v-if="instlServerDetails.osVersion != null"
      v-model="instlServerDetails.categoryType"
      aria-label="Server Typ auswählen"
      inline
      @change="instlServerDetails.category = null"
    >
      <template
        v-for="type in Object.values(categoryType)"
        :key="type"
      >
        <v-radio
          v-if="type === categoryType.Standard || wouldHaveOptions(type)"
          :label="categoryTypeLabels[type] || type"
          :value="type"
        />
      </template>
    </v-radio-group>

    <strong v-if="serverCategoryOptions.length > 0"
      >Anwendung/Datenbank*</strong
    >
    <v-select
      v-if="serverCategoryOptions.length > 0"
      v-model="instlServerDetails.category"
      :items="serverCategoryOptions"
      item-title="label"
      return-object
      rounded
      variant="outlined"
      class="mt-2"
      :menu-props="{ persistent: true, closeOnContentClick: true }"
    />
    <common-alert
      v-if="
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
      color="_red"
      >Standard ist PostgreSQL. Sie haben mit
      {{ instlServerDetails.category?.label }} ein alternatives DB-System
      ausgewählt.</common-alert
    >

    <strong
      v-if="
        instlServerDetails.osVersion == OsVersion.RHEL10 &&
        instlServerDetails.categoryType != null &&
        instlServerDetails.categoryType != categoryType.DB
      "
      >Middleware User</strong
    >
    <v-checkbox
      v-if="
        instlServerDetails.osVersion == OsVersion.RHEL10 &&
        instlServerDetails.categoryType != null &&
        instlServerDetails.categoryType != categoryType.DB
      "
      v-model="instlServerDetails.middlewareUser"
      label="Middleware User anlegen"
    ></v-checkbox>
    <v-radio-group
      v-if="
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
      v-model="nonPostgresOption"
      class="mt-6"
    >
      <v-radio
        label="Lifecycle-Maßnahme (Upgrade, Migration, technische Fortführung)"
        value="lcm"
      />

      <v-radio
        label="Neue Datenbank / neues Fachverfahren"
        value="newDb"
      />
    </v-radio-group>
    <strong
      v-if="
        nonPostgresOption !== null &&
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
    >
      Fachliche Einordnung: <br />
    </strong>
    <span
      v-if="
        nonPostgresOption !== null &&
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
      class="fontsize-085"
    >
      Kurze Begründung für den Einsatz eines anderen Datenbanksystems als
      PostgreSQL. <br />Bitte angeben, warum PostgreSQL nicht eingesetzt werden
      kann und ob Alternativen geprüft wurden. <br />
    </span>
    <v-textarea
      v-if="
        nonPostgresOption !== null &&
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
      v-model="nonPostgresReasonInput"
      label=""
      rows="3"
      rounded
      outlined
      :rules="useRules().getNonPostgresReasonRules()"
    ></v-textarea>
    <strong
      v-if="
        nonPostgresOption !== null &&
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
    >
      Wie geht es nach dem Absenden weiter?</strong
    >
    <div
      v-if="
        nonPostgresOption !== null &&
        (instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
        instlServerDetails.category != null &&
        !instlServerDetails.category?.label.match(/PostgreSQL/)
      "
    >
      <ul>
        <li class="fontsize-085">
          - Ihre Angaben werden automatisch an
          <v-tooltip location="top">
            <template #activator="{ props }">
              <strong v-bind="props">ITA-DB</strong>
            </template>
            <span>IT-Architektur DB-Systeme</span>
          </v-tooltip>
          und
          <v-tooltip location="top">
            <template #activator="{ props }">
              <strong v-bind="props">OSPO</strong>
            </template>
            <span>Open Source Program Office</span>
          </v-tooltip>
          gesendet.
        </li>

        <li class="fontsize-085">
          - Bei neuen Datenbanken außerhalb von PostgreSQL kann eine sachliche
          Prüfung erfolgen
        </li>
      </ul>
    </div>

    <div class="text-caption text-grey-darken-1">* Pflichtfeld</div>
  </div>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList.ts";

import { computed, onMounted, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import { useRules } from "@/composables/rules.ts";
import installServerDetails, {
  categoryType,
  OperatingSystem,
  OsType,
  OsVersion,
} from "@/types/installServerDetails";
import { categorys, serverTypeMixes } from "@/types/ServerTypes";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
const applicationServices = ref<AppserviceList[]>([]);
const selectedAppService = ref<AppserviceList | null>(null);
const loading = ref(false);
const search = ref("");
const offset = ref(0);
const limit = 50;
const hasMore = ref(true);
const nonPostgresOption = ref<"lcm" | "newDb" | null>(null);

watch(selectedAppService, (newVal) => {
  if (newVal) {
    props.instlServerDetails.appservice = newVal;
  } else {
    props.instlServerDetails.appservice = null;
  }
});

const serverCategoryOptions = computed(() => {
  const { categoryType, osType, osVersion } = props.instlServerDetails;
  if (!categoryType || !osType || !osVersion) return [];

  const osKey = osType.toLowerCase() as keyof typeof categorys;

  if (categoryType === "App") {
    return Object.values(categorys[osKey]).filter(
      (cat) => cat.isApp && cat.osVersion.includes(osVersion)
    );
  }
  if (categoryType === "DB") {
    // set PostgreSQL as default when switching to DB category
    if (
      !props.instlServerDetails.category ||
      !props.instlServerDetails.category.osVersion.includes(osVersion)
    ) {
      const postgresCategory = Object.values(categorys[osKey]).find(
        (cat) =>
          cat.isDb &&
          cat.label === "PostgreSQL" &&
          cat.osVersion.includes(osVersion)
      );
      if (postgresCategory) {
        props.instlServerDetails.category = postgresCategory;
      }
    }
    // get all other db categories
    return Object.values(categorys[osKey]).filter(
      (cat) => cat.isDb && cat.osVersion.includes(osVersion)
    );
  }
  if (categoryType === "Mixed") {
    return serverTypeMixes.filter((mix) => mix.osVersion.includes(osVersion));
  }
  return [];
});

const categoryTypeLabels: Record<string, string> = {
  [categoryType.Standard]: "Standardserver",
  [categoryType.App]: "Applikationsserver",
  [categoryType.DB]: "Datenbankserver",
};

// // Bei Textanpassung von LCM_PREFIX muss die Variable auch in der rules.ts angepasst werden
const LCM_PREFIX =
  "Diese Installation findet im Rahmen des Lifecyclemanagements statt. ";

const nonPostgresReasonInput = computed({
  get() {
    const val = props.instlServerDetails.nonPostgresReason || "";

    if (nonPostgresOption.value === "lcm" && val.startsWith(LCM_PREFIX)) {
      return val.slice(LCM_PREFIX.length);
    }

    return val;
  },
  set(value: string) {
    if (nonPostgresOption.value === "lcm") {
      props.instlServerDetails.nonPostgresReason = LCM_PREFIX + value;
    } else {
      props.instlServerDetails.nonPostgresReason = value;
    }
  },
});

function wouldHaveOptions(catType: categoryType): boolean {
  const { osType, osVersion } = props.instlServerDetails;
  if (!osType || !osVersion) return false;

  const osKey = osType.toLowerCase() as keyof typeof categorys;

  if (catType === categoryType.App) {
    return Object.values(categorys[osKey]).some(
      (cat) => cat.isApp && cat.osVersion.includes(osVersion)
    );
  }
  if (catType === categoryType.DB) {
    return Object.values(categorys[osKey]).some(
      (cat) => cat.isDb && cat.osVersion.includes(osVersion)
    );
  }
  if (catType === categoryType.Mixed) {
    return serverTypeMixes.some((mix) => mix.osVersion.includes(osVersion));
  }
  return false;
}

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
  if (selectedAppService.value && val === selectedAppService.value.name) {
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

onMounted(async () => {
  getApplicationServiceClasses(true);
});

watch(nonPostgresOption, (newVal) => {
  if (newVal === "lcm") {
    if (!props.instlServerDetails.nonPostgresReason?.startsWith(LCM_PREFIX)) {
      props.instlServerDetails.nonPostgresReason =
        LCM_PREFIX + (props.instlServerDetails.nonPostgresReason || "");
    }
  } else if (newVal === "newDb") {
    if (props.instlServerDetails.nonPostgresReason?.startsWith(LCM_PREFIX)) {
      props.instlServerDetails.nonPostgresReason =
        props.instlServerDetails.nonPostgresReason.slice(LCM_PREFIX.length);
    }
  }
});
</script>

<style scoped>
.fontsize-085 {
  font-size: 0.85em;
}
</style>
