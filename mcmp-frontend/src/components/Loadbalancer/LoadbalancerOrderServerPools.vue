<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col cols="11">
        <v-select
          v-model="ldblOrder.server_pools[0].member"
          :items="servers"
          label="Server Auswahl*"
          item-title="name"
          return-object
          :rules="[
            (v: any[]) =>
              !v?.some((m) => !m.ip) ||
              'Ein ausgewählter Server hat keine IP-Adresse',
            rules.notEmptySelectRule(
              'Es muss mindestens ein Server ausgewählt werden'
            ),
          ]"
          rounded
          variant="outlined"
          class="mt-2"
          multiple
          :menu-props="{ persistent: true, closeOnContentClick: true }"
        >
          <template #selection="{ item }">
            <v-expansion-panels
              :model-value="expandedIps.has(item.raw.ip) ? 0 : undefined"
              @click.stop
              @mousedown.stop
            >
              <v-expansion-panel
                class="ma-1"
                :title="item.title"
                color="select"
                rounded="xl"
              >
                <v-expansion-panel-text>
                  <v-row>
                    <v-col> IP-Adresse: </v-col>
                    <v-col> {{ item.raw.ip }} </v-col>
                  </v-row>
                  <v-row
                    class="mb-2"
                    align="center"
                  >
                    <v-col cols="4"> Ports </v-col>
                    <v-col cols="2" />
                    <v-col cols="5">
                      <v-btn-group
                        rounded="xl"
                        variant="outlined"
                      >
                        <v-btn
                          :icon="mdiMinusCircle"
                          size="small"
                          :disabled="(portCountByIp[item.raw.ip] || 1) <= 1"
                          @click.stop="() => decreasePortCount(item)"
                        />
                        <v-btn
                          :icon="mdiPlusCircle"
                          size="small"
                          :disabled="(portCountByIp[item.raw.ip] || 1) >= 10"
                          @click.stop="() => increasePortCount(item)"
                        />
                      </v-btn-group>
                    </v-col>
                  </v-row>
                  <div
                    v-for="n in portCountByIp[item.raw.ip] || 1"
                    :key="n"
                  >
                    <v-row>
                      <v-text-field
                        v-model.number="item.raw.ports[n - 1]"
                        type="number"
                        placeholder="Port*"
                        rounded
                        variant="filled"
                        :rules="[
                          rules.rangeRule(
                            1,
                            65535,
                            'Port muss zwischen 1 und 65535 liegen'
                          ),
                        ]"
                        @click.stop
                        @mousedown.stop
                      />
                    </v-row>
                  </div>
                </v-expansion-panel-text>
              </v-expansion-panel>
            </v-expansion-panels>
          </template>
        </v-select>
      </v-col>
      <v-col cols="1">
        <inline-tooltip margin-top="5">
          <p>
            Hier werden Server gelistet die mit dem ausgewählten
            Anwendungsservice verknüpft sind.
          </p>
        </inline-tooltip>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="5">
        <v-select
          v-model="localProtocol"
          :items="['tcp', 'http', 'https']"
          label="Protokoll*"
          rounded
          variant="outlined"
          :menu-props="{ persistent: true, closeOnContentClick: true }"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="3"
          class="links"
        >
          <p>
            Wie sollen die ausgewählten Server auf den definierten Ports
            angesprochen werden? <br />tcp: Kommt bspw. bei non-http Anwendungen
            oder für
            <a
              href="https://www.ssldragon.com/de/blog/ssl-passthrough/#what-it-is"
              target="_blank"
              >TLS-Passtrough</a
            >
            zum Einsatz <br />http: Server kommuniziert unverschlüsselt mit
            http<br />https: Server kommuniziert verschlüsselt mit http
          </p>
        </inline-tooltip>
      </v-col>
      <v-col cols="5">
        <v-select
          v-model="ldblOrder.server_pools[0].loadbalancing_mode"
          :items="loadbalancingModes"
          item-title="title"
          item-value="value"
          label="Loadbalancing Modus*"
          rounded
          variant="outlined"
          :menu-props="{ persistent: true, closeOnContentClick: true }"
        />
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="3"
          class="links"
        >
          <p>
            Verwendeter Algorithmus für das Loadbalancing siehe
            <a
              href="https://www.geeksforgeeks.org/system-design/load-balancing-algorithms"
              target="_blank"
              >load-balancing-algorithms</a
            >
          </p>
        </inline-tooltip>
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-radio-group
          v-model="monitors"
          inline
          :rules="[(v: MonitorType) => !!v || 'Monitor Typ ist erforderlich']"
          label="Monitor Typ*"
          @update:model-value="
            (val) => {
              if (val === 'tcp') {
                ldblOrder.server_pools[0].monitors = ['tcp'];
                showMonitors = false;
              } else {
                const headers = { Host: props.ldblOrder.dns! };
                const type: 'http' | 'https' =
                  val === 'https' ? 'https' : 'http';
                ldblOrder.server_pools[0].monitors = [
                  {
                    type: type,
                    method: 'GET',
                    path: '/status',
                    headers: headers,
                    receive_string: '200 OK',
                  },
                ];
                showMonitors = true;
              }
            }
          "
        >
          <v-radio
            label="TCP Monitor"
            value="tcp"
          ></v-radio>
          <v-radio
            label="HTTP Monitor"
            value="http"
            :disabled="localProtocol === 'https'"
          ></v-radio>
          <v-radio
            label="HTTPS Monitor"
            value="https"
            :disabled="localProtocol === 'http'"
          ></v-radio>
        </v-radio-group>
      </v-col>
      <v-col cols="1">
        <inline-tooltip
          margin-top="5"
          class="links"
        >
          <p>
            TCP Monitor überprüft ob der für den Server angegebene Port
            erreichbar ist<br />HTTP Monitor macht einen GET Request auf den
            angegebenen Pfad und überprüft ob die Antwort den definierten Text
            enthält. Für die Anfrage wird die
            <a
              href="https://developer.mozilla.org/de/docs/Web/HTTP/Guides/Evolution_of_HTTP#http1.1_%E2%80%93_das_standardisierte_protokoll"
              target="_blank"
              >HTTP-Version 1.1</a
            >
            verwendet
          </p>
        </inline-tooltip>
      </v-col>
    </v-row>
    <div
      v-if="
        Array.isArray(ldblOrder.server_pools[0].monitors) &&
        typeof ldblOrder.server_pools[0].monitors[0] === 'object' &&
        showMonitors
      "
    >
      <v-row>
        <v-col cols="5">
          <v-text-field
            v-model="ldblOrder.server_pools[0].monitors[0].path"
            label="Monitor Pfad*"
            rounded
            variant="outlined"
            class="mt-2"
            :rules="[
              rules.regexRule(
                /^\/(?!\/)[^?#\s]*(?:\?[^#\s]*)?(?:#[^\s]*)?$/,
                'Eingabe enthält ungültige Zeichen/Symbole'
              ),
            ]"
          />
        </v-col>
        <v-col cols="1">
          <inline-tooltip margin-top="5">
            <p>
              Der zu überwachende Anwendungspfad. <br />
              Oftmals gibt es hier eine Empfehlung des Softwareherstellers auf
              Nachfrage oder in der Dokumentation.
            </p>
          </inline-tooltip>
        </v-col>
        <v-col cols="5">
          <v-text-field
            v-model="ldblOrder.server_pools[0].monitors[0].receive_string"
            label="Erwarteter Antwortstring"
            rounded
            variant="outlined"
            class="mt-2"
          />
        </v-col>
        <v-col cols="1">
          <inline-tooltip
            margin-top="5"
            class="links"
          >
            <p>
              Dieser String wird vom Loadbalancer in der Antwort des Servers
              erwartet.<br />
              Hierbei zählt die gesamte HTTP-Response d.h. es kann sowohl auf
              <a
                href="https://developer.mozilla.org/de/docs/Web/HTTP/Reference/Status"
                target="_blank"
                >Response-Codes</a
              >
              wie
              <a
                href="https://developer.mozilla.org/de/docs/Web/HTTP/Reference/Status/200"
                target="_blank"
                >200</a
              >
              als auch auf Teile des Bodys (z.B. "running") geprüft werden.
            </p>
          </inline-tooltip>
        </v-col>
      </v-row>
    </div>
  </v-form>
