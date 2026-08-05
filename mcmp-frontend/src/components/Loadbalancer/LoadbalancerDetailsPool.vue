<template>
  <common-card
    v-for="pool in lb.pools"
    :key="pool.name"
    :title="`Pool: ${pool.name}`"
    :top-margin="lb.pools.indexOf(pool) === 0 ? undefined : '0'"
    class="links"
  >
    <v-row>
      <v-col cols="3">
        <h3>
          Loadbalancing-Methode<v-menu
            location="top"
            content-class="text-left"
            open-on-hover
            :close-on-content-click="false"
            :open-delay="0"
            :close-delay="100"
          >
            <template #activator="{ props: activatorProps }">
              <span
                v-bind="activatorProps"
                class="ml-1"
              >
                <v-icon
                  :icon="mdiInformationOutline"
                  size="16"
                  color="primary"
                  style="vertical-align: baseline; position: relative; top: 2px"
                />
              </span>
            </template>
            <v-card class="pa-2">
              <p class="text-caption mt-2 mb-1">
                Verwendeter Algorithmus für das Loadbalancing siehe
                <a
                  href="https://my.f5.com/manage/s/article/K42275060"
                  target="_blank"
                  rel="noopener noreferrer"
                  >load-balancing-algorithms</a
                >.
              </p>
            </v-card>
          </v-menu>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>Monitor</h3>
      </v-col>
      <v-col
        v-if="pool.poolRef"
        cols="3"
      >
        <h3>
          Pool-Typ<info-tooltip>
            <div class="pa-1">
              <p class="text-caption mt-2 mb-1">
                <strong>Routing:</strong> Alle Anfragen, die auf die definierten
                Hosts / Pfade passen, werden an diesen Pool weitergeleitet.
              </p>
              <p class="text-caption mt-2 mb-1">
                <strong>iRule:</strong> Dieser Pool ist Teil einer iRule, der
                Traffic wird anhand von Kriterien aus dieser verarbeitet.
              </p>
              <p class="text-caption mt-2 mb-1">
                <strong>Standard:</strong> Das ist der Default-Pool, welcher
                alle anderen Anfragen erhält.
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
        <p>{{ pool.lbMethod }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p v-if="pool.monitors && pool.monitors.length">
          {{ pool.monitors.map((m) => m.type).join(", ") }}
        </p>
        <p v-else>-</p>
      </v-col>
      <v-col
        v-if="pool.poolRef"
        cols="3"
        class="pt-0"
      >
        <p>{{ poolTypeLabel(pool) }}</p>
      </v-col>
    </v-row>

    <template v-if="pool.poolRef && pool.poolRef.isDefault === null">
      <v-divider class="my-3" />
      <v-row>
        <v-col cols="3">
          <h3>Host</h3>
        </v-col>
        <v-col cols="9">
          <h3>Pfad</h3>
        </v-col>
      </v-row>
      <v-row
        v-for="(host, idx) in poolHosts(pool)"
        :key="idx"
      >
        <v-col
          cols="3"
          class="pt-0"
        >
          <p>{{ host }}</p>
        </v-col>
        <v-col
          cols="9"
          class="pt-0"
        >
          <p>{{ poolPaths(pool) }}</p>
        </v-col>
      </v-row>
    </template>

    <v-divider class="my-3" />
    <v-row>
      <v-col cols="12">
        <h3>Default-Monitor</h3>
      </v-col>
    </v-row>
    <template v-if="pool.monitors && pool.monitors.length">
      <v-row>
        <v-col cols="1">
          <h3>Typ</h3>
        </v-col>
        <v-col cols="2">
          <h3>Port</h3>
        </v-col>
        <v-col cols="1">
          <h3>Interval</h3>
        </v-col>
        <template v-if="poolHasHttpMonitor(pool)">
          <v-col cols="2">
            <h3>Pfad</h3>
          </v-col>
          <v-col cols="2">
            <h3>Host</h3>
          </v-col>
          <v-col cols="1">
            <h3>Methode</h3>
          </v-col>
          <v-col cols="1">
            <h3>Version</h3>
          </v-col>
          <v-col cols="2">
            <h3>Erwartet</h3>
          </v-col>
        </template>
      </v-row>
      <v-row
        v-for="(monitor, mIdx) in pool.monitors"
        :key="mIdx"
      >
        <v-col
          cols="1"
          class="pt-0"
        >
          <p>{{ monitor.type }}</p>
        </v-col>
        <v-col
          cols="2"
          class="pt-0"
        >
          <p>{{ monitorPort(monitor) }}</p>
        </v-col>
        <v-col
          cols="1"
          class="pt-0"
        >
          <p>{{ monitor.interval ?? "-" }}</p>
        </v-col>
        <template v-if="poolHasHttpMonitor(pool)">
          <v-col
            cols="2"
            class="pt-0"
          >
            <p>{{ monitor.path ?? "-" }}</p>
          </v-col>
          <v-col
            cols="2"
            class="pt-0"
          >
            <p>{{ monitor.host ?? "-" }}</p>
          </v-col>
          <v-col
            cols="1"
            class="pt-0"
          >
            <p>{{ monitor.method ?? "-" }}</p>
          </v-col>
          <v-col
            cols="1"
            class="pt-0"
          >
            <p>{{ monitor.version ?? "-" }}</p>
          </v-col>
          <v-col
            cols="2"
            class="pt-0"
          >
            <p>{{ monitor.expect ?? "-" }}</p>
          </v-col>
        </template>
      </v-row>
    </template>
    <v-row v-else>
      <v-col
        cols="12"
        class="pt-0"
      >
        <p>-</p>
      </v-col>
    </v-row>

    <template v-if="pool.members && pool.members.length">
      <v-divider class="my-3" />
      <v-row v-if="isCapPool(pool)">
        <v-col cols="12">
          <v-alert
            type="info"
            density="compact"
          >
            Loadbalancer zeigt auf CAP Ingress - Funktionsgruppe
            {{ capFunktionsgruppe(pool) }} ->
            <a
              class="cap-alert-link"
              href="https://git.muenchen.de/openshift/openshift-configs/-/wikis/Netzwerksecurity#liste-der-konfigurierten-ingress-rouer-nodeports"
              target="_blank"
              rel="noopener noreferrer"
              >CAP-Anleitung</a
            >
          </v-alert>
        </v-col>
      </v-row>
      <v-row>
        <v-col
          cols="12"
          class="d-flex align-center"
          style="cursor: pointer"
          @click="toggleServerList(pool.name)"
        >
          <h3>Member ({{ pool.members.length }})</h3>
          <v-btn
            :icon="
              isServerListExpanded(pool.name) ? mdiChevronUp : mdiChevronDown
            "
            variant="text"
            size="small"
            @click.stop="toggleServerList(pool.name)"
          />
          <loadbalancer-change-pool-members
            v-if="lb.canEdit && lb.appservices.length === 1"
            :lb="lb"
            :pool="pool"
            @click.stop
          />
        </v-col>
      </v-row>
      <v-expand-transition>
        <div v-show="isServerListExpanded(pool.name)">
          <v-row>
            <v-col cols="3">
              <h3>Server</h3>
            </v-col>
            <v-col cols="2">
              <h3>IP</h3>
            </v-col>
            <v-col cols="1">
              <h3>Port</h3>
            </v-col>
            <v-col cols="3">
              <h3>Monitor</h3>
            </v-col>
          </v-row>
          <v-row
            v-for="(member, idx) in pool.members"
            :key="idx"
          >
            <v-col
              cols="3"
              class="pt-0 links"
            >
              <router-link
                v-if="member.serverId"
                :to="`/server/${member.serverId}`"
                >{{ member.serverName }}</router-link
              >
              <p v-else>{{ member.serverName ?? "-" }}</p>
            </v-col>
            <v-col
              cols="2"
              class="pt-0"
            >
              <p>{{ member.ip }}</p>
            </v-col>
            <v-col
              cols="1"
              class="pt-0"
            >
              <p>{{ member.port }}</p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  member.monitorCondition === "inherit" ? "default" : "custom"
                }}
              </p>
            </v-col>
          </v-row>
        </div>
      </v-expand-transition>
    </template>
    <v-row
      v-else
      align="center"
    >
      <v-col class="pt-0 text-grey">
        <p>Keine Pool-Member vorhanden.</p>
      </v-col>
      <v-col
        v-if="lb.canEdit && lb.appservices.length === 1"
        cols="auto"
        class="pt-0"
      >
        <loadbalancer-change-pool-members
          :lb="lb"
          :pool="pool"
        />
      </v-col>
    </v-row>
  </common-card>

  <div
    v-if="!lb.pools || lb.pools.length === 0"
    class="pa-4 text-grey"
  >
    Keine Pools vorhanden.
  </div>
