<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col cols="5">
        <v-select
          v-model="selectedProtocol"
          :items="listenerProtocols"
          item-title="title"
          item-value="value"
          label="Listener Protokoll*"
          rounded
          variant="outlined"
          class="mt-2"
          :menu-props="{ closeOnContentClick: true }"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="5"
          class="links"
        >
          <p>
            Wie soll der Loadbalancer auf dem Listener Port angesprochen werden?
            <br />tcp: Kommt bspw. bei non-http Anwendungen oder für
            <a
              href="https://www.ssldragon.com/de/blog/ssl-passthrough/#what-it-is"
              target="_blank"
            >
              SSL/TLS-Passtrough zum Einsatz</a
            >
            <br />http: Loadbalancer kommuniziert unverschlüsselt mit http
            <br />https: Loadbalancer kommuniziert verschlüsselt mit http
          </p>
        </inline-tooltip>
      </v-col>
      <v-col cols="6">
        <v-text-field
          v-model="ldblOrder.listener[0].port"
          label="Listener Port*"
          type="number"
          rounded
          variant="outlined"
          class="mt-2"
          :rules="[
            rules.rangeRule(1, 65535, 'Port muss zwischen 1 und 65535 liegen'),
          ]"
        />
      </v-col>
    </v-row>
    <div v-if="ldblOrder.listener[0].listener_type === 'http'">
      <v-row>
        <v-col cols="11">
          <v-select
            v-model="ldblOrder.listener[0].persistence"
            :items="['none', 'cookie', 'source-address']"
            label="Persistenz*"
            rounded
            variant="outlined"
            :menu-props="{ closeOnContentClick: true }"
          />
        </v-col>
        <v-col cols="1">
          <inline-tooltip
            margin-top="3"
            class="links"
          >
            <p>
              Sollen wiederholte Anfragen des selben "Clients" zum selben Server
              weitergeleitet werden? <br />cookie: Client ist definiert durch
              einen Cookie den er vom Loadbalancer erhält <br />source-address:
              Client wird durch seine Source IP definiert
            </p>
          </inline-tooltip>
        </v-col>
      </v-row>
      <v-expansion-panels>
        <v-expansion-panel title="Erweitert">
          <v-expansion-panel-text>
            <v-row>
              <v-col cols="5">
                <v-checkbox
                  v-model="ldblOrder.listener[0].x_forwarded_for"
                  label="X-Forwarded-For Header setzen"
                />
              </v-col>
              <v-col cols="1">
                <inline-tooltip
                  margin-top="3"
                  class="links"
                >
                  <p>
                    Siehe
                    <a
                      href="https://developer.mozilla.org/de/docs/Web/HTTP/Reference/Headers/X-Forwarded-For"
                      target="_blank"
                      >X-Forwarded-For Header</a
                    >
                  </p>
                </inline-tooltip>
              </v-col>
              <v-col cols="5">
                <v-checkbox
                  v-model="ldblOrder.listener[0].wss"
                  label="WebSocket Support aktivieren"
                />
              </v-col>
              <v-col cols="1">
                <inline-tooltip
                  margin-top="3"
                  class="links"
                >
                  <p>
                    Wird benötigt wenn die Anwendung das
                    <a
                      href="https://de.wikipedia.org/wiki/WebSocket"
                      target="_blank"
                      >Web-Socket-Protokoll</a
                    >
                    verwendet
                  </p>
                </inline-tooltip>
              </v-col>
            </v-row>
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>
    </div>
    <div
      v-if="
        ldblOrder.listener[0].listener_type === 'fast-tcp' ||
        ldblOrder.listener[0].listener_type === 'tcp'
      "
    >
      <v-row>
        <v-col>
          <v-checkbox
            label="TCP Hardwarebeschleunigung aktivieren"
            :model-value="ldblOrder.listener[0].listener_type === 'fast-tcp'"
            @update:model-value="
              (val) => {
                if (val) {
                  ldblOrder.listener[0].listener_type = 'fast-tcp';
                  listenerProtocols = [
                    { title: 'Fast-TCP', value: 'fast-tcp' },
                  ];
                  ldblOrder.listener[0].clientside_tls = false;
                  ldblOrder.listener[0].serverside_tls = false;
                } else {
                  ldblOrder.listener[0].listener_type = 'tcp';
                  listenerProtocols = [{ title: 'TCP', value: 'tcp' }];
                }
              }
            "
          />
        </v-col>
        <v-col>
          <v-select
            v-model="ldblOrder.listener[0].persistence"
            :items="['none', 'source-address']"
            label="Persistenz*"
            rounded
            variant="outlined"
            :menu-props="{ closeOnContentClick: true }"
          />
        </v-col>
      </v-row>
    </div>
  </v-form>
