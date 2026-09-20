<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col>
        <v-text-field
          v-model="order.namespaceName"
          label="Namespace Name*"
          rounded
          variant="outlined"
          class="mt-2"
          :rules="[
            rules.notEmptyRule('Ein Namespace Name muss angegeben werden'),
            rules.minLengthRule(
              1,
              'Der Namespace Name muss mindestens 1 Zeichen lang sein'
            ),
            rules.maxLengthRule(
              64,
              'Der Namespace Name darf maximal 64 Zeichen lang sein'
            ),
            rules.regexRule(
              /^[a-z0-9-]+$/,
              'Nur Kleinbuchstaben, Zahlen und Bindestriche erlaubt'
            ),
          ]"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-textarea
          v-model="order.description"
          label="Projektbeschreibung"
          rounded
          variant="outlined"
          :rules="[
            rules.maxLengthRule(
              1024,
              'Die Projektbeschreibung darf maximal 1024 Zeichen lang sein'
            ),
          ]"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="11">
        <v-select
          v-model="order.nodeSelector"
          label="Node Selector*"
          :items="nodeSelectorItems"
          rounded
          variant="outlined"
          :menu-props="{ closeOnContentClick: true }"
          :rules="[
            rules.notEmptySelectRule(
              'Ein Node Selector muss ausgewählt werden'
            ),
          ]"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="3"
          class="links_inverted"
        >
          <p>
            worker (normale stadtweite Anwendung)
            <br />stargate (Ins Internet exponierte Anwendung) <br />holyplace
            (stadtweite Anwendung mit besonders schützenswerten Daten) <br />
            <a
              href="https://git.muenchen.de/openshift/openshift-configs/-/wikis/Internet#stargate"
              target="_blank"
              >weitere Infos zu stargate und holyplace</a
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

const nodeSelectorItems = ["worker", "stargate", "holyplace"];

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});
</script>
