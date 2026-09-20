<template>
  <common-dialog
    v-model="dialog"
    :loading="loading"
    title="Openshift Namespace Bestellung"
    max-width="1200"
    submit-activated
    show-change-warning
    :check-for-enabled-actions="['OPENSHIFT_NAMESPACE_ORDER']"
    @dialog-cancel="close"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        flat
        @click="registerOpenDialog"
        >Namespace Bestellen
      </v-btn>
    </template>
    <v-stepper
      v-model="step"
      :items="pages"
      class="pa-4"
    >
      <template #item.1>
        <openshift-namespace-order-general
          :ref="(el) => (stepRefs[1] = el)"
          v-model:order="orderProp"
          @validation-change="(val) => (stepValidity[1] = val)"
        />
      </template>
      <template #item.2>
        <openshift-namespace-order-namespace
          :ref="(el) => (stepRefs[2] = el)"
          v-model:order="orderProp"
          @validation-change="(val) => (stepValidity[2] = val)"
        />
      </template>
      <template #item.3>
        <openshift-namespace-order-network
          :ref="(el) => (stepRefs[3] = el)"
          v-model:order="orderProp"
          @validation-change="(val) => (stepValidity[3] = val)"
        />
      </template>
      <template #item.4>
        <openshift-namespace-order-hardware
          :ref="(el) => (stepRefs[4] = el)"
          v-model:order="orderProp"
          @validation-change="(val) => (stepValidity[4] = val)"
        />
      </template>
      <template #item.5>
        <openshift-namespace-order-optional
          :ref="(el) => (stepRefs[5] = el)"
          v-model:order="orderProp"
          @validation-change="(val) => (stepValidity[5] = val)"
        />
      </template>

      <template #actions="{ prev }">
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
import type { ComponentPublicInstance } from "vue";

import { mdiArrowLeft, mdiArrowRight } from "@mdi/js";
import { inject, ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import OpenshiftNamespaceOrderGeneral from "@/components/Openshift/OpenshiftNamespaceOrderGeneral.vue";
import OpenshiftNamespaceOrderHardware from "@/components/Openshift/OpenshiftNamespaceOrderHardware.vue";
import OpenshiftNamespaceOrderNamespace from "@/components/Openshift/OpenshiftNamespaceOrderNamespace.vue";
import OpenshiftNamespaceOrderNetwork from "@/components/Openshift/OpenshiftNamespaceOrderNetwork.vue";
import OpenshiftNamespaceOrderOptional from "@/components/Openshift/OpenshiftNamespaceOrderOptional.vue";
import OpenshiftNamespaceOrder from "@/types/OpenshiftNamespaceOrder.ts";

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const orderProp = ref<OpenshiftNamespaceOrder>(createDefaultOrder());

const dialog = ref(false);
const loading = ref(false);
const step = ref(1);
const isValidating = ref(false);
const stepRefs = ref<Record<number, Element | ComponentPublicInstance | null>>(
  {}
);
const stepValidity = ref<Record<number, boolean>>({});

const pages = ref([
  { title: "Allgemeines" },
  { title: "Namespace" },
  { title: "Netzwerk" },
  { title: "Hardware" },
  { title: "Optionales" },
]);

function createDefaultOrder(): OpenshiftNamespaceOrder {
  return new OpenshiftNamespaceOrder(
    null,
    "",
    "",
    null,
    null,
    "100m",
    "1Gi",
    0,
    2,
    "no",
    ""
  );
}

function close() {
  dialog.value = false;
  step.value = 1;
  orderProp.value = createDefaultOrder();
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
  const order = orderProp.value;
  const payload = {
    appserviceId: order.appservice?.id,
    namespaceName: order.namespaceName,
    description: order.description,
    nodeSelector: order.nodeSelector,
    ingress: order.ingress,
    cpuLimit: order.cpuLimit,
    memoryLimit: order.memoryLimit,
    pvLimit: order.pvLimit,
    podLimit: order.podLimit,
    logging: order.logging,
    quayOrga: order.quayOrga,
  };
  jobService
    .startJob(loading, "OPENSHIFT_NAMESPACE_ORDER", -1, payload)
    .then(() => {
      close();
    });
}
</script>

<style scoped>
.confirm-btn {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.3);
  color: white !important;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #2196f3 0%, #1976d2 100%);
}
</style>
