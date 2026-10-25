<template>
  <common-dialog
    :model-value="dialog"
    title="Namespace abbauen"
    :icon="mdiDelete"
    max-width="600"
    show-actions
    :submit-activated="true"
    show-change-warning
    :check-for-enabled-actions="['OPENSHIFT_NAMESPACE_DELETE']"
    @dialog-confirm="onDialogConfirm"
    @dialog-cancel="onDialogCancel"
  >
    <template #activator="{ props: activatorProps }">
      <v-tooltip
        text="Namespace abbauen"
        location="bottom"
        aria-label="Namespace abbauen"
      >
        <template #activator="{ props: tooltipProps }">
          <span
            v-bind="tooltipProps"
            class="tooltip-activator"
            style="display: inline-flex"
          >
            <v-btn
              v-bind="activatorProps"
              :disabled="loading"
              color="btn_red"
              :loading="loading"
              class="material-action-btn"
              variant="flat"
              icon
              size="small"
              alt="Namespace abbauen"
              aria-label="Namespace abbauen"
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
      <div class="confirm-entity-label">Ausgewählter Namespace:</div>
      <div class="confirm-entity-name">{{ namespace.name }}</div>
    </common-alert>
    <br />
    Wollen Sie diesen Namespace wirklich abbauen?
  </common-dialog>

  <dialog-extra-sure
    v-if="extraSureDialog"
    v-model="extraSureDialog"
    title="Namespace abbauen"
    text="Wollen Sie diesen Namespace wirklich abbauen?"
    checkbox-text="Ich bin mir sicher, dass ich diesen Namespace abbauen möchte."
    :icon="mdiDelete"
    @do="onExtraSureDialogConfirm"
    @cancel="onDialogCancel"
  />
</template>

<script setup lang="ts">
import type { OpenshiftNamespaceDetail } from "@/types/OpenshiftNamespaceDetail";

import { mdiDelete } from "@mdi/js";
import { inject, ref } from "vue";

import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import DialogExtraSure from "@/components/common/dialogExtraSure.vue";

const props = defineProps<{
  namespace: OpenshiftNamespaceDetail;
}>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const loading = ref(false);
const dialog = ref(false);
const extraSureDialog = ref(false);

function openDialog() {
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
  jobService.startJob(loading, "OPENSHIFT_NAMESPACE_DELETE", -1, {
    kubernetes_namespace_id: props.namespace.id,
  });
}
</script>
