<template>
  <common-dialog
    v-model="dialog"
    title="Repository erstellen"
    :icon="mdiPackageVariantPlus"
    max-width="700"
    show-actions
    show-change-warning
    :submit-activated="isValid && !loading"
    :check-for-enabled-actions="['PAKETSHOP_REPO_CREATE']"
    @dialog-cancel="close"
    @dialog-confirm="submit"
  >
    <template #activator="{ props: slotProps }">
      <slot
        name="activator"
        :props="{ ...slotProps, onClick: open }"
      >
        <v-btn
          v-bind="slotProps"
          flat
          @click="open"
        >
          Erstellen
        </v-btn>
      </slot>
    </template>

    <v-form
      ref="form"
      v-model="isValid"
    >
      <v-autocomplete
        v-if="!props.appservice"
        v-model="selectedAppservice"
        :items="appservices"
        :loading="loadingAppservices"
        label="Anwendungsservice*"
        item-title="name"
        return-object
        no-filter
        rounded
        clearable
        variant="outlined"
        :rules="[
          rules.notEmptySelectRule(
            'Ein Anwendungsservice muss ausgewählt werden'
          ),
        ]"
        @update:search="onAppserviceSearch"
      >
        <template #no-data>
          <div class="px-4 py-2">Keine Anwendungsservices gefunden</div>
        </template>
      </v-autocomplete>
      <common-alert
        v-else
        color="accent"
        class="mb-4"
      >
        <div class="confirm-entity-label">Anwendungsservice:</div>
        <div class="confirm-entity-name">{{ props.appservice.name }}</div>
      </common-alert>

      <v-text-field
        v-model="name"
        label="Name*"
        hint='Muss auf "-test" oder "-prod" enden'
        persistent-hint
        variant="outlined"
        rounded
        clearable
        :rules="newRepoNameRules"
        class="mb-2"
      />

      <v-checkbox
        v-model="selfOnly"
        label="Nur für Besitzer"
        hint="Das Repository kann nur von Mitgliedern der Änderungsgruppe des Anwendungsservices an Server angebunden werden"
        persistent-hint
      />

      <h4 class="mt-4 mb-2">Upstream (optional)</h4>
      <v-text-field
        v-model="upstreamUrl"
        label="Upstream URL"
        variant="outlined"
        rounded
        clearable
        :rules="upstreamUrlRules"
      />
      <template v-if="upstreamUrl?.trim()">
        <v-checkbox
          v-model="upstreamAllPackages"
          label="Alle Pakete vom Upstream übernehmen"
          hide-details
          class="mb-2"
        />
        <v-row>
          <v-col cols="6">
            <v-text-field
              v-model="upstreamUser"
              label="Upstream Benutzer"
              variant="outlined"
              rounded
              clearable
              autocomplete="off"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="upstreamPassword"
              label="Upstream Passwort"
              type="password"
              variant="outlined"
              rounded
              clearable
              autocomplete="new-password"
            />
          </v-col>
        </v-row>
      </template>

      <h4 class="mt-4 mb-2">GPG-Key</h4>
      <v-text-field
        v-model="gpgkeyLocation"
        label="GPG-Key Speicherort"
        variant="outlined"
        rounded
        clearable
      />
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice";
import type AppserviceList from "@/types/AppserviceList";

import { mdiPackageVariantPlus } from "@mdi/js";
import { inject, ref } from "vue";

import appserviceService from "@/api/appserviceService";
import jobService from "@/api/jobService";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRepoRules } from "@/composables/repoRules.ts";
import { useRules } from "@/composables/rules.ts";

const props = defineProps<{
  appservice?: Appservice | null;
}>();

const emit = defineEmits<(e: "order-done") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const rules = useRules();
const { newRepoNameRules, upstreamUrlRules } = useRepoRules();

const dialog = ref(false);
const loading = ref(false);
const isValid = ref(false);

const selectedAppservice = ref<AppserviceList | null>(null);
const appservices = ref<AppserviceList[]>([]);
const loadingAppservices = ref(false);
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const name = ref("");
const upstreamUrl = ref("");
const upstreamAllPackages = ref(false);
const upstreamUser = ref("");
const upstreamPassword = ref("");
const gpgkeyLocation = ref("");
const selfOnly = ref(false);

function reset() {
  selectedAppservice.value = null;
  name.value = "";
  upstreamUrl.value = "";
  upstreamAllPackages.value = false;
  upstreamUser.value = "";
  upstreamPassword.value = "";
  gpgkeyLocation.value = "";
  selfOnly.value = false;
}

function open() {
  reset();
  dialog.value = true;
  registerOpenDialog?.();
  if (!props.appservice) void loadAppservices("");
}

function close() {
  dialog.value = false;
  unregisterOpenDialog?.();
}

async function loadAppservices(search: string) {
  const res = await appserviceService.getAppservices(
    loadingAppservices,
    0,
    50,
    "asc",
    search
  );
  appservices.value = res.content;
}

function onAppserviceSearch(search: string | null) {
  if (search === null || search === selectedAppservice.value?.name) return;
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => void loadAppservices(search), 300);
}

function optional(value: string) {
  return value?.trim() ? value.trim() : undefined;
}

function submit() {
  const appserviceId = props.appservice?.id ?? selectedAppservice.value?.id;
  const hasUpstream = !!optional(upstreamUrl.value);
  jobService
    .startJob(loading, "PAKETSHOP_REPO_CREATE", -1, {
      appserviceId,
      name: name.value.trim(),
      "upstream-url": optional(upstreamUrl.value),
      "upstream-all-packages": hasUpstream
        ? upstreamAllPackages.value
        : undefined,
      "upstream-user": hasUpstream ? optional(upstreamUser.value) : undefined,
      "upstream-password": hasUpstream
        ? optional(upstreamPassword.value)
        : undefined,
      "gpgkey-location": optional(gpgkeyLocation.value),
      self_only: selfOnly.value,
    })
    .then(() => {
      emit("order-done");
      close();
    });
}
</script>