</template>
<script setup lang="ts">
import type LoadbalancerOrder from "@/types/LoadbalancerOrder.ts";

import { computed, ref, watch } from "vue";

import InlineTooltip from "@/components/common/InlineTooltip.vue";
import { useRules } from "@/composables/rules.ts";

const ldblOrder = defineModel<LoadbalancerOrder>("ldblOrder", {
  required: true,
});
const props = defineProps<{
  protocol?: "tcp" | "http" | "https";
}>();
const rules = useRules();
const isValid = ref(false);
const emit = defineEmits<(e: "validation-change", valid: boolean) => void>();

const listenerProtocols = ref<{ title: string; value: string }[]>([
  { title: "HTTPS", value: "https" },
  { title: "HTTP", value: "http" },
]);

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});

// Watcher für effectiveProtocol, um listenerProtocols anzupassen
watch(
  () => props.protocol,
  (protocol) => {
    if (protocol === "tcp") {
      // Nur TCP verfügbar
      listenerProtocols.value = [{ title: "TCP", value: "tcp" }];
      if (
        ldblOrder.value.listener[0].listener_type !== "tcp" &&
        ldblOrder.value.listener[0].listener_type !== "fast-tcp"
      ) {
        ldblOrder.value.listener[0].listener_type = "tcp";
      }
    } else if (protocol === "http") {
      // HTTP und HTTPS verfügbar
      listenerProtocols.value = [
        { title: "HTTPS", value: "https" },
        { title: "HTTP", value: "http" },
      ];
      ldblOrder.value.listener[0].listener_type = "http";
      ldblOrder.value.listener[0].clientside_tls = true;
      ldblOrder.value.listener[0].port = 443;
    } else if (protocol === "https" || protocol === undefined) {
      // Nur HTTPS verfügbar (auch Standard, wenn kein Protokoll gesetzt)
      listenerProtocols.value = [{ title: "HTTPS", value: "https" }];
      // Listener bleibt of type 'http' but clientside_tls = true
      ldblOrder.value.listener[0].listener_type = "http";
      ldblOrder.value.listener[0].clientside_tls = true;
      ldblOrder.value.listener[0].port = 443;
    }
  },
  { immediate: true }
);

// Watcher für listener_type Änderungen durch Benutzer
watch(
  () => ldblOrder.value?.listener?.[0]?.listener_type,
  (newVal) => {
    if (!newVal) return;
    if (newVal === "http") {
      ldblOrder.value.listener[0].persistence = "cookie";
      // clientside_tls und port werden durch selectedProtocol gesetzt
    } else if (newVal === "tcp" || newVal === "fast-tcp") {
      ldblOrder.value.listener[0].persistence = "source-address";
      ldblOrder.value.listener[0].clientside_tls = false;
      ldblOrder.value.listener[0].serverside_tls = false;
    }
  }
);

// Separater computed um zwischen http/https Auswahl zu vermitteln
const selectedProtocol = computed({
  get: () => {
    if (ldblOrder.value.listener[0].listener_type === "http") {
      return ldblOrder.value.listener[0].clientside_tls ? "https" : "http";
    }
    return ldblOrder.value.listener[0].listener_type;
  },
  set: (value: string) => {
    if (value === "http") {
      ldblOrder.value.listener[0].listener_type = "http";
      ldblOrder.value.listener[0].clientside_tls = false;
      ldblOrder.value.listener[0].port = 80;
    } else if (value === "https") {
      ldblOrder.value.listener[0].listener_type = "http";
      ldblOrder.value.listener[0].clientside_tls = true;
      if (ldblOrder.value.listener[0].port === 80) {
        ldblOrder.value.listener[0].port = 443;
      }
    } else {
      ldblOrder.value.listener[0].listener_type =
        value as LoadbalancerOrder["listener"][0]["listener_type"];
    }
  },
});
</script>
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link_inverted));
  text-decoration: none;
}
</style>
