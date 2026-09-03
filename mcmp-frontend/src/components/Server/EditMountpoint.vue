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
            newMountpoint
              ? 'Neues lokales Laufwerk anlegen'
              : 'Laufwerk bearbeiten'
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
    :title="
      newMountpoint ? 'Neues lokales Laufwerk anlegen' : 'Laufwerk bearbeiten'
    "
    max-width="1000"
    :icon="props.mountPoints.length > 0 ? mdiPencil : mdiPlus"
    show-actions
    submit-activated
    show-change-warning
    :check-for-enabled-actions="
      isLinux
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
              Für Ressourcenerweiterung >2000 GB bitte ein Ticket bei
              <a
                href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=73fd83e11bde1094588efddacd4bcb92"
                target="_blank"
                rel="noopener noreferrer"
              >
                IBS48 Linux-Server
              </a>
              oder
              <a
                href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=1539a307c3e843d0d130f1fb050131a9"
                target="_blank"
                rel="noopener noreferrer"
              >
                IBS49 Windows-Server
              </a>
              eröffnen.
            </div>
          </common-alert>
        </v-col>
        <v-col
          v-if="
            !isLinux &&
            !newMountpoint &&
            mountPoint != null &&
            formatter.calculateBtoGB(mountPoint.freeSpaceInBytes) < 5
          "
          cols="12"
        >
          <common-alert type="warning">
            <h4>
              Für eine Speichererweiterung müssen min. 5 GB freier Speicherplatz verfügbar sein.
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
              rules.regexRule(
                /^[a-z0-9/]+$/,
                'Pfad darf nur aus Kleinbuchstaben und Zahlen bestehen.'
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
              Math.ceil(
                formatter.calculateBtoGB(mountPoint?.capacityInBytes ?? 0)
              )
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
              Math.ceil(
                formatter.calculateBtoGB(
                  mountPoint?.capacityInBytes ?? 1024 ** 3
                )
              )
            "
            :max="2000"
            :rules="[
              (v) => v >= 1 || 'Neue Größe darf nicht kleiner 1 GB sein.',
              (v) =>
                v >=
                  Math.ceil(
                    formatter.calculateBtoGB(
                      mountPoint?.capacityInBytes ?? 1024 ** 3
                    )
                  ) || 'Der neue Wert darf nicht kleiner oder gleich der alten Größe sein',
              () =>
                isLinux ||
                newMountpoint ||
                (mountPoint != null &&
                  formatter.calculateBtoGB(mountPoint.freeSpaceInBytes) >= 5) ||
                'Für eine Speichererweiterung müssen min. 5 GB freier Speicherplatz verfügbar sein',
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
              rules.notEmptyRule(
                'Es muss eine Volume Gruppe angegeben werden.'
              ),
              rules.regexRule(
                /^[a-z0-9]+$/,
                'Pfad darf nur aus Kleinbuchstaben und Zahlen bestehen.'
              ),
            ]"
          />
        </v-col>
      </v-row>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import { mdiPencil, mdiPlus } from "@mdi/js";
import { computed, inject, ref, watch } from "vue";

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

const isLinux = computed(() =>
  props.selectedServer.guestConfigFullName?.toLowerCase().includes("linux") ?? false
);

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
            1,
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