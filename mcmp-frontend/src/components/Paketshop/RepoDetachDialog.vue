<template>
  <common-dialog
    v-model="dialog"
    :title="title"
    :icon="mdiLinkOff"
    max-width="700"
    show-actions
    show-change-warning
    :submit-activated="canSubmit"
    :check-for-enabled-actions="['PAKETSHOP_REPO_DETACH']"
    @dialog-cancel="close"
    @dialog-confirm="submit"
  >
    <template #activator="{ props: slotProps }">
      <v-tooltip
        :text="disabledReason || title"
        location="top"
      >
        <template #activator="{ props: tooltipProps }">
          <span
            v-bind="tooltipProps"
            style="display: inline-flex"
          >
            <v-btn
              v-bind="slotProps"
              icon
              :flat="!props.small"
              :variant="props.small ? 'text' : undefined"
              :size="props.small ? 'small' : undefined"
              :density="props.small ? 'compact' : undefined"
              :disabled="!!disabledReason"
              :aria-label="title"
              @click="open"
            >
              <v-icon :icon="mdiLinkOff" />
            </v-btn>
          </span>
        </template>
      </v-tooltip>
    </template>

    <v-form v-model="isValid">
      <common-alert
        color="accent"
        class="mb-4"
      >
        <div class="confirm-entity-label">
          {{ props.repositories.length > 1 ? "Repositories:" : "Repository:" }}
        </div>
        <div
          v-for="repo in props.repositories"
          :key="repo.id"
          class="confirm-entity-name"
        >
          {{ repo.name }}
        </div>
      </common-alert>

      <common-alert
        v-if="props.server"
        color="accent"
        class="mb-4"
      >
        <div class="confirm-entity-label">Server:</div>
        <div class="confirm-entity-name">{{ props.server.name }}</div>
      </common-alert>
      <v-autocomplete
        v-else
        v-model="selectedFqdns"
        :items="serverOptions"
        :loading="loadingServers"
        item-title="name"
        item-value="fqdn"
        label="Server*"
        multiple
        chips
        closable-chips
        rounded
        clearable
        variant="outlined"
        :rules="[
          (v: string[]) =>
            (v && v.length > 0) ||
            'Es muss mindestens ein Server ausgewählt werden.',
        ]"
      >
        <template #no-data>
          <div class="px-4 py-2">Keine Server verfügbar</div>
        </template>
      </v-autocomplete>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository";
import type { ServerFqdn } from "@/types/ServerFqdn";

import { mdiLinkOff } from "@mdi/js";
import { computed, inject, ref } from "vue";

import jobService from "@/api/jobService";
import serverService from "@/api/serverService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";

interface ServerOption {
  name: string;
  fqdn: string;
}

const props = withDefaults(
  defineProps<{
    repositories: Repository[];
    /** Fixed server to detach from. If not set, the servers that have the repositories attached can be selected. */
    server?: ServerOption | null;
    disabledReason?: string;
    /** Compact text button, e.g. inside a table row. */
    small?: boolean;
  }>(),
  {
    server: null,
    disabledReason: "",
    small: false,
  }
);

const emit = defineEmits<(e: "order-done") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const dialog = ref(false);
const loading = ref(false);
const isValid = ref(false);
const selectedFqdns = ref<string[]>([]);
const loadingServers = ref(false);
/** repository id -> FQDNs of the servers it is attached to (and the user can edit) */
const attachedFqdnsByRepo = ref<Map<number, string[]>>(new Map());
const serverOptions = ref<ServerFqdn[]>([]);

async function loadAttachedServers() {
  attachedFqdnsByRepo.value = new Map();
  serverOptions.value = [];
  const perRepo = await Promise.all(
    props.repositories.map(async (repo) => ({
      repoId: repo.id,
      servers: await serverService.getRepoTargetServersByRepositoryIds(
        loadingServers,
        [repo.id]
      ),
    }))
  );
  const union = new Map<string, ServerFqdn>();
  perRepo.forEach(({ repoId, servers }) => {
    attachedFqdnsByRepo.value.set(
      repoId,
      servers.map((s) => s.fqdn)
    );
    servers.forEach((s) => union.set(s.fqdn, s));
  });
  serverOptions.value = [...union.values()].sort((a, b) =>
    a.name.localeCompare(b.name)
  );
}

/** Only the selected servers that actually have this repository attached. */
function fqdnsToDetachFor(repo: Repository): string[] {
  if (props.server) return [props.server.fqdn];
  const attached = attachedFqdnsByRepo.value.get(repo.id) ?? [];
  return selectedFqdns.value.filter((fqdn) => attached.includes(fqdn));
}

const title = computed(() =>
  props.repositories.length > 1
    ? "Repositories von Server entfernen"
    : "Repository von Server entfernen"
);

const fqdnsToDetach = computed<string[]>(() =>
  props.server ? [props.server.fqdn] : selectedFqdns.value
);

const canSubmit = computed(
  () =>
    isValid.value &&
    !loading.value &&
    props.repositories.length > 0 &&
    fqdnsToDetach.value.length > 0
);

function open() {
  selectedFqdns.value = [];
  dialog.value = true;
  registerOpenDialog?.();
  if (!props.server) void loadAttachedServers();
}

function close() {
  dialog.value = false;
  unregisterOpenDialog?.();
}

async function submit() {
  // one job per repository, the backend detaches a single repository per job
  // repositories that are not attached to any of the selected servers are skipped
  const results = await Promise.allSettled(
    props.repositories
      .map((repo) => ({ repo, fqdns: fqdnsToDetachFor(repo) }))
      .filter(({ fqdns }) => fqdns.length > 0)
      .map(({ repo, fqdns }) =>
        jobService.startJob(loading, "PAKETSHOP_REPO_DETACH", -1, {
          name: repo.name,
          control_paketshop_repos_systems: fqdns,
        })
      )
  );
  if (results.some((r) => r.status === "fulfilled")) {
    emit("order-done");
  }
  close();
}
</script>
