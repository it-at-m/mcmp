<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col cols="11">
        <v-select
          v-model="order.ingress"
          label="Ingress*"
          :items="ingressItems"
          rounded
          variant="outlined"
          class="mt-2"
          :menu-props="{ closeOnContentClick: true }"
          :rules="[
            rules.notEmptySelectRule('Ein Ingress muss ausgewählt werden'),
          ]"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="5"
          class="links_inverted"
        >
          <p>
            web2tier (Zugriff aus dem Server + Clientnetz)
            <br />eai (Zugriff aus dem Servernetz) <br />sysadm (sysadm =
            Systemadministration | besondere Zugriffe aus und in das Server +
            Clientnetz) <br />swvt (swvt = Softwareverteilung | besondere
            Zugriffe aus und in das Server + Clientnetz) <br />monitor
            (Vorgesehen für Monitorung-Anwendungen) <br />
            <a
              href="https://git.muenchen.de/openshift/openshift-configs/-/wikis/Onboarding-im-neuen-Cluster#%C3%BCbersicht-%C3%BCber-die-netzsegmente-und-die-zugriffe"
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

const order = defineModel<OpenshiftNamespaceOrder>({ required: true });
const rules = useRules();
const isValid = ref(false);
const emit = defineEmits(["validation-change"]);

const ingressItems = ["web2tier", "eai", "sysadm", "swvt", "monitor"];

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});
</script>
