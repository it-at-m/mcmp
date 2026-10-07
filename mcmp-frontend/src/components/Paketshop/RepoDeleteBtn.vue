<template>
  <common-dialog
    :model-value="dialog"
    :title="title"
    :icon="mdiDelete"
    max-width="600"
    show-actions
    :submit-activated="true"
    show-change-warning
    :check-for-enabled-actions="['PAKETSHOP_REPO_DELETE']"
    @dialog-confirm="onDialogConfirm"
    @dialog-cancel="onDialogCancel"
  >
    <template #activator="{ props: activatorProps }">
      <v-tooltip
        :text="tooltipText"
        location="bottom"
      >
        <template #activator="{ props: tooltipProps }">
          <span
            v-bind="tooltipProps"
            style="display: inline-flex"
          >
            <v-btn
              v-bind="activatorProps"
              :disabled="loading || isDisabled"
              color="btn_red"
              :loading="loading"
              class="material-action-btn"
              variant="flat"
              icon
              size="small"
              :aria-label="title"
              @click="openDialog"
            >
              <v-icon
                :icon="mdiDelete"
                size="x-large"
              />
            </v-btn>
          </span>
        </template>
      </v-tooltip>
    </template>

    <common-alert color="accent">
      <div class="confirm-entity-label">
        {{
          repositories.length > 1
            ? "Ausgewählte Repositories:"
            : "Ausgewähltes Repository:"
        }}
      </div>
      <div
        v-for="repo in repositories"
        :key="repo.id"
        class="confirm-entity-name"
      >
        {{ repo.name }}
      </div>
    </common-alert>
    <br />
    {{ questionText }}
  </common-dialog>

  <dialog-extra-sure
    v-if="extraSureDialog"
    v-model="extraSureDialog"
    :title="title"
    :text="questionText"
    :checkbox-text="
      repositories.length > 1
        ? 'Ich bin mir sicher, dass ich die ausgewählten Repositories löschen möchte.'
        : 'Ich bin mir sicher, dass ich dieses Repository löschen möchte.'
    "
    :icon="mdiDelete"
    @do="onExtraSureDialogConfirm"
    @cancel="onDialogCancel"
  />
</template>

<script setup lang="ts">
import type { RepositoryLockStatus } from "@/types/Repository";

import { mdiDelete } from "@mdi/js";
import { computed, inject, ref } from "vue";

import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import DialogExtraSure from "@/components/common/dialogExtraSure.vue";

interface DeletableRepository {
  id: number;
  name: string;
  lockStatus: RepositoryLockStatus;
}

const props = withDefaults(
  defineProps<{
    repositories: DeletableRepository[];
    disabled?: boolean;
    disabledTooltip?: string;
  }>(),
  {
    disabled: false,
    disabledTooltip: "",
  }
);

const emit = defineEmits<(e: "deleted") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const loading = ref(false);
const dialog = ref(false);
const extraSureDialog = ref(false);

const title = computed(() =>
  props.repositories.length > 1 ? "Repositories löschen" : "Repository löschen"
);
const questionText = computed(() =>
  props.repositories.length > 1
    ? `Wollen Sie die ${props.repositories.length} ausgewählten Repositories wirklich löschen?`
    : "Wollen Sie dieses Repository wirklich löschen?"
);

const disableReason = computed(() => {
  if (props.disabled) return props.disabledTooltip;
  if (props.repositories.length === 0) return "Keine Repositories ausgewählt.";
  if (props.repositories.some((r) => r.lockStatus === "LOCKED"))
    return "Gesperrte Repositories können nicht gelöscht werden.";
  return "";
});
const isDisabled = computed(() => !!disableReason.value);
const tooltipText = computed(() => disableReason.value || title.value);

function openDialog() {
  if (isDisabled.value) return;
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
  void makeJobCalls();
}

function onDialogCancel() {
  dialog.value = false;
  extraSureDialog.value = false;
  unregisterOpenDialog?.();
}

async function makeJobCalls() {
  // one job per repository, the backend only deletes a single repository per job
  await Promise.allSettled(
    props.repositories.map((repo) =>
      jobService.startJob(loading, "PAKETSHOP_REPO_DELETE", -1, {
        name: repo.name,
      })
    )
  );
  emit("deleted");
}
</script>
