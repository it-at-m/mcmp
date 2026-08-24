<template>
  <common-dialog
    v-if="canManagePool"
    v-model="dialog"
    title="Pool-Member bearbeiten"
    :icon="mdiPencil"
    max-width="800"
    show-actions
    :submit-activated="canSubmit"
    show-change-warning
    :check-for-enabled-actions="['LOADBALANCER_F5_CHANGE_POOL_MEMBERS']"
    @dialog-cancel="close"
    @dialog-confirm="submit"
  >
    <template #activator="{ props: activatorProps }">
      <v-btn
        v-bind="activatorProps"
        :icon="mdiPencil"
        variant="text"
        size="small"
        aria-label="Pool-Member bearbeiten"
        @click.stop="openDialog"
      />
    </template>

    <v-row>
      <v-col cols="12">
        <h3>Aktuelle Member ({{ pool.members.length }})</h3>
      </v-col>
    </v-row>

    <v-row v-if="pool.members.length === 0">
      <v-col
        cols="12"
        class="pt-0 text-grey"
      >
        Keine Member vorhanden.
      </v-col>
    </v-row>

    <template v-else>
      <v-row class="text-grey">
        <v-col cols="6">
          <h4>Server</h4>
        </v-col>
        <v-col cols="3">
          <h4>IP</h4>
        </v-col>
        <v-col cols="2">
          <h4>Port</h4>
        </v-col>
        <v-col cols="1" />
      </v-row>
      <v-row
        v-for="member in pool.members"
        :key="memberKey(member)"
        class="member-row"
        :class="{ 'member-removed': removedMembers.has(member) }"
        align="center"
      >
        <v-col
          cols="6"
          class="pt-0"
        >
          {{ member.serverName ?? member.ip }}
        </v-col>
        <v-col
          cols="3"
          class="pt-0"
        >
          {{ member.ip }}
        </v-col>
        <v-col
          cols="2"
          class="pt-0"
        >
          {{ member.port }}
        </v-col>
        <v-col
          cols="1"
          class="pt-0 text-right"
        >
          <v-btn
            :icon="removedMembers.has(member) ? mdiUndo : mdiDelete"
            size="small"
            variant="text"
            :color="removedMembers.has(member) ? undefined : 'error'"
            @click="toggleRemove(member)"
          />
        </v-col>
      </v-row>
    </template>

    <v-row>
      <v-col cols="12">
        <h3>Server hinzufügen</h3>
      </v-col>
    </v-row>
    <v-row align="center">
      <v-col cols="6">
        <v-autocomplete
          v-model="newServer"
          :items="serverOptions"
          :loading="serverSearchLoading"
          :search="serverSearchInput"
          item-title="name"
          item-value="id"
          label="Server suchen"
          placeholder="Mind. 2 Zeichen eingeben"
          rounded
          variant="outlined"
          clearable
          return-object
          hide-details
          @update:search="onServerSearch"
        >
          <template #no-data>
            <div class="px-4 py-2">Kein Server gefunden</div>
          </template>
        </v-autocomplete>
      </v-col>
      <v-col cols="3">
        <v-text-field
          v-model.number="newPort"
          type="number"
          label="Port*"
          rounded
          variant="outlined"
          hide-details
        />
      </v-col>
      <v-col cols="3">
        <v-btn
          :disabled="!canAddMember"
          color="do"
          variant="flat"
          rounded
          block
          @click="addMember"
        >
          Hinzufügen
        </v-btn>
      </v-col>
    </v-row>
    <v-row v-if="portInvalid">
      <v-col
        cols="3"
        offset="6"
        class="pt-0 text-error text-caption"
      >
        Port muss zwischen 1 und 65535 liegen
      </v-col>
    </v-row>
    <v-row v-if="addError">
      <v-col
        cols="12"
        class="pt-0 text-error"
      >
        {{ addError }}
      </v-col>
    </v-row>

    <template v-if="addedMembers.length">
      <v-divider class="my-6" />
      <v-row>
        <v-col cols="12">
          <h3>Neue Member ({{ addedMembers.length }})</h3>
        </v-col>
      </v-row>
      <v-row class="text-grey">
        <v-col cols="6">
          <h4>Server</h4>
        </v-col>
        <v-col cols="2">
          <h4>Port</h4>
          <v-col cols="3"> </v-col>
        </v-col>
        <v-col cols="1" />
      </v-row>
      <v-row
        v-for="(added, index) in addedMembers"
        :key="`${added.server.id}-${added.port}`"
        align="center"
        class="member-row"
      >
        <v-col
          cols="6"
          class="pt-0"
        >
          {{ added.server.name }}
        </v-col>
        <v-col
          cols="5"
          class="pt-0"
        >
          {{ added.port }}
        </v-col>
        <v-col
          cols="1"
          class="pt-0 text-right"
        >
          <v-btn
            :icon="mdiDelete"
            size="small"
            variant="text"
            color="error"
            @click="addedMembers.splice(index, 1)"
          />
        </v-col>
      </v-row>
    </template>
  </common-dialog>