</template>

<script setup lang="ts">
import type { MonitorType } from "@/types/LoadbalancerOrder.ts";

import { mdiMinusCircle, mdiPlusCircle } from "@mdi/js";
import { reactive, ref, watch } from "vue";

import InlineTooltip from "@/components/common/InlineTooltip.vue";
import { useRules } from "@/composables/rules.ts";
import LoadbalancerOrder from "@/types/LoadbalancerOrder.ts";

interface member {
  name: string;
  ip: string;
  ports: number[];
}

const props = defineProps<{
  ldblOrder: LoadbalancerOrder;
  servers: member[];
  protocol: "tcp" | "http" | "https";
}>();
const emit = defineEmits(["validation-change", "update:protocol"]);
const rules = useRules();
const isValid = ref(false);
const showMonitors = ref(false);
const monitors = ref("tcp");
// localProtocol mirrors incoming prop and emits updates for v-model:protocol
const localProtocol = ref<"tcp" | "http" | "https">(props.protocol ?? "http");
// per-server port counts keyed by IP to avoid shared counter across items
const portCountByIp = reactive<Record<string, number>>({});
// IPs whose expansion panel should be open (newly added servers)
const expandedIps = reactive<Set<string>>(new Set());
// IPs that were already selected on mount – these should NOT auto-expand
const initializedIps = new Set<string>();
let membersInitialized = false;

