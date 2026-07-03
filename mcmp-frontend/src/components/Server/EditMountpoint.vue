<template>
  <v-tooltip
    location="bottom"
    :text="
      props.newMountpoint
        ? 'Hinzufügen'
        : 'Bearbeitung' +
          ' nicht möglich ' +
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
          :aria-label="
            newMountpoint ? 'Neues lokales Laufwerk anlegen' : 'Laufwerk bearbeiten'
          "
          @click="dialog = true"
        >
          <v-icon>{{ newMountpoint ? mdiPlus : mdiPencil }}</v-icon>
        </v-btn>
      </span>
    </template>
  </v-tooltip>
  <common-dialog
    v-model="dialog"
    :title="newMountpoint ? 'Neues lokales Laufwerk anlegen' : 'Laufwerk bearbeiten'"
    max-width="1000"
    :icon="props.mountPoints.length > 0 ? mdiPencil : mdiPlus"
    show-actions
    submit-activated
    show-change-warning
    :check-for-enabled-actions="
      selectedServer.guestConfigFullName?.toLowerCase().includes('linux')
        ? ['LINUX_MOUNTPOINT_CHANGE']
        : ['WINDOWS_PARTITION_CHANGE']
    "
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <v-form ref="form">
      <v-row>
        <v-col
          v-if="newCapacityGB >= 2000"
          cols="12"
        >
          <common-alert color="notice_red">
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
          </common-alert>
        </v-col>
        <v-col
          v-if="
            ((newCapacityGB -
              (formatter.calculateBtoGB(mountPoint?.freeSpaceInBytes) +
                (newCapacityGB -
                  formatter.calculateBtoGB(mountPoint?.capacityInBytes)))) *
              100) /
              newCapacityGB >
            95
          "
          cols="12"
        >
          <common-alert type="warning">
            <h4>
              Es wird empfohlen mindestens 5% freien Speicherplatz zu behalten.
            </h4>
          </common-alert>
        </v-col>
        <v-col
          v-if="!newMountpoint"
          cols="12"
        >
          <h4>Anzupassendes Laufwerk auswählen:</h4>
        </v-col>
        <v-col
          v-if="!newMountpoint"
          cols="12"
        >
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
                ? Math.max(
                    1,
                    Number(formatter.formatBtoGB(mountPoint.capacityInBytes))
                  )
                : 0
            "
          />
        </v-col>
        <v-col
          v-if="mountPoint != null && !newMountpoint"
          cols="12"
        >
          <h4>Aktueller Füllstand (bei neuer Größe):</h4>
          <linear-progress-with-colors
            :value="
              newCapacityGB > 0
                ? ((newCapacityGB -
                    (formatter.calculateBtoGB(mountPoint?.freeSpaceInBytes) +
                      (newCapacityGB -
                        formatter.calculateBtoGB(
                          mountPoint?.capacityInBytes
                        )))) *
                    100) /
                  newCapacityGB
                : 0
            "
            :show-percentage="true"
          />
        </v-col>
        <v-col
          v-if="newMountpoint"
          cols="12"
        >
          <h4>Pfad:</h4>
          <v-text-field
            ref="inputRef"
            v-model="newPath"
            clearable
            maxlength="50"
            placeholder="/mnt/test"
            :rules="[
              rules.notEmptyRule('Es muss ein Pfad angegeben werden.'),
              rules.regexRule(/^\//, 'Pfad muss mit einem \'/\' beginnen.'),
              rules.regexRule(
                /^(?!.*\/\/).*$/,
                'Pfad darf keine nicht \'//\' enthalten.'
              ),
              rules.regexRule(
                /^(?!.*\/$).*$/,
                'Pfad darf nicht mit \'/\' enden.'
              ),
            ]"
          />
        </v-col>
        <v-col
          v-if="mountPoint != null || newMountpoint"
          cols="12"
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
          v-if="mountPoint != null || newMountpoint"
          cols="12"
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
        <v-col
          v-if="newMountpoint"
          cols="12"
        >
          <h4>Name der Volume Gruppe:</h4>
          <v-text-field
            v-model="newVolumeGroup"
            clearable
            maxlength="50"
            :rules="[
              rules.notEmptyRule('Es muss eine Volume Gruppe angegeben werden.'),
            ]"
          />
        </v-col>
      </v-row>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import { mdiPencil, mdiPlus } from "@mdi/js";
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
  newMountpoint: boolean;
}>();

const emit =
  defineEmits<
    (
      e: "save",
      mountPoint: MountPoint,
      newCapacityGB: number,
      newVolumeGroup: string
    ) => void
  >();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const form = ref<HTMLFormElement>();
const rules = useRules();
const formatter = useFormatter();
const dialog = ref(false);
const mountPoint = ref<MountPoint | null>(null);
const newCapacityGB = ref(5);
const newPath = ref("");
const newVolumeGroup = ref("data");

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
  newCapacityGB.value = 5;
  newPath.value = "";
  newVolumeGroup.value = "";
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      if (props.newMountpoint) {
        emit(
          "save",
          new MountPoint(
            props.selectedServer.id,
            newPath.value,
            0,
            0,
            "",
            false
          ),
          newCapacityGB.value,
          newVolumeGroup.value
        );
      } else {
        emit("save", mountPoint.value, newCapacityGB.value, "");
      }
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
