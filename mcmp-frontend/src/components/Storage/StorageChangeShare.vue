<template>
  <CommonDialog
    v-model="dialog"
    :title="title"
    max-width="1000"
    :icon="mdiPencil"
    show-actions
    :submitActivated="validated"
    @dialog-cancel="close"
    @dialog-confirm="save"
    showChangeWarning
    :checkForEnabledActions="['STORAGE_MODIFY_NFS', 'STORAGE_MODIFY_CIFS']"
  >
    <template #activator="{ props }">
      <v-tooltip
        :text="editable ? 'Ressourcen bearbeiten' : 'Nicht bearbeitbar'"
        location="bottom"
      >
        <template #activator="{ props: tooltipProps }">
          <v-btn
            v-bind="{ ...props, ...tooltipProps }"
            icon
            variant="flat"
            :disabled="!editable"
            aria-label="Ressourcen bearbeiten"
          >
            <v-icon>{{ mdiPencil }}</v-icon>
          </v-btn>
        </template>
      </v-tooltip>
    </template>

    <v-row>
      <v-col cols="12">
        <StorageCharts
          :selectedStorageItem="selectedStorageItem"
          :preview-size-gb="draftSizeGb"
          :preview-snapshot-reserve-percent="draftSnapshotReservePercent"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="12">
        <p class="text-body-2 mb-2">Aktuell: {{ currentSizeLabel }}</p>
        <p class="text-caption mb-2">Minimum: {{ minimumSizeLabel }}</p>
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="9">
        <v-slider
          class="mt-3"
          v-model="sliderSizeValue"
          :label="sliderSizeLabel"
          :min="sliderMinValue"
          :max="sliderMaxValue"
          :step="sliderStep"
          :disabled="!editable"
        />
      </v-col>
      <v-col cols="3">
        <v-text-field
          rounded
          v-model="sliderSizeValue"
          :label="sliderSizeLabel"
          :suffix="useMbSlider ? 'MB' : 'GB'"
          :disabled="!editable"
          type="number"
          step="1"
          :rules="[
            (value) => {
              const numValue = Number(value);
              if (isNaN(numValue)) {
                return 'Ungültige Zahl';
              }
              if (numValue < sliderMinValue) {
                return `Muss mindestens ${sliderMinValue} ${useMbSlider ? 'MB' : 'GB'} sein`;
              }
              if (numValue > sliderMaxValue) {
                return `Darf höchstens ${sliderMaxValue} ${useMbSlider ? 'MB' : 'GB'} sein`;
              }
              return true;
            },
          ]"
        />
      </v-col>
    </v-row>
    <v-row v-if="editable && !isWorm">
      <v-col cols="9">
        <v-slider
          class="mt-3"
          v-model="draftSnapshotReservePercent"
          label="Snapshotreserve (%)"
          :min="snapshotReserveMinPercent"
          :max="snapshotReserveMaxPercent"
          :step="1"
        />
      </v-col>
      <v-col cols="3">
        <v-text-field
          rounded
          v-model="draftSnapshotReservePercent"
          label="Snapshotreserve (%)"
          suffix="%"
          type="number"
          step="1"
          :rules="[
            (value) => {
              const numValue = Number(value);
              if (isNaN(numValue)) {
                return 'Ungültige Zahl';
              }
              if (numValue < snapshotReserveMinPercent) {
                return `Muss mindestens ${snapshotReserveMinPercent}% sein`;
              }
              if (numValue > snapshotReserveMaxPercent) {
                return `Darf höchstens ${snapshotReserveMaxPercent}% sein`;
              }
              return true;
            },
          ]"
        ></v-text-field>
      </v-col>
    </v-row>
    <v-row v-else-if="editable && isWorm">
      <v-col cols="12">
        <v-alert
          type="info"
          variant="tonal"
        >
          Snapshotreserve bei WORM nicht änderbar.
        </v-alert>
      </v-col>
    </v-row>
  </CommonDialog>
</template>

<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage.ts";

import { mdiPencil } from "@mdi/js";
import { computed, ref, watch } from "vue";

import CommonDialog from "@/components/common/CommonDialog.vue";
import StorageCharts from "@/components/Storage/StorageCharts.vue";

const bytesPerGb = 1024 * 1024 * 1024;
const mbPerGb = 1024;
const smallShareThresholdGb = 1;
const snapshotReserveMinPercent = 0;
const snapshotReserveMaxPercent = 60;

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
}>();

const emit = defineEmits<{
  (
    e: "save",
    payload: { sizeGb: number; snapshotReservePercent: number }
  ): void;
}>();

const dialog = ref(false);
const draftSizeGb = ref(0);
const draftSnapshotReservePercent = ref(0);

const editable = computed(() => {
  return (
    (props.selectedStorageItem.type === "NFS" &&
      props.selectedStorageItem.nfs_mount_path?.match(
        /svm[pkc][0-9]{2}dcn.srv.muenchen.de:\/(sn3|sn3c|wn3)_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}/
      )) ||
    (props.selectedStorageItem.type === "CIFS" &&
        props.selectedStorageItem.cifs_mount_path?.match(
          /\\\\svm[pkc][0-9]{2}dcc\.srv\.muenchen\.de\\(sc|scc|wc)_[pskcd]_[a-z0-9]{3,20}_[a-z0-9]{3,20}/
        ))
  );
});