</template>

<script setup lang="ts">
import type {
  LbMonitor,
  LoadbalancerDetail,
  LoadbalancerPool,
} from "@/types/LoadbalancerDetail";

import { mdiChevronDown, mdiChevronUp, mdiInformationOutline } from "@mdi/js";
import { ref } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import LoadbalancerChangePoolMembers from "@/components/Loadbalancer/LoadbalancerChangePoolMembers.vue";

defineProps<{
  lb: LoadbalancerDetail;
}>();

const collapsedServerLists = ref<Set<string>>(new Set());

function toggleServerList(poolName: string) {
  if (collapsedServerLists.value.has(poolName)) {
    collapsedServerLists.value.delete(poolName);
  } else {
    collapsedServerLists.value.add(poolName);
  }
}

function isServerListExpanded(poolName: string): boolean {
  return !collapsedServerLists.value.has(poolName);
}

function poolHosts(pool: LoadbalancerPool): string[] {
  const hosts = pool.poolRef?.hosts;
  return hosts && hosts.length ? hosts : ["*"];
}

function poolPaths(pool: LoadbalancerPool): string {
  const paths = pool.poolRef?.paths;
  return paths && paths.length ? paths.join(", ") : "*";
}

function monitorPort(monitor: LbMonitor): string {
  return monitor.port === "poolmember"
    ? `Siehe Server Port`
    : (monitor.port ?? "-");
}