</template>

<script setup lang="ts">
import type {
  LoadbalancerDetail,
  LoadbalancerMember,
  LoadbalancerPool,
} from "@/types/LoadbalancerDetail";
import type { ServerList } from "@/types/ServerList";

import { mdiDelete, mdiPencil, mdiUndo } from "@mdi/js";
import { computed, inject, onMounted, ref } from "vue";

import jobService from "@/api/jobService";
import serverService from "@/api/serverService";
import testenvService from "@/api/testenvService";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { isCapPool } from "@/util/loadbalancerPool";

const props = defineProps<{
  lb: LoadbalancerDetail;
  pool: LoadbalancerPool;
}>();

const emit = defineEmits<(e: "changed") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const dialog = ref(false);
const loading = ref(false);
const testEnv = ref(false);
const loadingTestEnv = ref(false);

onMounted(() => {
  testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
    testEnv.value = enabled;
  });
});

const canManagePool = computed(
  () => testEnv.value && !props.lb.wafEnabled && !isCapPool(props.pool)
);

const removedMembers = ref<Set<LoadbalancerMember>>(new Set());

interface AddedMember {
  server: ServerList;
  port: number;
}

const addedMembers = ref<AddedMember[]>([]);
const newServer = ref<ServerList | null>(null);
const newPort = ref<number | null>(null);
const addError = ref("");

const serverOptions = ref<ServerList[]>([]);
const serverSearchInput = ref("");
const serverSearchLoading = ref(false);
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

function memberKey(member: LoadbalancerMember): string {
  return `${member.ip}:${member.port}`;
}

function onServerSearch(searchText: string) {
  serverSearchInput.value = searchText;
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    if (!searchText || searchText.trim().length < 2) {
      serverOptions.value = [];
      return;
    }
    serverSearchLoading.value = true;
    serverService
      .getVisibleServers(
        ref(false),
        0,
        20,
        "name",
        "asc",
        searchText,
        [],
        "",
        false
      )
      .then((page) => {
        serverOptions.value = page.content;
      })
      .catch(() => {
        serverOptions.value = [];
      })
      .finally(() => {
        serverSearchLoading.value = false;
      });
  }, 300);
}

const portInvalid = computed(
  () => newPort.value != null && (newPort.value < 1 || newPort.value > 65535)
);

const canAddMember = computed(
  () => !!newServer.value && !!newPort.value && !portInvalid.value
);

function addMember() {
  addError.value = "";
  const server = newServer.value;
  const port = newPort.value;
  if (!server || !port) return;
  const alreadyAdded = addedMembers.value.some(
    (m) => m.server.id === server.id && m.port === port
  );
  const alreadyMember = props.pool.members.some(
    (m) =>
      !removedMembers.value.has(m) &&
      m.serverId === server.id &&
      m.port === port
  );
  if (alreadyAdded || alreadyMember) {
    addError.value = "Dieser Server ist mit diesem Port bereits im Pool.";
    return;
  }
  addedMembers.value.push({ server, port });
  newServer.value = null;
  newPort.value = null;
  serverOptions.value = [];
}

function toggleRemove(member: LoadbalancerMember) {
  if (removedMembers.value.has(member)) {
    removedMembers.value.delete(member);
  } else {
    removedMembers.value.add(member);
  }
}

const canSubmit = computed(
  () => removedMembers.value.size > 0 || addedMembers.value.length > 0
);

function resetForm() {
  removedMembers.value = new Set();
  addedMembers.value = [];
  newServer.value = null;
  newPort.value = null;
  addError.value = "";
  serverOptions.value = [];
}

function close() {
  dialog.value = false;
  resetForm();
  unregisterOpenDialog?.();
}

function openDialog() {
  dialog.value = true;
  registerOpenDialog?.();
}

function submit() {
  if (!canSubmit.value) return;
  const payload = {
    lb_virtual_server_id: props.lb.id,
    pool_name: props.pool.name,
    added: addedMembers.value.map((m) => ({
      server_id: m.server.id,
      port: m.port,
    })),
    removed: Array.from(removedMembers.value).map((m) => ({
      ip: m.ip,
      port: m.port,
    })),
  };
  jobService
    .startJob(loading, "LOADBALANCER_F5_CHANGE_POOL_MEMBERS", -1, payload)
    .then(() => {
      dialog.value = false;
      resetForm();
      unregisterOpenDialog?.();
      emit("changed");
    });
}
</script>

<style scoped>
.member-row {
  /* noinspection CssUnresolvedCustomProperty */
  border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
}

.member-removed {
  opacity: 0.5;
  text-decoration: line-through;
}
</style>
