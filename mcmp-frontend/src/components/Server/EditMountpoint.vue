<template>
  <v-tooltip
    location="bottom"
    :text="
      'Bearbeitung nicht möglich ' +
      (props.selectedServer.powerState != 'poweredOn'
        ? '(Server ist ausgeschaltet)'
        : '') +
      (props.snapshotOnServer ? ' (Server hat einen Snapshot)' : '')
    "
    :open-on-hover="
      props.selectedServer.powerState != 'poweredOn' || props.snapshotOnServer
    "
  >
    <template #activator="{ props: tooltipProps }">
      <span v-bind="tooltipProps">
        <v-btn
          icon
          :disabled="
            props.selectedServer.powerState != 'poweredOn' ||
            props.snapshotOnServer
          "
          variant="flat"
          aria-label="Laufwerke bearbeiten"
          @click="dialog = true"
        >
          <v-icon>{{ mdiPencil }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <CommonDialog
    v-model="dialog"
    title="Laufwerke bearbeiten"
    max-width="1000"
    :icon="mdiPencil"
    show-actions
    submitActivated
    @dialog-cancel="close"
    @dialog-confirm="save"
    showChangeWarning
    :checkForEnabledActions="
      selectedServer.guestConfigFullName?.toLowerCase().includes('linux')
        ? ['LINUX_MOUNTPOINT_CHANGE']
        : ['WINDOWS_PARTITION_CHANGE']
    "
  >
    <v-form ref="form">
      <v-row>
        <v-col
          cols="12"
          v-if="newCapacityGB >= 2000"
        >
          <CommonAlert color="notice_red">
            <div class="links">
              <h4>Hinweis:</h4>
              Zur Ressourcenerweiterung über den maximalen Wert von 2000 GB
              bitte
              <a
                href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=f2385ce61b76a050e52dfddacd4bcb3e"
                target="_blank"
                rel="noopener noreferrer"
              >
                Ticket
              </a>
              an IBS48 Linux-Server oder IBS49 Windows-Server
            </div>
          </CommonAlert>
        </v-col>
        <v-col
          cols="12"
          v-if="
            ((newCapacityGB -
              (formatter.calculateBtoGB(mountPoint?.freeSpaceInBytes) +
                (newCapacityGB -
                  formatter.calculateBtoGB(mountPoint?.capacityInBytes)))) *
              100) /
              newCapacityGB >
            95
          "
        >
          <CommonAlert type="warning">
            <h4>
              Es wird empfohlen mindestens 5% freien Speicherplatz zu behalten.
            </h4>
          </CommonAlert>
        </v-col>
        <v-col cols="12">
          <h4>Anzupassendes Laufwerk auswählen:</h4>
          <v-autocomplete
            v-model="mountPoint"
            :items="
              mountPoints
                .filter((mp) => mp.editable)
                .sort((a, b) => a.diskPath.localeCompare(b.diskPath))
            "
            item-title="diskPath"
            return-object
            rounded
            clearable
            variant="outlined"
            :rules="[
              rules.notEmptyRule('Es muss ein Laufwerk ausgewählt werden.'),
            ]"
            @update:model-value="
              newCapacityGB = mountPoint?.capacityInBytes
                ? formatter.formatBtoGB(mountPoint.capacityInBytes)
                : 0
            "
          />
        </v-col>
        <v-col
          cols="12"
          v-if="mountPoint != null"
        >
          <h4>Aktueller Füllstand (bei neuer Göße):</h4>
          <LinearProgressWithColors
            :value="
              ((newCapacityGB -
                (formatter.calculateBtoGB(mountPoint?.freeSpaceInBytes) +
                  (newCapacityGB -
                    formatter.calculateBtoGB(mountPoint?.capacityInBytes)))) *
                100) /
              newCapacityGB
            "
            :show-percentage="true"
          />
        </v-col>
        <v-col
          cols="12"
          v-if="mountPoint != null"
        >
          <v-slider
            v-model="newCapacityGB"
            label="Größe"
            :min="
              Math.ceil(formatter.calculateBtoGB(mountPoint?.capacityInBytes))
            "
            :max="2000"
            step="1"
          />
        </v-col>
        <v-col
          cols="12"
          v-if="mountPoint != null"
        >
          <v-text-field
            v-model="newCapacityGB"
            label="Größe in GB"
            type="number"
            :min="
              Math.ceil(formatter.calculateBtoGB(mountPoint?.capacityInBytes))
            "
            :max="2000"
            :rules="[
              (v) =>
                v >=
                  Math.ceil(
                    formatter.calculateBtoGB(mountPoint?.capacityInBytes)
                  ) ||
                'Neue Größe darf nicht kleiner als die alte Größe seien.',
              (v) => v <= 2000 || 'Neue Größe darf nicht größer 2TB sein.',
            ]"
          />
        </v-col>
      </v-row>
    </v-form>
  </CommonDialog>
</template>

<script setup lang="ts">
import { mdiPencil } from "@mdi/js";
import { inject, ref, watch } from "vue";

import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import LinearProgressWithColors from "@/components/common/LinearProgressWithColors.vue";
import { useFormatter } from "@/composables/formatter";
import { useRules } from "@/composables/rules";
import MountPoint from "@/types/MountPoint";
import Server from "@/types/Server";

const props = defineProps<{
  selectedServer: Server;
  mountPoints: MountPoint[];
  snapshotOnServer?: boolean;
}>();

const emit = defineEmits<{
  (e: "save", mountPoint: MountPoint, newCapacityGB: number): void;
}>();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const form = ref<HTMLFormElement>();
const rules = useRules();
const formatter = useFormatter();
const dialog = ref(false);
const mountPoint = ref<MountPoint | null>(null);
const newCapacityGB = ref();

// Dialog-Status überwachen
watch(dialog, (newValue) => {
  if (newValue) {
    registerOpenDialog?.();
  } else {
    unregisterOpenDialog?.();
  }
});

function close() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  mountPoint.value = null;
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emit("save", mountPoint.value, newCapacityGB.value);
      dialog.value = false;
      resetForm();
    }
  });
}
</script>

<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
