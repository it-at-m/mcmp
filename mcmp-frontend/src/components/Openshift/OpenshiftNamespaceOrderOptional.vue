<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col cols="11">
        <v-select
          v-model="order.logging"
          label="Logging"
          :items="loggingItems"
          item-title="title"
          item-value="value"
          rounded
          variant="outlined"
          class="mt-2"
          :menu-props="{ closeOnContentClick: true }"
          :rules="[rules.notEmptySelectRule('Logging muss ausgewählt werden')]"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="5"
          class="links_inverted"
        >
          <p>
            Ohne JSON-Parsing werden die Log-Nachrichten aus OpenShift als
            reiner Text an Graylog übertragen. Die einzelnen Informationen
            werden dabei nicht automatisch in strukturierte Felder aufgeteilt
            <br />
            <br />
            JSON-Parsing beim Logging in OpenShift nach Graylog bedeutet, dass
            Log-Nachrichten, die aus OpenShift kommen, als strukturierte
            JSON-Daten verarbeitet werden.
            <br />
            Dadurch können einzelne Informationen wie Zeitstempel, Log-Level,
            Service oder Fehlermeldungen in Graylog automatisch erkannt und als
            eigene Felder gespeichert werden. Das erleichtert die Suche,
            Filterung und Auswertung der Logs
            <br />
            <a
              href="https://git.muenchen.de/openshift/openshift-configs/-/wikis/Logging#onboarding-cluster-logging-umstellung-auf-graylog"
              target="_blank"
              >weitere Infos</a
            >
          </p>
        </inline-tooltip>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="11">
        <v-text-field
          v-model="order.quayOrga"
          label="Quay Orga (ohne cap- Präfix)"
          rounded
          variant="outlined"
          :rules="[
            rules.maxLengthRule(
              1024,
              'Die Quay Orga darf maximal 1024 Zeichen lang sein'
            ),
            quayOrgaFormatRule,
          ]"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="3"
          class="links_inverted"
        >
          <p>
            Die Quay-Registry wird zur cluster- und namespaceübergreifenden
            Imageablage bereitgestellt.<br />
            Jedes Projekt hat Schreib- und Leserechte auf die eigene
            Organisation in der Quay.
            <br />
            <a
              href="https://git.muenchen.de/openshift/openshift-configs/-/wikis/quay#images-aus-cap4v1k-kopieren"
              target="_blank"
              >weitere Infos</a
            >
          </p>
        </inline-tooltip>
      </v-col>
    </v-row>
  </v-form>
</template>

<script setup lang="ts">
import type OpenshiftNamespaceOrder from "@/types/OpenshiftNamespaceOrder.ts";

import { ref, watch } from "vue";

import InlineTooltip from "@/components/common/InlineTooltip.vue";
import { useRules } from "@/composables/rules.ts";

const order = defineModel<OpenshiftNamespaceOrder>("order", { required: true });
const rules = useRules();
const isValid = ref(false);
const emit = defineEmits(["validation-change"]);

function quayOrgaFormatRule(value: string | null | undefined) {
  if (!value) return true;
  return (
    /^[a-z0-9-]+$/.test(value) ||
    "Nur Kleinbuchstaben, Zahlen und Bindestriche erlaubt"
  );
}

const loggingItems = [
  { title: "Nein", value: "no" },
  { title: "Ja", value: "yes" },
  { title: "Mit JSON Parsing", value: "jsonparsing" },
];

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});
</script>