// helpers to safely initialize per-server counters and manipulate ports on the item
function ensurePortCountFor(ip: string, ports?: number[]) {
  if (!portCountByIp[ip]) {
    portCountByIp[ip] = ports && ports.length ? ports.length : 1;
  }
}

function decreasePortCount(item: any) {
  const ip = item.raw.ip;
  ensurePortCountFor(ip, item.raw.ports);
  portCountByIp[ip] = Math.max(1, portCountByIp[ip] - 1);
  // remove trailing port entries if ports array is longer than the count
  if (
    Array.isArray(item.raw.ports) &&
    portCountByIp[ip] < item.raw.ports.length
  ) {
    item.raw.ports.pop();
  }
}

function increasePortCount(item: any) {
  const ip = item.raw.ip;
  ensurePortCountFor(ip, item.raw.ports);
  portCountByIp[ip] = Math.min(10, portCountByIp[ip] + 1);
  if (!Array.isArray(item.raw.ports)) item.raw.ports = [];
  // push a sensible default if needed
  item.raw.ports.push(80);
}

const loadbalancingModes = [
  { title: "Round Robin", value: "round-robin" },
  { title: "Least Connections", value: "least-connections-member" },
];

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});

// initialize per-server counters when servers prop is available or changes
watch(
  () => props.servers,
  (newServers) => {
    if (Array.isArray(newServers)) {
      newServers.forEach((s) => {
        ensurePortCountFor(s.ip, s.ports);
      });
    }
  },
  { immediate: true, deep: true }
);

// ensure counters exist for currently selected members (v-select model)
watch(
  () => props.ldblOrder.server_pools[0].member,
  (members) => {
    if (Array.isArray(members)) {
      if (!membersInitialized) {
        // First run (immediate): record all existing members as known, no auto-expand
        members.forEach((m: any) => {
          const ip = m?.raw?.ip ?? m?.ip;
          const ports = m?.raw?.ports ?? m?.ports;
          if (ip) {
            ensurePortCountFor(ip, ports);
            initializedIps.add(ip);
          }
        });
        membersInitialized = true;
        return;
      }

      const currentIps = new Set<string>();
      members.forEach((m: any) => {
        const ip = m?.raw?.ip ?? m?.ip;
        const ports = m?.raw?.ports ?? m?.ports;
        if (ip) {
          currentIps.add(ip);
          if (!initializedIps.has(ip)) {
            // Genuinely new server added by the user – expand it
            initializedIps.add(ip);
            ensurePortCountFor(ip, ports);
            expandedIps.add(ip);
          } else {
            ensurePortCountFor(ip, ports);
          }
        }
      });
      // Clean up expandedIps for removed servers
      for (const ip of expandedIps) {
        if (!currentIps.has(ip)) expandedIps.delete(ip);
      }
    }
  },
  { immediate: true, deep: true }
);

// sync incoming prop -> localProtocol
watch(
  () => props.protocol,
  (p) => {
    if (p && p !== localProtocol.value) localProtocol.value = p;
  }
);

// when localProtocol changes, emit update and apply derived settings
watch(localProtocol, (newVal) => {
  emit("update:protocol", newVal);
  // update serverside tls on listener
  props.ldblOrder.listener[0].serverside_tls = newVal === "https";
  if (newVal === "tcp") {
    props.ldblOrder.listener[0].persistence = "source-address";
  }

  // If monitor selection conflicts with protocol, adjust monitors
  if (monitors.value === "http" && newVal === "https") {
    // keep monitors array consistent: prefer 'tcp' when conflict
    monitors.value = "tcp";
    props.ldblOrder.server_pools[0].monitors = ["tcp"];
    showMonitors.value = false;
  } else if (monitors.value === "https" && newVal === "http") {
    monitors.value = "tcp";
    props.ldblOrder.server_pools[0].monitors = ["tcp"];
    showMonitors.value = false;
  }
});

// existing monitor radio update handler relied on 'protocol' var; other code uses localProtocol
watch(
  monitors,
  (val) => {
    // keep ldblOrder.server_pools[0].monitors in sync when user changes monitor selection
    if (val === "tcp") {
      props.ldblOrder.server_pools[0].monitors = ["tcp"];
      showMonitors.value = false;
    } else {
      const headers = { Host: props.ldblOrder.dns! };
      const type: "http" | "https" = val === "https" ? "https" : "http";
      props.ldblOrder.server_pools[0].monitors = [
        {
          type: type,
          method: "GET",
          path: "/status",
          headers: headers,
          receive_string: "200 OK",
        },
      ];
      showMonitors.value = true;
    }
  },
  { immediate: true }
);
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
