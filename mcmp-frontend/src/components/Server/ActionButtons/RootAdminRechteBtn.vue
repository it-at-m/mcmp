<template>
  <v-tooltip
    :text="tooltip"
    location="bottom"
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          :disabled="disabled || loading"
          :color="color"
          :loading="loading"
          class="material-action-btn"
          variant="flat"
          icon
          size="small"
          :aria-label="tooltip"
          @click="onBtnClick"
        >
          <v-icon
            :icon="icon"
            size="x-large"
            class="InnerIcon"
          />
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    :model-value="dialog"
    :title="confirmDialogTitle"
    :icon="icon"
    max-width="600"
    show-actions
    :submit-activated="validated"
    show-change-warning
    :check-for-enabled-actions="jobToCall ? [jobToCall] : undefined"
    @dialog-confirm="onDialogConfirm"
    @dialog-cancel="onDialogCancel"
  >
    <common-alert
      v-if="server || (isBatchOperation && selectedServerIds?.length)"
      color="accent"
    >
      <div
        v-if="server"
        class="server-info-label"
      >
        Ausgewählter Server:
      </div>
      <div
        v-if="server"
        class="server-name"
      >
        {{ server.name }}
      </div>

      <div
        v-if="isBatchOperation"
        class="server-info-label"
      >
        Ausgewählte Server:
      </div>
      <div
        v-if="isBatchOperation"
        class="server-name"
      >
        {{ selectedServerIds?.length }} Server
      </div>

      <div
        v-if="isBatchOperation && selectedServers && selectedServers.length"
        class="mt-2"
      >
        <ul class="pl-4">
          <li
            v-for="s in selectedServers.filter((x) =>
              selectedServerIds?.includes(x.id)
            )"
            :key="s.id"
          >
            {{ s.name }}
          </li>
        </ul>
      </div>
    </common-alert>

    <br />
    {{ confirmDialogText || "Wollen Sie die Aktion ausführen?" }}

    <v-form ref="form">
      <v-checkbox
        v-model="isOtherUser"
        label="Für einen anderen Nutzer bestellen"
        @input="tryValidation"
      />
      <v-text-field
        v-if="isOtherUser"
        v-model="otherUsername"
        label="Benutzername"
        placeholder="max.mustermann"
        variant="outlined"
        :rules="[
          rules.notEmptyRule('Benutzername ist erforderlich'),
          rules.regexRule(
            /^[a-zA-Z0-9][a-zA-Z0-9-_.]*$/,
            'Benutzername muss alphanumerisch sein und darf nur - _ oder . enthalten'
          ),
        ]"
        @input="tryValidation"
      ></v-text-field>
    </v-form>
    <!-- Link Section -->
    <div
      v-if="confirmDialogLink"
      class="link-section"
    >
      <v-divider class="mb-4" />
      <a
        :href="confirmDialogLink"
        target="_blank"
        class="info-link"
      >
        <v-icon
          :icon="mdiOpenInNew"
          size="16"
          class="mr-2"
        />
        {{ confirmDialogLinkText || "Weitere Hinweise hier" }}
      </a>
    </div>

    <!-- Date Input (hidden for now) -->
    <div
      v-if="false"
      class="date-section"
    >
      <v-divider class="mb-4" />
      <v-date-input
        v-model="rootUntilDate"
        label="Root bis (optional)"
        :min="
          new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().split('T')[0]
        "
        :max="
          new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
            .toISOString()
            .split('T')[0]
        "
        class="mt-4"
        clearable
        landscape
        :display-format="
          (date: any) =>
            date.toLocaleDateString('de-DE', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
            })
        "
      />
    </div>
  </common-dialog>
</template>

<script setup lang="ts">
import { mdiOpenInNew } from "@mdi/js";
import { inject, ref } from "vue";

import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";
import Server from "@/types/Server";

const props = defineProps<{
  color?: string;
  icon: string;
  disabled?: boolean;
  tooltip: string;
  server?: Server;
  selectedServerIds?: number[];
  selectedServers?: any[];
  jobToCall?: string;
  showConfirmDialog?: boolean;
  confirmDialogTitle?: string;
  confirmDialogText?: string;
  confirmDialogLink?: string;
  confirmDialogLinkText?: string;
  isBatchOperation?: boolean;
}>();

