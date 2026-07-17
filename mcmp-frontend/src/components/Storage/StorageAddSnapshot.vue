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
          <v-icon>{{ mdiPlus }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    :model-value="dialog"
    max-width="1100"
    :title="title"
    :icon="mdiPlus"
    show-actions
    :submit-activated="validated"
    show-change-warning
    :check-for-enabled-actions="[
      'STORAGE_CREATE_SNAPSHOT_NFS',
      'STORAGE_CREATE_SNAPSHOT_CIFS',
    ]"
    @dialog-cancel="close()"
    @dialog-confirm="save()"
  >
    <v-form ref="form">
      <h4>Tage (fester Wert):</h4>
      <v-number-input
        v-model="days"
        control-variant="split"
        readonly
        disabled
        persistent-hint
        hint="Aufbewahrungszeit des Snapshots bis zur automatischen Löschung."
      ></v-number-input>
      <br />
      <h4>Beschreibung:</h4>
      <v-text-field
        v-model="description"
        :rules="[
          useRules().regexRule(
            /^[a-z0-9]*$/,
            'Nur Kleinbuchstaben und Zahlen erlaubt.'
          ),
          useRules().notEmptyRule('Eine Beschreibung ist erforderlich.'),
          useRules().minLengthRule(3, 'Mindestens 3 Zeichen.'),
          useRules().maxLengthRule(20, 'Maximal 20 Zeichen.'),
        ]"
      ></v-text-field>
      <common-alert
        color="notice_red"
        class="mt-3"
      >
        <h4>Hinweis:</h4>
        Anders als bei Serversnapshots werden bei Storagesnaphots die
        beteiligten Server nicht automatisch heruntergefahren. Sofern zur
        Sicherstellung der Datenkonsistenz ein herunterfahren der beteiligten
        Server notwendig ist, bitte dies separat manuell durchführen.
      </common-alert>
    </v-form>
  </common-dialog>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage.ts";

import { mdiPlus } from "@mdi/js";
import { computed, ref, watch } from "vue";

import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useRules } from "@/composables/rules.ts";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
}>();
const emits = defineEmits<(e: "save", description: string) => void>();

const form = ref<HTMLFormElement>();
const dialog = ref(false);
const days = ref(10);
const description = ref("");
const validated = ref(false);

const isDisabled = computed(() => {
  return props.selectedStorageItem.isWorm;
});

const tooltipText = computed(() => {
  if (props.selectedStorageItem.isWorm) {
    return "Snapshot erstellen nicht möglich, da Speicher WORM ist.";
  }
  return "Snapshot erstellen";
});

const title = computed(() => {
  return `Snapshot für ${props.selectedStorageItem.type == "NFS" ? props.selectedStorageItem.nfs_mount_path : props.selectedStorageItem.cifs_mount_path} erstellen`;
});

function openDialog() {
  dialog.value = true;
}

function close() {
  description.value = "";
  dialog.value = false;
}

function save() {
  if (validated.value) {
    emits("save", description.value);
    close();
  }
}

watch([description], async () => {
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
});
</script>
