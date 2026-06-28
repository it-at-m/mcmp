<template>
  <common-card title="Informationen">
    <v-row>
      <v-col cols="3">
        <h3>Name</h3>
      </v-col>
      <v-col cols="3">
        <h3>Adressen</h3>
      </v-col>
      <v-col cols="3">
        <h3>Domains</h3>
      </v-col>
      <v-col cols="3">
        <h3>
          MCMP-Anwendungsservice{{
            lb.appserviceNames && lb.appserviceNames.length > 1 ? "s" : ""
          }}<info-tooltip>
            <div class="pa-1">
              <strong>MCMP Anwendungservice-Ansicht</strong>
              <p class="text-caption mt-2 mb-1">
                Öffnet die Detailseite des Anwendungsservice direkt hier in der
                MCMP.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ lb.name }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <div v-if="lb.addresses && lb.addresses.length">
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="addr in lb.addresses"
              :key="addr"
            >
              {{ addr }}
            </li>
          </ul>
        </div>
        <p v-else>-</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div v-if="lb.domains && lb.domains.length">
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="domain in lb.domains"
              :key="domain"
            >
              <a
                :href="`https://${domain}`"
                target="_blank"
                rel="noopener noreferrer"
                >{{ domain }}</a
              >
            </li>
          </ul>
        </div>
        <p v-else>-</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <div v-if="lb.appserviceNames && lb.appserviceNames.length > 1">
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="name in lb.appserviceNames"
              :key="name"
              class="mb-1"
            >
              {{ name }}
            </li>
          </ul>
        </div>
        <p v-else-if="lb.appserviceNames && lb.appserviceNames.length === 1">
          {{ lb.appserviceNames[0] }}
        </p>
        <p v-else>-</p>
      </v-col>
    </v-row>
  </common-card>

  <common-card
    title="Listener"
    top-margin="0"
  >
    <v-row>
      <v-col cols="3">
        <h3>Listen</h3>
      </v-col>
      <v-col cols="3">
        <h3>Forward</h3>
      </v-col>
      <v-col cols="3">
        <h3>Port</h3>
      </v-col>
      <v-col cols="3">
        <h3>Persistenz</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ lb.listen }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ lb.forward }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ lb.port }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ lb.persistence }}</p>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="3">
        <h3>Redirect HTTP → HTTPS</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formatter.formatBooleanToJaNein(lb.redirect80) }}</p>
      </v-col>
    </v-row>
  </common-card>

  <common-card
    title="WAF"
    top-margin="0"
  >
    <v-row>
      <v-col cols="3">
        <h3>WAF aktiviert</h3>
      </v-col>
      <v-col
        v-if="lb.wafEnabled"
        cols="3"
      >
        <h3>WAF Status</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formatter.formatBooleanToJaNein(lb.wafEnabled) }}</p>
      </v-col>
      <v-col
        v-if="lb.wafEnabled"
        cols="3"
        class="pt-0"
      >
        <p>{{ lb.wafStatus ?? "-" }}</p>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";

import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import { useFormatter } from "@/composables/formatter.ts";

defineProps<{
  lb: LoadbalancerDetail;
}>();

const formatter = useFormatter();
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