const emit = defineEmits<(e: "change") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const loading = ref(false);
const dialog = ref(false);
const rootUntilDate = ref<string | null>(null);
const isOtherUser = ref(false);
const otherUsername = ref("");
const form = ref<HTMLFormElement>();
const validated = ref(true);
const rules = useRules();

function tryValidation() {
  if (!isOtherUser.value) {
    validated.value = true;
    return;
  }
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
}

function onBtnClick() {
  if (props.showConfirmDialog) {
    dialog.value = true;
    registerOpenDialog?.();
  } else {
    makeJobCall();
  }
}

function onDialogConfirm() {
  dialog.value = false;
  unregisterOpenDialog?.();
  makeJobCall();
}

function onDialogCancel() {
  dialog.value = false;
  unregisterOpenDialog?.();
}

// determine action identifier by server OS if jobToCall not explicitly provided
const getActionIdentifierForServer = (server?: Server) => {
  const os = (server?.os || "").toLowerCase();
  if (os.includes("windows")) return "WINDOWS_TEMP_ADMIN";
  return "LINUX_TEMP_ROOT";
};

function makeJobCall() {
  // if batch operation, iterate over selectedServerIds
  if (props.isBatchOperation) {
    const ids =
      props.selectedServerIds ?? props.selectedServers?.map((s) => s.id) ?? [];
    if (ids.length === 0) {
      return;
    }

    loading.value = true;
    const promises = ids.map((id) => {
      const server = props.selectedServers?.find((s) => s.id === id);
      const action = props.jobToCall ?? getActionIdentifierForServer(server);
      const extraVars: Record<string, any> = {
        duration: "3 days",
      };
      if (isOtherUser.value) extraVars.other_username = otherUsername.value;
      return jobService.startJob(loading, action, id, extraVars);
    });

    Promise.all(promises)
      .then(() => emit("change"))
      .catch((err) => console.error("Batch job error", err))
      .finally(() => (loading.value = false));

    return;
  }

  if (!props.jobToCall) {
    return;
  }

  // format duration in awx format e.g. 3 days or 72 hours
  const duration = "3 days";
  /*if (rootUntilDate.value) {
    const today = new Date();
    const until = new Date(rootUntilDate.value);
    const diffMs = until.getTime() - today.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    duration = `${diffDays} days`;
  }*/

  jobService
    .startJob(loading, props.jobToCall, props.server!.id, {
      duration: duration,
      other_username: isOtherUser.value ? otherUsername.value : undefined,
    })
    .then(() => {
      emit("change");
    })
    .catch((err) => console.error("Job start error", err));
}
</script>

<style scoped>
.material-action-btn {
  border-radius: 50% !important;
  margin: 0 4px;
  width: 33.35px !important;
  height: 33.35px !important;
  box-shadow:
    0 3px 1px -2px rgba(0, 0, 0, 0.2),
    0 2px 2px 0 rgba(0, 0, 0, 0.14),
    0 1px 5px 0 rgba(0, 0, 0, 0.12);
  transition: box-shadow 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.material-action-btn:hover {
  box-shadow:
    0 2px 4px -1px rgba(0, 0, 0, 0.2),
    0 4px 5px 0 rgba(0, 0, 0, 0.14),
    0 1px 10px 0 rgba(0, 0, 0, 0.12);
}

.link-section {
  margin-top: 16px;
}

.info-link {
  display: inline-flex;
  align-items: center;
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
  font-weight: 500;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.2s ease;
  background: rgba(25, 118, 210, 0.04);
  border: 1px solid rgba(25, 118, 210, 0.12);
}

.info-link:hover {
  background: rgba(25, 118, 210, 0.08);
  border-color: rgba(25, 118, 210, 0.24);
  text-decoration: none;
  transform: translateY(-1px);
}

.date-section {
  margin-top: 16px;
}

@keyframes dialogSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.server-info-label {
  font-size: 0.875rem;
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-accent));
  font-weight: 500;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.server-name {
  font-size: 1.25rem;
  font-weight: 700;
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-text));
  padding: 4px 0;
  word-break: break-word;
}

/* Responsive Design Update */
@media (max-width: 700px) {
  .modern-dialog :deep(.v-overlay__content) {
    margin: 16px;
    max-width: calc(100vw - 32px);
  }

  .server-name {
    font-size: 1.125rem;
  }
}
</style>
