<template>
  <div>
    <v-form ref="form">
      <!-- Cluster Checkbox statt Präfix-Dropdown -->
      <v-checkbox
        v-if="instlServerDetails.category?.allowedPrefixes.length > 0"
        v-model="useClusterPrefix"
        label="Server wird Teil eines DB-Clusters"
        @change="onClusterToggle"
      />

      <v-text-field
        v-model="instlServerDetails.serverName!.application"
        label="Applikationsname*"
        clearable
        :rules="nameRule"
        rounded
        @input="debouncedGenerateServerName()"
      />

      <v-checkbox
        v-model="changeNumbers"
        label="Servernummerierung anpassen?"
      />
      <v-number-input
        v-if="changeNumbers"
        v-model="instlServerDetails.serverName!.customNumber"
        :min="1"
        :max="999"
        hint="Erlaubte Werte 1 bis 999"
        persistent-hint
        control-variant="split"
        @update:model-value="debouncedGenerateServerName()"
      >
      </v-number-input>

      <strong>Domäne*</strong>
      <v-radio-group
        v-model="instlServerDetails.serverName!.domain"
        inline
        :rules="domainRule"
      >
        <v-radio
          v-for="domain in domains"
          :key="domain"
          :label="domain"
          :value="domain"
          @change="generateServerName()"
        />
      </v-radio-group>

      <strong>Voraussichtlicher Servername</strong>
      <br />
      <v-progress-circular
        v-if="loading"
        indeterminate
      />
      <span>{{ instlServerDetails.expectedServerName }}</span>
    </v-form>
  </div>
</template>

<script setup lang="ts">
import type { ServerCategoryType } from "@/types/ServerTypes.ts";

import { computed, nextTick, onMounted, ref, watch } from "vue";

import infobloxFQDNService from "@/api/infobloxFQDNService.ts";
import { useRules } from "@/composables/rules";
import installServerDetails, {
  categoryType,
  OperatingSystem,
} from "@/types/installServerDetails.ts";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();

const changeNumbers = ref(false);
const useClusterPrefix = ref(false); // neue Checkbox
const domains = ["srv.muenchen.de"];
const form = ref<HTMLFormElement>();
const loading = ref(false);
const debounceTimer = ref<number | null>(null);
const DEBOUNCE_MS = 500;

const validationRules = useRules();

function computeMaxLength(): number {
  const prefix = props.instlServerDetails.serverName?.prefix ?? "";
  const osVersion = props.instlServerDetails.osVersion ?? "";
  let allowedAppLength = 12;

  // Windows Server names can be max 15 chars long
  if (OperatingSystem.Windows.includes(osVersion)) {
    allowedAppLength = 9;
  }

  return allowedAppLength - prefix.length;
}

const nameRule = computed(() => [
  validationRules.minLengthRule(
    3,
    "Der Name muss mindestens 3 Zeichen lang sein."
  ),
  validationRules.maxLengthRule(
    computeMaxLength(),
    `Der Name darf maximal ${computeMaxLength()} Zeichen lang sein.`
  ),
  validationRules.notEmptyRule("Der Name darf nicht leer sein."),
  validationRules.regexRule(
    /^[a-z][a-z0-9]+$/,
    "Der Name muss mit einem Kleinbuchstaben anfangen und darf nur aus Kleinbuchstaben und Zahlen bestehen."
  ),
]);

const domainRule = [
  validationRules.notEmptyRule("Es muss eine Domäne ausgewählt werden."),
];

function debouncedGenerateServerName() {
  if (debounceTimer.value !== null) {
    clearTimeout(debounceTimer.value);
  }
  debounceTimer.value = window.setTimeout(() => {
    generateServerName();
    debounceTimer.value = null;
  }, DEBOUNCE_MS);
}

function generateServerName() {
  nextTick(() => {
    form.value?.validate().then((validation: { valid: boolean }) => {
      if (validation.valid) {
        if (props.instlServerDetails.serverName == null) {
          console.error("serverName is null, that should not be possible.");
          return;
        }
        if (props.instlServerDetails.osVersion == null) {
          return;
        }
        props.instlServerDetails.expectedServerName = "";

        let serverType = props.instlServerDetails.serverName.serverType;
        if (
          props.instlServerDetails.category &&
          "kenner" in props.instlServerDetails.category &&
          !("appCategorys" in props.instlServerDetails.category)
        ) {
          const category = props.instlServerDetails
            .category as ServerCategoryType;
          if (category.kenner) {
            serverType = serverType + category.kenner;
          }
        }

        // set Os prefix only if no kenner is set
        if (serverType.length == 0) {
          if (
            OperatingSystem.Linux.includes(props.instlServerDetails.osVersion)
          ) {
            serverType = serverType + "lx";
          } else if (
            OperatingSystem.Windows.includes(props.instlServerDetails.osVersion)
          ) {
            serverType = serverType + "wi";
          } else {
            return;
          }
        }

        infobloxFQDNService
          .getFreeServerFQDN(
            loading,
            props.instlServerDetails.serverName.prefix,
            props.instlServerDetails.serverName.application,
            serverType,
            props.instlServerDetails.appservice!.id,
            props.instlServerDetails.serverName.domain,
            props.instlServerDetails.serverName.customNumber
          )
          .then((responseFQDN) => {
            props.instlServerDetails.expectedServerName = responseFQDN;
          });
      }
    });
  });
}

// Handler für die Cluster-Checkbox
function onClusterToggle() {
  props.instlServerDetails.serverName!.prefix = useClusterPrefix.value
    ? "cn-"
    : "";
  debouncedGenerateServerName();
}

onMounted(() => {
  // set srv.muenchen.de as default domain
  if (!props.instlServerDetails.serverName?.domain) {
    props.instlServerDetails.serverName!.domain = "srv.muenchen.de";
  }
});

watch(
  () => [
    props.instlServerDetails.appservice,
    props.instlServerDetails.osType,
    props.instlServerDetails.osVersion,
    props.instlServerDetails.categoryType,
    props.instlServerDetails.category,
  ],
  ([appservice, osType, osVersion, categoryTypeInst, category]) => {
    props.instlServerDetails.serverName!.prefix = "";
    useClusterPrefix.value = false;
    if (appservice && osType && osVersion && categoryTypeInst && category) {
      generateServerName();
    } else if (
      appservice &&
      osType &&
      osVersion &&
      categoryTypeInst == categoryType.Standard
    ) {
      generateServerName();
    } else {
      props.instlServerDetails.expectedServerName = "";
    }
  }
);
</script>
