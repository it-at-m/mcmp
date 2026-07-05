<template>
  <common-card
    v-for="pool in lb.pools"
    :key="pool.name"
    :title="`Pool: ${pool.name}`"
    :top-margin="lb.pools.indexOf(pool) === 0 ? undefined : '0'"
  >
    <v-row>
      <v-col cols="3">
        <h3>Loadbalancing-Methode</h3>
      </v-col>
      <v-col cols="3">
        <h3>Monitor</h3>
      </v-col>
      <v-col
        v-if="pool.poolRef"
        cols="3"
      >
        <h3>Pool-Typ</h3>
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
        <p>{{ pool.poolRef.isDefault ? "Standard" : "Routing" }}</p>
      </v-col>
    </v-row>

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
          <p>{{ member.monitorCondition === "inherit" ? "default" : "custom" }}</p>
        </v-col>
      </v-row>
    </template>
    <v-row v-else>
      <v-col class="pt-0 text-grey">
        <p>Keine Pool-Member vorhanden.</p>
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

import CommonCard from "@/components/common/CommonCard.vue";

defineProps<{
  lb: LoadbalancerDetail;
}>();

function poolHosts(pool: LoadbalancerPool): string[] {
  const hosts = pool.poolRef?.hosts;
  return hosts && hosts.length ? hosts : ["*"];
}

function poolPaths(pool: LoadbalancerPool): string {
  const paths = pool.poolRef?.paths;
  return paths && paths.length ? paths.join(", ") : "*";
}

function monitorPort(monitor: LbMonitor): string {
  return monitor.port === "poolmember" ? `Siehe Server Port` : monitor.port ?? "-";
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
</style>
