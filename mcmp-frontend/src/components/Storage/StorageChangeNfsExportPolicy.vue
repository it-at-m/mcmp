<template>
  <template v-if="isAllowedShare && isEditMode">
    <v-tooltip
      location="bottom"
      :text="activatorDisabled ? disabledReason : tooltipText"
      :open-on-hover="true"
    >
      <template #activator="{ props: tooltipProps }">
        <span v-bind="tooltipProps">
          <v-btn
            icon
            variant="flat"
            :aria-label="tooltipText"
            :disabled="activatorDisabled"
            :title="activatorDisabled ? disabledReason : tooltipText"
            @click="openDialog"
          >
            <v-icon>{{ activatorIcon }}</v-icon>
          </v-btn>
        </span>
      </template>
    </v-tooltip>
    <common-dialog
      v-model="dialog"
      :loading="loading"
      :title="dialogTitle"
      max-width="600"
      show-actions
      :submit-activated="canSubmit"
      :icon="dialogIcon"
      show-change-warning
      :check-for-enabled-actions="['STORAGE_CHANGE_NFS_EXPORT_POLICY']"
      @dialog-cancel="close"
      @dialog-confirm="save"
    >
      <v-form>
        <v-row class="mb-4">
          <v-col cols="12">
            <v-autocomplete
              v-model="selectedServer"
              v-model:search="searchText"
              :items="availableServers"
              :loading="loadingServers"
              :label="isEditMode ? 'Server' : 'Server auswählen'"
              variant="outlined"
              item-title="name"
              item-value="name"
              :clearable="!isEditMode"
              :disabled="isEditMode"
              :rules="[
                rules.notEmptySelectRule('Ein Server muss ausgewählt werden'),
              ]"
              @click="handleServerFieldClick"
            >
              <template #no-data
                ><a class="ml-2">Keine Server gefunden</a></template
              >
            </v-autocomplete>
          </v-col>
        </v-row>

        <v-row>
          <v-col cols="12">
            <v-select
              v-model="selectedPermission"
              label="Berechtigungen"
              variant="outlined"
              :items="permissionOptions"
              item-title="label"
              item-value="value"
              :rules="[
                rules.notEmptySelectRule(
                  'Eine Berechtigung muss ausgewählt werden'
                ),
              ]"
            />
          </v-col>
        </v-row>
      </v-form>
    </common-dialog>
  </template>
</template>

<script setup lang="ts">
import type { ServerList } from "@/types/ServerList.ts";
import type { UnifiedStorageItem } from "@/types/Storage.ts";

import { mdiPencil, mdiPlus } from "@mdi/js";
import { computed, ref } from "vue";

import jobService from "@/api/jobService.ts";
import serverService from "@/api/serverService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const dialog = ref(false);
const loading = ref(false);
const loadingServers = ref(false);
const rules = useRules();
const selectedServer = ref("");
const selectedPermission = ref("");
const searchText = ref("");
const serverList = ref<Pick<ServerList, "name">[]>([]);
const requestedAlready = ref(false);

const permissionOptions = [
  { label: "read-write (rw)", value: "rw" },
  { label: "read-only (ro)", value: "ro" },
];

const props = withDefaults(
  defineProps<{
    selectedStorage: UnifiedStorageItem;
    mode?: "add" | "edit";
    serverFqdn?: string;
    permission?: string;
  }>(),
  {
    mode: "add",
    serverFqdn: "",
    permission: "",
  }
);

const isAllowedShare = computed(
  () =>
    props.selectedStorage.storageCategory == "NFS_STANDARD_SHARE" ||
    props.selectedStorage.storageCategory == "NFS_CLONE" ||
    props.selectedStorage.storageCategory == "NFS_WORM"
);

const isEditMode = computed(() => props.mode === "edit");
const tooltipText = computed(() =>
  isEditMode.value ? "Berechtigung bearbeiten" : "Server hinzufügen"
);
const dialogTitle = computed(() =>
  isEditMode.value ? "Berechtigung bearbeiten" : "Export-Policy ändern"
);
const activatorIcon = computed(() => (isEditMode.value ? mdiPencil : mdiPlus));
const dialogIcon = computed(() => (isEditMode.value ? mdiPencil : mdiPlus));
const canSubmit = computed(() =>
  Boolean(selectedServer.value && selectedPermission.value)
);
const availableServers = computed(() => {
  if (!isEditMode.value) {
    return serverList.value;
  }
  if (!selectedServer.value) {
    return [];
  }
  return [{ name: selectedServer.value }];
});

// Simple check if a value looks like an IPv4 address, CIDR or an IP range (e.g. 192.168.0.1-192.168.0.10)
function isIpOrRange(value: string) {
  if (!value) return false;
  const v = value.trim();
  // Matches:
  //  - 192.168.0.1
  //  - 192.168.0.1/24
  //  - 192.168.0.1-192.168.0.10
  const ipv4 = /^(?:\d{1,3}\.){3}\d{1,3}(?:\/(?:\d|[1-2]\d|3[0-2]))?$/;
  const ipv4Range = /^(?:\d{1,3}\.){3}\d{1,3}\s*-\s*(?:\d{1,3}\.){3}\d{1,3}$/;
  return ipv4.test(v) || ipv4Range.test(v);
}

const activatorDisabled = computed(() => {
  return (
    !props.selectedStorage.canEdit ||
    (isEditMode.value && isIpOrRange(props.serverFqdn ?? ""))
  );
});

const disabledReason = computed(() => {
  if (!props.selectedStorage.canEdit) {
    return "Bearbeitung nur möglich, wenn genau ein Anwendungsservice zugeordnet ist und Sie berechtigt sind.";
  }
  return "Editieren nicht möglich für IP-Adressen/Range";
});

function openDialog() {
  // Prevent opening when in edit mode for IP addresses / ranges
  if (activatorDisabled.value) {
    return;
  }

  if (isEditMode.value) {
    selectedServer.value = props.serverFqdn;
    selectedPermission.value = normalizePermission(props.permission);
  }
  dialog.value = true;
}

function reset() {
  selectedServer.value = "";
  selectedPermission.value = "";
  searchText.value = "";
  requestedAlready.value = false;
}

function close() {
  reset();
  dialog.value = false;
}

function save() {
  if (canSubmit.value) {
    jobService
      .startJob(loading, "STORAGE_CHANGE_NFS_EXPORT_POLICY", -1, {
        uuid: props.selectedStorage.uuid,
        fqdn: selectedServer.value,
        permission: selectedPermission.value,
      })
      .then(() => {
        close();
      });
  }
}

function handleServerFieldClick() {
  if (!isEditMode.value) {
    getServers();
  }
}

function normalizePermission(permission: string) {
  if (permission === "read-write" || permission === "rw") {
    return "rw";
  }
  if (permission === "read-only" || permission === "ro") {
    return "ro";
  }
  return permission;
}

function getServers() {
  if (requestedAlready.value) {
    return;
  }
  requestedAlready.value = true;
  loadingServers.value = true;
  serverService
    .getVisibleServers(loadingServers, 0, -1, "name", "asc", "", [], "", false)
    .then((response) => {
      serverList.value = response.content;
    })
    .finally(() => {
      loadingServers.value = false;
    });
}
</script>
