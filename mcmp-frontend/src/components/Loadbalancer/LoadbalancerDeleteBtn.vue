<template>
  <common-dialog
    v-if="testEnv"
    :model-value="dialog"
    title="Loadbalancer abbauen"
    :icon="mdiDelete"
    max-width="600"
    show-actions
    :submit-activated="true"
    show-change-warning
    :check-for-enabled-actions="['LOADBALANCER_F5_DELETE']"
    @dialog-confirm="onDialogConfirm"
    @dialog-cancel="onDialogCancel"
  >
    <template #activator="{ props: activatorProps }">
      <v-tooltip
        :text="isDeleteDisabled ? disableReason : 'Loadbalancer abbauen'"
        location="bottom"
        aria-label="Loadbalancer abbauen"
      >
        <template #activator="{ props: tooltipProps }">
          <span
            v-bind="tooltipProps"
            class="tooltip-activator"
            style="display: inline-flex"
          >
            <v-btn
              v-bind="activatorProps"
              :disabled="loading || isDeleteDisabled"
              color="btn_red"
              :loading="loading"
              class="material-action-btn"
              variant="flat"
              icon
              size="small"
              alt="Loadbalancer abbauen"
              aria-label="Loadbalancer abbauen"
              @click="openDialog"
            >
              <v-icon
                :icon="mdiDelete"
                size="x-large"
                role="img"
              />
            </v-btn>
          </span>
        </template>
      </v-tooltip>
    </template>

    <common-alert color="accent">
      <div class="confirm-entity-label">Ausgewählter Loadbalancer:</div>
      <div class="confirm-entity-name">{{ lb.name }}</div>
    </common-alert>
    <br />
    Wollen Sie diesen Loadbalancer wirklich abbauen?
  </common-dialog>

  <dialog-extra-sure
    v-if="testEnv && extraSureDialog"
    v-model="extraSureDialog"
    title="Loadbalancer abbauen"
    text="Wollen Sie diesen Loadbalancer wirklich abbauen?"
    checkbox-text="Ich bin mir sicher, dass ich diesen Loadbalancer abbauen möchte."
    :icon="mdiDelete"
    @do="onExtraSureDialogConfirm"
    @cancel="onDialogCancel"
  />
</template>

<script setup lang="ts">
import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";

import { mdiDelete } from "@mdi/js";
import { computed, inject, onMounted, ref } from "vue";

import jobService from "@/api/jobService";
import testenvService from "@/api/testenvService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import DialogExtraSure from "@/components/common/dialogExtraSure.vue";

const props = defineProps<{
  lb: LoadbalancerDetail;
}>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const loading = ref(false);
const dialog = ref(false);
const extraSureDialog = ref(false);
const testEnv = ref(false);
const loadingTestEnv = ref(false);

onMounted(() => {
  testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
    testEnv.value = enabled;
  });
});

const disableReason = computed(() => {
  if (props.lb.wafEnabled)
    return "Abbau ist bei aktivierter WAF nicht möglich.";
  return "";
});

const isDeleteDisabled = computed(() => !!disableReason.value);

function openDialog() {
  if (isDeleteDisabled.value) return;
  dialog.value = true;
  registerOpenDialog?.();
}

function onDialogConfirm() {
  dialog.value = false;
  extraSureDialog.value = true;
}

function onExtraSureDialogConfirm() {
  extraSureDialog.value = false;
  unregisterOpenDialog?.();
  makeJobCall();
}

function onDialogCancel() {
  dialog.value = false;
  extraSureDialog.value = false;
  unregisterOpenDialog?.();
}

function makeJobCall() {
  jobService.startJob(loading, "LOADBALANCER_F5_DELETE", -1, {
    lb_virtual_server_id: props.lb.id,
  });
}
</script>
