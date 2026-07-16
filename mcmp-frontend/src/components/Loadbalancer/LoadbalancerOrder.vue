<template>
  <common-dialog
    v-model="dialog"
    :loading="loading"
    title="Loadbalancer Bestellung"
    max-width="1200"
    submit-activated
    show-change-warning
    :check-for-enabled-actions="['LOADBALANCER_F5']"
    @dialog-cancel="close"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        flat
        @click="registerOpenDialog"
        >Bestellen
      </v-btn>
    </template>
    <v-stepper
      v-model="step"
      :items="pages"
      class="pa-4"
    >
      <template #item.1>
        <loadbalancer-order-general
          :ref="(el) => (stepRefs[1] = el)"
          :ldbl-order="LoadbalancerOrderProp"
          :has-servers="hasServers"
          @validation-change="(val) => (stepValidity[1] = val)"
        />
      </template>
      <template #item.2>
        <loadbalancer-order-server-pools
          :ref="(el) => (stepRefs[2] = el)"
          v-model:protocol="protocol"
          :ldbl-order="LoadbalancerOrderProp"
          :servers="servers"
          @validation-change="(val) => (stepValidity[2] = val)"
        />
      </template>
      <template #item.3>
        <loadbalancer-order-listener
          :ref="(el) => (stepRefs[3] = el)"
          :ldbl-order="LoadbalancerOrderProp"
          :protocol="protocol"
          @validation-change="(val) => (stepValidity[3] = val)"
        />
      </template>
      <template #item.4>
        <loadbalancer-order-summary
          :ref="(el) => (stepRefs[4] = el)"
          :ldbl-order="LoadbalancerOrderProp"
          :protocol="protocol"
          @validation-change="(val) => (stepValidity[4] = val)"
        />
      </template>

      <template #actions="{ next, prev }">
        <div class="d-flex justify-space-between w-100 mt-4 mb-4 px-4">
          <v-btn
            :prepend-icon="mdiArrowLeft"
            color="cancel"
            variant="outlined"
            rounded="xl"
            class="action-btn cancel-btn"
            :disabled="step == 1"
            @click="prev"
            >Zurück
          </v-btn>
          <v-btn
            :append-icon="mdiArrowRight"
            color="do"
            variant="flat"
            size="large"
            rounded="xl"
            class="action-btn confirm-btn"
            :loading="isValidating"
            :disabled="!stepValidity[step]"
            @click="onNext"
          >
            {{ step === pages.length ? "Bestellen" : "Weiter" }}
          </v-btn>
        </div>
      </template>
    </v-stepper>
  </common-dialog>
</template>

<script setup lang="ts">
import { mdiArrowLeft, mdiArrowRight } from "@mdi/js";
import { inject, ref, watch } from "vue";

import jobService from "@/api/jobService.ts";
import serverService from "@/api/serverService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import LoadbalancerOrderGeneral from "@/components/Loadbalancer/LoadbalancerOrderGeneral.vue";
import LoadbalancerOrderListener from "@/components/Loadbalancer/LoadbalancerOrderListener.vue";
import LoadbalancerOrderServerPools from "@/components/Loadbalancer/LoadbalancerOrderServerPools.vue";
import LoadbalancerOrderSummary from "@/components/Loadbalancer/LoadbalancerOrderSummary.vue";
import LoadbalancerOrder from "@/types/LoadbalancerOrder.ts";

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const LoadbalancerOrderProp = ref<LoadbalancerOrder>(
  createDefaultLoadbalancerOrder()
);

interface member {
  name: string;
  ip: string;
  ports: number[];
}

const dialog = ref(false);
const loading = ref(false);
const step = ref(1);
const isValidating = ref(false);
const stepRefs = ref<Record<number, any>>({});
const stepValidity = ref<Record<number, boolean>>({});
const servers = ref<member[]>([]);
const hasServers = ref(true);
// protocol shared between ServerPools and Listener
const protocol = ref<"tcp" | "http" | "https">("http");

const pages = ref([
  { title: "Allgemeines" },
  { title: "Server Pool" },
  { title: "Listener" },
  { title: "Zusammenfassung" },
]);

function close() {
  dialog.value = false;
  step.value = 1;
  LoadbalancerOrderProp.value = createDefaultLoadbalancerOrder();
  protocol.value = "http";
  stepValidity.value = {};
  unregisterOpenDialog?.();
}

async function onNext() {
  isValidating.value = true;
  try {
    if (step.value === pages.value.length) {
      submitOrder();
    } else {
      step.value++;
    }
  } finally {
    isValidating.value = false;
  }
}

function submitOrder() {
  jobService
    .startJob(
      loading,
      "LOADBALANCER_F5",
      -1,
      JSON.parse(JSON.stringify(LoadbalancerOrderProp.value))
    )
    .then(() => {
      close();
    });
}

function createDefaultLoadbalancerOrder(): LoadbalancerOrder {
  return new LoadbalancerOrder(
    null,
    "",
    [
      {
        port: 443,
        server_pool: "default",
        listener_type: "http",
        clientside_tls: false,
        serverside_tls: false,
        x_forwarded_for: true,
        persistence: "cookie",
        wss: false,
      },
    ],
    [
      {
        member: [],
        monitors: [
          {
            type: "http",
            method: "GET",
            path: "/status",
            headers: { Host: "example.muenchen.de" },
            receive_string: "200 OK",
          },
        ],
        loadbalancing_mode: "round-robin",
      },
    ]
  );
}

function getServers() {
  hasServers.value = true;
  serverService
    .getFullServersByAppserviceId(
      loading,
      LoadbalancerOrderProp.value.appservice!.id
    )
    .then((response) => {
      if (response.length === 0) {
        hasServers.value = false;
        servers.value = [];
        return;
      }
      servers.value = response.map((server) => ({
        name: server.fqdn,
        ip: server.guestToolsIpAddress,
        ports: [80],
      }));
    });
}

watch(
  () => LoadbalancerOrderProp.value.appservice,
  (newVal) => {
    if (!newVal) {
      servers.value = [];
      return;
    }
    if (newVal) {
      LoadbalancerOrderProp.value.server_pools[0].member = [];
      getServers();
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.action-btn {
  min-width: 120px;
  height: 44px;
  border-radius: 12px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: 0.5px;
  transition: all 0.3s ease;
}

.cancel-btn {
  border: 2px solid #90a4ae;
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-cancel));
}

.cancel-btn:hover {
  /* noinspection CssUnresolvedCustomProperty */
  background: rgb(var(--v-theme-bg_light));
  border-color: #90a4ae;
  transform: translateY(-1px);
}

.confirm-btn {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.3);
  color: white !important;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #2196f3 0%, #1976d2 100%);
}
</style>
