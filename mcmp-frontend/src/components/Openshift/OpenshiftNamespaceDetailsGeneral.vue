<template>
  <common-card title="Informationen">
    <v-row>
      <v-col cols="3">
        <h3>Namespace Name</h3>
      </v-col>
      <v-col cols="3">
        <h3>Cluster</h3>
      </v-col>
      <v-col cols="3">
        <h3>K8s-UID</h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Anwendungsservice{{
            namespace.appservices && namespace.appservices.length > 1 ? "s" : ""
          }}
        </h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <p v-if="namespace.webconsoleUrl">
          <a
            :href="namespace.webconsoleUrl"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ namespace.name }}
          </a>
        </p>
        <p v-else>{{ namespace.name }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formatter.formatOpenshiftClusterName(namespace.clusterName) }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ namespace.k8sUid ?? "-" }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div v-if="namespace.appservices && namespace.appservices.length > 1">
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="appservice in namespace.appservices"
              :key="appservice.id"
              class="mb-1"
            >
              <router-link :to="`/appservice/${appservice.id}`">
                {{ appservice.name }}
              </router-link>
            </li>
          </ul>
        </div>
        <p v-else-if="firstAppservice">
          <router-link :to="`/appservice/${firstAppservice.id}`">
            {{ firstAppservice.name }}
          </router-link>
        </p>
        <p v-else>-</p>
      </v-col>
    </v-row>
  </common-card>

  <common-card
    title="CI Details"
    top-margin="0"
  >
    <v-row>
      <v-col cols="3">
        <h3>ServiceNow CI</h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Zuletzt entdeckt<info-tooltip>
            <div class="pa-1">
              <p class="text-caption mt-2 mb-1">
                Dieser Wert wird von ServiceNow gepflegt und nur alle 24 Stunden
                aktualisiert.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <p v-if="snowUrl">
          <a
            :href="snowUrl"
            target="_blank"
            rel="noopener noreferrer"
            >{{ namespace.name }}</a
          >
        </p>
        <p v-else>-</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formattedLastDiscovered }}</p>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type { OpenshiftNamespaceDetail } from "@/types/OpenshiftNamespaceDetail";

import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  namespace: OpenshiftNamespaceDetail;
}>();

const formatter = useFormatter();

const firstAppservice = computed(
  () => props.namespace.appservices?.[0] ?? null
);

const formattedLastDiscovered = computed(() =>
  props.namespace.lastDiscovered
    ? formatter.toDateAndTimeString(props.namespace.lastDiscovered)
    : "-"
);

const snowUrl = computed(() =>
  props.namespace.sysClass && props.namespace.sysId
    ? `https://it-services.muenchen.de/now/sgw/record/${props.namespace.sysClass}/${props.namespace.sysId}`
    : null
);
</script>

<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