const isWorm = computed(() => props.selectedStorageItem.isWorm === true);

const currentSizeGb = computed(() => {
  return Math.max(props.selectedStorageItem.size / bytesPerGb, 0);
});

const usedByAfsGb = computed(() => {
  return Math.max(
    (props.selectedStorageItem.spaceLogicalUsedByAfs ?? 0) / bytesPerGb,
    0
  );
});

const snapshotUsedGb = computed(() => {
  return Math.max(
    (props.selectedStorageItem.spaceSnapshotUsed ?? 0) / bytesPerGb,
    0
  );
});

const effectiveSnapshotReservePercent = computed(() => {
  if (isWorm.value) {
    return Math.min(
      Math.max(
        props.selectedStorageItem.spaceSnapshotReservePercent ?? 0,
        snapshotReserveMinPercent
      ),
      snapshotReserveMaxPercent
    );
  }

  return Math.min(
    Math.max(draftSnapshotReservePercent.value, snapshotReserveMinPercent),
    snapshotReserveMaxPercent
  );
});

const minSizeGb = computed(() => {
  const reserveFactor = 1 - effectiveSnapshotReservePercent.value / 100;
  const minForUsed =
    reserveFactor <= 0
      ? Number.POSITIVE_INFINITY
      : usedByAfsGb.value / reserveFactor;
  const minForTotalData = usedByAfsGb.value + snapshotUsedGb.value;

  return Math.ceil(Math.max(minForUsed, minForTotalData, 0));
});

const maxSizeGb = computed(() => {
  return Math.min(Math.ceil(currentSizeGb.value * 1.5), 2048);
});

const useMbSlider = computed(() => {
  return currentSizeGb.value < smallShareThresholdGb;
});

const sliderSizeValue = computed({
  get: () =>
    useMbSlider.value ? draftSizeGb.value * mbPerGb : draftSizeGb.value,
  set: (value: number) => {
    draftSizeGb.value = useMbSlider.value ? value / mbPerGb : value;
  },
});

const sliderMaxValue = computed(() => {
  return useMbSlider.value ? maxSizeGb.value * mbPerGb : maxSizeGb.value;
});

const sliderStep = computed(() => {
  return 1;
});

const sliderMinValue = computed(() => {
  if (useMbSlider.value) {
    return Math.ceil(minSizeGb.value * mbPerGb);
  }

  return Math.ceil(minSizeGb.value);
});

const sliderSizeLabel = computed(() => {
  return useMbSlider.value ? "Größe (MB)" : "Größe (GB)";
});

const currentSizeLabel = computed(() => {
  return useMbSlider.value
    ? `${(currentSizeGb.value * mbPerGb).toFixed(0)} MB`
    : `${currentSizeGb.value.toFixed(1)} GB`;
});


const minimumSizeLabel = computed(() => {
  return useMbSlider.value
    ? `${Math.ceil(minSizeGb.value * mbPerGb)} MB`
    : `${Math.ceil(minSizeGb.value)} GB`;
});

const title = computed(() => {
  if (!editable.value) {
    return "Nicht bearbeitbar";
  }

  if (props.selectedStorageItem.type === "NFS") {
    return `${props.selectedStorageItem.nfs_mount_path ?? props.selectedStorageItem.name} anpassen`;
  }

  if (props.selectedStorageItem.type === "CIFS") {
    return `${props.selectedStorageItem.cifs_mount_path ?? props.selectedStorageItem.name} anpassen`;
  }

  return "Share anpassen";
});

const validated = computed(() => {
  if (!editable.value) {
    return false;
  }

  const sizeValid =
    draftSizeGb.value >= minSizeGb.value &&
    draftSizeGb.value <= maxSizeGb.value;
  const reserveValid =
    isWorm.value ||
    (draftSnapshotReservePercent.value >= snapshotReserveMinPercent &&
      draftSnapshotReservePercent.value <= snapshotReserveMaxPercent);

  return sizeValid && reserveValid;
});

function initForm() {
  draftSnapshotReservePercent.value = isWorm.value
    ? 0
    : Math.min(
        props.selectedStorageItem.spaceSnapshotReservePercent ?? 0,
        snapshotReserveMaxPercent
      );
  draftSizeGb.value = Math.max(currentSizeGb.value, minSizeGb.value);
}

function resetForm() {
  initForm();
}

watch(dialog, (isOpen) => {
  if (isOpen) {
    initForm();
  } else {
    resetForm();
  }
});

watch(
  () => props.selectedStorageItem.uuid,
  () => {
    if (!dialog.value) {
      resetForm();
    }
  }
);

watch([minSizeGb, draftSnapshotReservePercent], () => {
  if (draftSizeGb.value < minSizeGb.value) {
    draftSizeGb.value = minSizeGb.value;
  }
});

function close() {
  dialog.value = false;
}

function save() {
  if (!validated.value) {
    return;
  }

  emit("save", {
    sizeGb: draftSizeGb.value,
    snapshotReservePercent: draftSnapshotReservePercent.value,
  });

  dialog.value = false;
}
</script>
