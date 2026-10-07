<template>
  <common-dialog
    v-model="dialog"
    :title="title"
    :icon="mdiLinkPlus"
    max-width="700"
    show-actions
    show-change-warning
    :submit-activated="canSubmit"
    :check-for-enabled-actions="[
      'PAKETSHOP_REPO_ATTACH_OWNED_REPO',
      'PAKETSHOP_REPO_ATTACH_NOT_OWNED_REPO',
    ]"
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
              flat
              :disabled="!!disabledReason"
              :aria-label="title"
              @click="open"
            >
              <v-icon :icon="mdiLinkPlus" />
            </v-btn>
          </span>
        </template>
      </v-tooltip>
    </template>

    <v-form v-model="isValid">
      <common-alert
        v-if="props.repositories"
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
      <repo-autocomplete-field
        v-else
        v-model="selectedRepo"
      />

      <common-alert
        v-if="props.server"
        color="accent"
        class="mb-4 mt-3"
      >
        <div class="confirm-entity-label">Server:</div>
        <div class="confirm-entity-name">{{ props.server.name }}</div>
      </common-alert>
      <v-autocomplete
        v-else
        v-model="selectedServers"
        :items="serverItems"
        :loading="loadingServers"
        item-title="name"
        item-value="fqdn"
        label="Server*"
        return-object
        multiple
        chips
        closable-chips
        no-filter
        rounded
        clearable
        variant="outlined"
        :rules="[
          (v: ServerFqdn[]) =>
            (v && v.length > 0) ||
            'Es muss mindestens ein Server ausgewählt werden.',
        ]"
        @update:search="onServerSearch"
      >
        <template #no-data>
          <div class="px-4 py-2">Keine Server gefunden</div>
        </template>
      </v-autocomplete>

      <common-alert
        v-if="blockedReason"
        color="error"
        class="mt-3"
      >
        {{ blockedReason }}
      </common-alert>

      <v-checkbox
        v-model="enabled"
        label="Repository aktivieren"
        hide-details
      />
      <v-checkbox
        v-model="gpgcheck"
        label="GPG-Prüfung aktivieren (gpgcheck)"
        hide-details
      />
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository";
import type { ServerFqdn } from "@/types/ServerFqdn";

import { mdiLinkPlus } from "@mdi/js";
import { computed, inject, ref } from "vue";

import jobService from "@/api/jobService";
import repositoryService from "@/api/repositoryService";
import serverService from "@/api/serverService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import RepoAutocompleteField from "@/components/Paketshop/RepoAutocompleteField.vue";
import { repoAttachDisabledReason } from "@/composables/repoLockStatus";

interface ServerOption {
  name: string;
  fqdn: string;
}

const props = defineProps<{
  /** Fixed repositories to attach (batch). If not set, a repository can be searched. */
  repositories?: Repository[];
  /** Fixed server to attach to. If not set, all servers the user can attach repositories to can be searched. */
  server?: ServerOption | null;
  disabledReason?: string;
}>();

const emit = defineEmits<(e: "order-done") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const dialog = ref(false);
const loading = ref(false);
const isValid = ref(false);
const selectedRepo = ref<Repository | null>(null);
const selectedServers = ref<ServerFqdn[]>([]);
const searchResults = ref<ServerFqdn[]>([]);
const loadingServers = ref(false);
let searchTimeout: ReturnType<typeof setTimeout> | null = null;
const enabled = ref(true);

// keep already selected servers in the items, otherwise their chips disappear after a new search
const serverItems = computed(() => [
  ...selectedServers.value,
  ...searchResults.value.filter(
    (s) => !selectedServers.value.some((sel) => sel.fqdn === s.fqdn)
  ),
]);

async function loadServers(search: string) {
  searchResults.value = await serverService.getRepoTargetServers(
    loadingServers,
    search
  );
}

function onServerSearch(search: string | null) {
  if (search === null) return;
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => void loadServers(search), 300);
}
const gpgcheck = ref(true);

const title = computed(() =>
  props.repositories && props.repositories.length > 1
    ? "Repositories an Server anbinden"
    : "Repository an Server anbinden"
);

const reposToAttach = computed<Repository[]>(() => {
  if (props.repositories) return props.repositories;
  return selectedRepo.value ? [selectedRepo.value] : [];
});

const fqdnsToAttach = computed<string[]>(() =>
  props.server ? [props.server.fqdn] : selectedServers.value.map((s) => s.fqdn)
);

// e.g. SELF_ONLY repositories the user doesn't own, the backend rejects those
const blockedReason = computed(
  () => reposToAttach.value.map(repoAttachDisabledReason).find((r) => r) ?? ""
);

const canSubmit = computed(
  () =>
    isValid.value &&
    !loading.value &&
    !blockedReason.value &&
    reposToAttach.value.length > 0 &&
    fqdnsToAttach.value.length > 0
);

function open() {
  selectedRepo.value = null;
  selectedServers.value = [];
  searchResults.value = [];
  enabled.value = true;
  gpgcheck.value = true;
  dialog.value = true;
  registerOpenDialog?.();
  if (!props.server) void loadServers("");
}

function close() {
  dialog.value = false;
  unregisterOpenDialog?.();
}

async function attachRepository(repo: Repository) {
  // The backend has separate actions depending on whether the user may edit (owns) the repository
  const detail = await repositoryService.getRepositoryById(ref(false), repo.id);
  const action = detail.canEdit
    ? "PAKETSHOP_REPO_ATTACH_OWNED_REPO"
    : "PAKETSHOP_REPO_ATTACH_NOT_OWNED_REPO";
  await jobService.startJob(loading, action, -1, {
    name: repo.name,
    enabled: enabled.value,
    gpgcheck: gpgcheck.value,
    control_paketshop_repos_systems: fqdnsToAttach.value,
  });
}

async function submit() {
  // one job per repository, the backend attaches a single repository per job
  const results = await Promise.allSettled(
    reposToAttach.value.map(attachRepository)
  );
  if (results.some((r) => r.status === "fulfilled")) {
    emit("order-done");
  }
  close();
}
</script>
