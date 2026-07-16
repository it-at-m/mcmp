<template>
  <h2>Allgemein</h2>
  <v-divider />
  <br />
  <v-row>
    <v-col><strong>Anwendungsservice:</strong> </v-col>
    <v-col>{{ ldblOrder.appservice?.name }} </v-col>
  </v-row>
  <v-row>
    <v-col><strong>DNS Eintrag:</strong> </v-col>
    <v-col>{{ ldblOrder.dns }} </v-col>
  </v-row>
  <br />
  <h2>Server Pool</h2>
  <v-divider />
  <br />
  <v-row>
    <v-col><strong>Ausgewählte Server:</strong> </v-col>
    <v-col>
      <ul>
        <li
          v-for="(server, index) in ldblOrder.server_pools[0].member"
          :key="server.name || index"
        >
          {{ server.name }}
          <ul class="ports-list">
            <li
              v-for="(port, portIndex) in server.ports"
              :key="portIndex"
            >
              {{ port }}
            </li>
          </ul>
        </li>
      </ul>
    </v-col>
  </v-row>
  <v-row>
    <v-col><strong>Protokoll:</strong> </v-col>
    <v-col>{{ protocol }} </v-col>
  </v-row>
  <v-row>
    <v-col><strong>Loadbalaning Modus:</strong> </v-col>
    <v-col>{{ ldblOrder.server_pools[0].loadbalancing_mode }} </v-col>
  </v-row>
  <v-row v-if="ldblOrder.server_pools[0].monitors[0] == 'tcp'">
    <v-col><strong>Monitor:</strong> </v-col>
    <v-col> tcp </v-col>
  </v-row>
  <div v-else>
    <v-row>
      <v-col><strong>Monitor:</strong> </v-col>
      <v-col> {{ ldblOrder.server_pools[0].monitors[0].type }} </v-col>
    </v-row>
    <v-row>
      <v-col><strong>Monitor Pfad:</strong> </v-col>
      <v-col>{{ ldblOrder.server_pools[0].monitors[0].path }} </v-col>
    </v-row>
    <v-row>
      <v-col><strong>Monitor Antwortstring:</strong> </v-col>
      <v-col>{{ ldblOrder.server_pools[0].monitors[0].receive_string }} </v-col>
    </v-row>
  </div>
  <br />
  <h2>Listener</h2>
  <v-divider />
  <br />
  <v-row>
    <v-col><strong>Listener Typ:</strong> </v-col>
    <v-col v-if="ldblOrder.listener[0].clientside_tls">https</v-col>
    <v-col v-else>{{ ldblOrder.listener[0].listener_type }} </v-col>
  </v-row>
  <v-row>
    <v-col><strong>Listener Port:</strong> </v-col>
    <v-col>{{ ldblOrder.listener[0].port }} </v-col>
  </v-row>
  <v-row>
    <v-col><strong>Persistenz:</strong> </v-col>
    <v-col>{{ ldblOrder.listener[0].persistence }} </v-col>
  </v-row>
  <v-row>
    <v-col><strong>X-Forwarded-For Header:</strong> </v-col>
    <v-col
      >{{
        useFormatter().formatBooleanToJaNein(
          ldblOrder.listener[0].x_forwarded_for
        )
      }}
    </v-col>
  </v-row>
  <v-row>
    <v-col><strong>WebSocket Support:</strong> </v-col>
    <v-col
      >{{ useFormatter().formatBooleanToJaNein(ldblOrder.listener[0].wss) }}
    </v-col>
  </v-row>
</template>
<script setup lang="ts">
import type LoadbalancerOrder from "@/types/LoadbalancerOrder.ts";

import { onMounted } from "vue";

import { useFormatter } from "@/composables/formatter.ts";

const emit = defineEmits(["validation-change"]);
const props = defineProps<{
  ldblOrder: LoadbalancerOrder;
  protocol?: "tcp" | "http" | "https";
}>();

onMounted(() => {
  emit("validation-change", true);
});
</script>
<style scoped>
.ports-list {
  padding-left: 2rem; /* Einrückung */
  margin-top: 0.25rem;
}
.ports-list li {
  margin-bottom: 0.125rem;
}
</style>
