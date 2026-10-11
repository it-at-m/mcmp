<template>
  <common-dialog
    v-model="dialog"
    title="Repository kopieren"
    :icon="mdiContentCopy"
    max-width="600"
    show-actions
    show-change-warning
    :submit-activated="isValid && !!source && !loading"
    :check-for-enabled-actions="['PAKETSHOP_REPO_COPY']"
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
          Kopieren
        </v-btn>
      </slot>
    </template>

    <v-form v-model="isValid">
      <v-select
        v-if="props.sourceOptions"
        v-model="source"
        :items="copyableSourceOptions"
        item-title="name"
        return-object
        label="Kopieren von*"
        variant="outlined"
        rounded
        :rules="[
          (v: Repository | null) =>
            !!v || 'Es muss ein Repository ausgewählt werden.',
        ]"
      />
      <repo-autocomplete-field
        v-else
        v-model="source"
        label="Kopieren von*"
        editable-only
      />

      <v-text-field
        v-model="name"
        label="Name des neuen Repositories*"
        hint='Muss auf "-test" oder "-prod" enden'
        persistent-hint
        variant="outlined"
        rounded
        clearable
        :rules="newRepoNameRules"
      />
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository";

import { mdiContentCopy } from "@mdi/js";
import { computed, inject, ref } from "vue";

import jobService from "@/api/jobService";
import CommonDialog from "@/components/common/CommonDialog.vue";
import RepoAutocompleteField from "@/components/Paketshop/RepoAutocompleteField.vue";
import { useRepoRules } from "@/composables/repoRules.ts";

const props = defineProps<{
  /** Restricts the source to these repositories (e.g. those of an appservice); otherwise all visible repositories can be searched. */
  sourceOptions?: Repository[];
}>();

const emit = defineEmits<(e: "order-done") => void>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const { newRepoNameRules } = useRepoRules();

const dialog = ref(false);
const loading = ref(false);
const isValid = ref(false);
const source = ref<Repository | null>(null);
const name = ref("");

// the backend only allows copying unlocked repositories the user can edit
const copyableSourceOptions = computed(() =>
  (props.sourceOptions ?? []).filter(
    (r) => r.lockStatus !== "LOCKED" && r.canEdit
  )
);

function open() {
  source.value = null;
  name.value = "";
  dialog.value = true;
  registerOpenDialog?.();
}

function close() {
  dialog.value = false;
  unregisterOpenDialog?.();
}

function submit() {
  if (!source.value) return;
  jobService
    .startJob(loading, "PAKETSHOP_REPO_COPY", -1, {
      name: name.value.trim(),
      "copy-from": source.value.name,
    })
    .then(() => {
      emit("order-done");
      close();
    });
}
</script>
