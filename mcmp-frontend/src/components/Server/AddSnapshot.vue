<template>
  <v-tooltip
    location="bottom"
    :text="tooltipText"
    :open-on-hover="true"
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          icon
          :disabled="isDisabled"
          variant="flat"
          aria-label="Snapshot erstellen"
          @click="openDialog"
        >
          <v-icon>{{ isBatchOperation ? mdiTagPlus : mdiPlus }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    :model-value="dialog"
    max-width="600"
    title="Snapshot erstellen"
    :icon="mdiPencil"
    show-actions
    submit-activated
    show-change-warning
    :check-for-enabled-actions="['VMWARE_CREATE_SNAPSHOT']"
    @dialog-cancel="close()"
    @dialog-confirm="save()"
  >
    <v-form ref="form">
      <div v-if="serverPowerOnInDialog">
        <h4>
          Server vor Erstellung des Snapshots herunterfahren um Datenkonsistenz
          sicherzustellen:
        </h4>
        <v-select
          v-model="withShutdown"
          :items="[
            { title: 'Ja', value: true },
            { title: 'Nein', value: false },
          ]"
          item-title="title"
          item-value="value"
          :menu-props="{ persistent: true, closeOnContentClick: true }"
        />
      </div>
      <h4>Tage:</h4>
      <v-number-input
        v-model="days"
        :min="1"
        :max="10"
        :step="1"
        control-variant="split"
        persistent-hint
        hint="Aufbewahrungszeit des Snapshots bis zur automatischen Löschung."
      />
      <br />
      <h4>Beschreibung:</h4>
      <v-text-field
        v-model="description"
        :maxlength="50"
        :rules="[
          validationRules.maxLengthRule(50, 'Maximal 50 Zeichen erlaubt.'),
        ]"
        :counter="50"
      />
      <common-alert
        v-if="withShutdown"
        color="notice_red"
      >
        <h4>Hinweis:</h4>
        Vor der Erstellung des Snapshots wird der Server heruntergefahren um
        eine mögliche Dateninkonsistenz zu vermeiden. Dadurch kommt es zu einer
        kurzen Downtime. Nach der Erstellung wird der Server automatisch wieder
        hochgefahren.
      </common-alert>
      <common-alert
        v-if="!withShutdown"
        color="notice_red"
      >
        <h4>Hinweis:</h4>
        Vor der Erstellung des Snapshots wird der Server
        <strong>NICHT</strong> heruntergefahren. Dies kann es zu einer möglichen
        Dateninkonsistenz führen.
      </common-alert>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Server from "@/types/Server.ts";

import { mdiPencil, mdiPlus, mdiTagPlus } from "@mdi/js";
import { computed, inject, ref } from "vue";

import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules";

const props = defineProps<{
  server?: Server;
  snapshotCount?: number;
  isBatchOperation?: boolean;
  selectedServerIds?: number[];
  selectedServers?: Server[];
  parentAllSelectedServersEligible?: boolean;
  parentDisabledTooltip?: string;
}>();

const validationRules = useRules();
const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const form = ref<HTMLFormElement>();
const dialog = ref(false);
const days = ref(2);
const withShutdown = ref(true);
const description = ref("");
const loading = ref(false);

const serverPowerOnInDialog = computed(() => {
  if (props.isBatchOperation) {
    return (props.selectedServers || []).some(
      (s) => s?.powerState === "poweredOn"
    );
  }
  return props.server?.powerState === "poweredOn";
});

const isDisabled = computed(() => {
  if (props.isBatchOperation) {
    if (!props.parentAllSelectedServersEligible) return true;
    const ids = props.selectedServerIds || [];
    return ids.length === 0;
    // enabled when parent says eligible and at least one selected
  }
  // Einzelserver-Logik wie vorher
  if (!props.server) return true;
  return (
    (props.snapshotCount ?? 0) > 0 ||
    props.server.cloud?.cloudType !== "VCENTER"
  );
});

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      if (props.isBatchOperation) {
        // Batch: start job for each selected server id. Parent guarantees eligibility.
        const ids = props.selectedServerIds || [];
        ids.forEach((id) => {
          jobService.startJob(loading, "VMWARE_CREATE_SNAPSHOT", id, {
            duration: days.value,
            description: description.value,
            withShutdown: withShutdown.value,
          });
        });
      } else if (props.server) {
        jobService.startJob(
          loading,
          "VMWARE_CREATE_SNAPSHOT",
          props.server.id,
          {
            duration: days.value,
            description: description.value,
            withShutdown: withShutdown.value,
          }
        );
      }

      dialog.value = false;
      unregisterOpenDialog?.();
      resetForm();
    }
  });
}

function openDialog() {
  // batch: check selection and parent eligibility
  if (props.isBatchOperation) {
    if ((props.selectedServerIds || []).length === 0) {
      dialog.value = false;
      return;
    }
    if (!props.parentAllSelectedServersEligible) {
      dialog.value = false;
      return;
    }
    dialog.value = true;
    registerOpenDialog?.();
  } else {
    if ((props.snapshotCount ?? 0) > 0) {
      dialog.value = false;
    } else {
      dialog.value = true;
      registerOpenDialog?.();
    }
  }
}

function close() {
  dialog.value = false;
  unregisterOpenDialog?.();
  resetForm();
}

function resetForm() {
  days.value = 2;
  description.value = "";
}

const tooltipText = computed(() => {
  // parent-driven disabled reason takes precedence
  if (props.isBatchOperation && !props.parentAllSelectedServersEligible) {
    return (
      props.parentDisabledTooltip ||
      "Nicht berechtigt oder Server nicht verwaltet."
    );
  }

  if (props.isBatchOperation) {
    if ((props.selectedServerIds || []).length === 0)
      return "Keine Server ausgewählt.";
    // all selected are eligible per parent
    return `Snapshot für ${props.selectedServerIds?.length || 0} Server erstellen`;
  }

  if ((props.snapshotCount ?? 0) > 0) {
    return "Es ist max 1 Snapshot erlaubt.";
  }
  if (props.server?.cloud?.cloudType != "VCENTER") {
    return "Snapshots sind für diesen Server nicht möglich.";
  }
  return "Snapshot erstellen"; // Standardtext, wenn Button aktiv
});
</script>