function poolHasHttpMonitor(pool: LoadbalancerPool): boolean {
  return !!pool.monitors?.some((m) => m.type?.toLowerCase().includes("http"));
}

const CAP_PORT_RANGES: [number, number][] = [
  [32201, 32207],
  [32301, 32307],
  [32401, 32407],
];

function isCapPort(port: number): boolean {
  return CAP_PORT_RANGES.some(([from, to]) => port >= from && port <= to);
}

const CAP_FUNKTIONSGRUPPEN: Record<string, string> = {
  "01": "Web2Tier",
  "02": "EAI",
  "04": "SYSADM",
  "05": "SWVT",
  "06": "AUTH",
  "07": "Monitor",
};

function capFunktionsgruppe(pool: LoadbalancerPool): string {
  const suffix = String(pool.members[0]?.port ?? "").slice(-2);
  return CAP_FUNKTIONSGRUPPEN[suffix] ?? "-";
}

function isCapPool(pool: LoadbalancerPool): boolean {
  return (
    pool.members.length > 0 &&
    pool.members.every((m) => !m.serverId) &&
    pool.members.every((m) => isCapPort(m.port))
  );
}

function poolTypeLabel(pool: LoadbalancerPool): string {
  if (pool.poolRef?.isDefault === true) {
    return "Standard";
  }
  if (pool.poolRef?.isDefault === false) {
    return "iRule";
  }
  return "Routing";
}
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

.links a.cap-alert-link,
.links a.cap-alert-link:visited,
.links a.cap-alert-link:hover,
.links a.cap-alert-link:active {
  color: inherit;
  font-weight: 600;
  text-decoration: underline;
}
</style>
