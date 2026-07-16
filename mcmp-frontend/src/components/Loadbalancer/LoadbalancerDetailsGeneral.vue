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
            lb.appservices && lb.appservices.length > 1 ? "s" : ""
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
        class="pt-0 links"
      >
        <p>{{ lb.name }}</p>
        <a
          v-if="lb.tenantRepositoryUrl"
          :href="lb.tenantRepositoryUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          Repository
        </a>
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
        <div v-if="lb.appservices && lb.appservices.length > 1">
          <ul style="padding-left: 0; list-style-position: inside">
            <li
              v-for="appservice in lb.appservices"
              :key="appservice.id"
              class="mb-1"
            >
              <router-link :to="`/appservice/${appservice.id}`">
                {{ appservice.name }}
              </router-link>
            </li>
          </ul>
        </div>
        <p v-else-if="lb.appservices && lb.appservices.length === 1">
          <router-link :to="`/appservice/${lb.appservices[0].id}`">
            {{ lb.appservices[0].name }}
          </router-link>
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
        <h3>
          Listener Protokoll<info-tooltip>
            <div class="pa-1">
              <p class="text-caption mt-2 mb-1">
                Der Listener nimmt die Anfragen des Clients auf dem definierten
                Port an, verarbeitet diese und leitet sie dann an den passenden
                Pool von Servern weiter.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>Server Protokoll</h3>
      </v-col>
      <v-col cols="3">
        <h3>Port</h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Persistenz<info-tooltip>
            <div class="pa-1">
              <p class="text-caption mt-2 mb-1">
                Sollen wiederholte Anfragen desselben Clients an denselben
                Server weitergeleitet werden?
              </p>
              <p class="text-caption mt-2 mb-1">
                <strong>Cookie:</strong> Der Client erhält bei der ersten
                Anfrage ein Cookie vom Loadbalancer, durch das er bei
                darauffolgenden Anfragen identifiziert werden kann.
              </p>
              <p class="text-caption mt-2 mb-1">
                <strong>Source-Address:</strong> Der Client wird anhand seiner
                Quell-IP identifiziert.
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
        <h3>
          Redirect HTTP → HTTPS<info-tooltip>
            <div class="pa-1">
              <p class="text-caption mt-2 mb-1">
                Der Listener lauscht zusätzlich auf Port 80 (http) und leitet
                Anfragen von dort auf Port 443 (https) weiter.
              </p>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col
        v-if="lb.wafEnabled"
        cols="3"
      >
        <h3>
          WAF-Status<info-tooltip>
            <div class="pa-1">
              <p class="text-caption mt-2 mb-1">
                Verhalten des Web-Application-Firewall-Moduls:
              </p>
              <p class="text-caption mt-2 mb-1">
                <strong>blocking:</strong> Als bösartig klassifizierte Requests
                werden blockiert.
              </p>
              <p class="text-caption mt-2 mb-1">
                <strong>transparent:</strong> Als bösartig klassifizierte
                Requests werden nicht blockiert, lediglich geloggt.
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
        <p>{{ formatter.formatBooleanToJaNein(lb.redirect80) }}</p>
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
