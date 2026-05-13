<template>
  <v-dialog
    v-model="dialog"
    :max-width="maxWidth"
    persistent
    class="modern-dialog"
    @keyup.esc="onDialogCancel()"
  >
    <template #activator="{ props }">
      <slot
        name="activator"
        :props="props"
      />
    </template>
    <v-card
      class="modern-card"
      elevation="24"
      v-if="showMainCard"
    >
      <v-toolbar
        class="dialog-header"
        flat
      >
        <v-toolbar-title>
          <div class="header-content">
            <div
              class="header-icon"
              v-if="icon"
            >
              <v-icon
                :icon="icon"
                size="28"
                color="text"
              />
            </div>
            <h2 class="dialog-title">{{ title || "Aktion Bestätigen" }}</h2>
          </div>
        </v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          variant="text"
          color="text"
          size="default"
          class="close-btn"
          @click="onDialogCancel"
        />
      </v-toolbar>
      <v-card-text class="dialog-content">
        <CommonAlert
          isSnowChange
          v-if="showChangeWarning"
        />
        <br />
        <slot />
      </v-card-text>
      <v-card-actions
        class="dialog-actions"
        v-if="showActions"
      >
        <slot name="actions">
          <v-spacer />
          <v-btn
            :append-icon="mdiClose"
            variant="outlined"
            color="cancel"
            size="large"
            class="action-btn cancel-btn"
            @click="onDialogCancel"
            rounded="xl"
          >
            Abbrechen
          </v-btn>
          <v-btn
            :append-icon="mdiCheck"
            variant="flat"
            color="do"
            size="large"
            class="action-btn confirm-btn"
            @click="onDialogConfirm"
            rounded="xl"
            :disabled="!submitActivated"
          >
            Bestätigen
          </v-btn>
        </slot>
      </v-card-actions>
    </v-card>
    <v-card
      class="modern-card"
      elevation="24"
      v-else-if="!showMainCard"
    >
      <v-toolbar
        class="dialog-header"
        flat
      >
        <v-toolbar-title>
          <div class="header-content">
            <div
              class="header-icon"
              v-if="icon"
            >
              <v-icon
                :icon="icon"
                size="28"
                color="text"
              />
            </div>
            <h2 class="dialog-title">Aktion ist Deaktiviert</h2>
          </div>
        </v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          variant="text"
          color="text"
          size="default"
          class="close-btn"
          @click="onDialogCancel"
        />
      </v-toolbar>
      <v-card-text class="dialog-content">
        <CommonAlert color="error">
          <div
            v-for="(enabled, action) in actionsEnabled"
            :key="action"
          >
            <div v-if="!enabled">
              Die Aktion {{ action }} ist derzeit deaktiviert und kann nicht
              ausgeführt werden.
            </div>
          </div>
          <div>
            Dies kann verschiedene Gründe haben, wie z.B. Wartung, oder eine
            fehlerhafte Konfiguration.
            <br class="mb-3" />
            Falls Sie eine Aktion in diesem Dialog ausführen möchten welche
            nicht oben genannt ist, drücken sie auf "Weiter" um fortzufahren.
            (Wenn z.b. In der Serverinstallation nur die Aktion Windows Server
            Installation deaktiviert ist, nicht aber die Linux Server
            Installation.)
          </div>
        </CommonAlert>
      </v-card-text>
      <v-card-actions class="dialog-actions">
        <v-spacer />
        <v-btn
          :append-icon="mdiClose"
          variant="outlined"
          color="cancel"
          size="large"
          class="action-btn cancel-btn"
          @click="onDialogCancel"
          rounded="xl"
        >
          Abbrechen
        </v-btn>
        <v-btn
          :append-icon="mdiCheck"
          variant="flat"
          color="do"
          size="large"
          class="action-btn confirm-btn"
          @click="acknowledgedActionDisabled = true"
          rounded="xl"
          :disabled="
            (actionsEnabled.value &&
              Object.values(actionsEnabled.value).every(
                (enabled) => !enabled
              )) ||
            acknowledgedActionDisabled
          "
        >
          Weiter
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { mdiCheck, mdiClose } from "@mdi/js";
import { computed, ref, watch } from "vue";

import actionService from "@/api/actionService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";

const props = defineProps<{
  modelValue: boolean;
  title?: string;
  icon?: string;
  color?: string;
  showActions?: boolean;
  maxWidth?: number | string;
  submitActivated: boolean;
  showChangeWarning?: boolean;
  checkForEnabledActions?: string[];
}>();
const loading = ref(false);
const actionsEnabled = ref<Record<string, boolean>>({});
const actionsChecked = ref(false);
const acknowledgedActionDisabled = ref(false);

async function onOpenedCheckActions() {
  actionsChecked.value = false;
  if (props.checkForEnabledActions) {
    actionsEnabled.value = props.checkForEnabledActions.reduce<
      Record<string, boolean>
    >((acc, action) => {
      acc[action] = false;
      return acc;
    }, {});
    const promises = props.checkForEnabledActions.map((action) =>
      actionService.getActionEnabled(loading, action).then((response) => {
        actionsEnabled.value[action] = response;
      })
    );
    await Promise.all(promises);
    actionsChecked.value = true;
  } else {
    actionsChecked.value = true;
  }
}

const showMainCard = computed(() => {
  return (
    !props.checkForEnabledActions ||
    (actionsChecked.value &&
      Object.values(actionsEnabled.value ?? {}).every((enabled) =>
        Boolean(enabled)
      )) ||
    acknowledgedActionDisabled.value
  );
});

const emit = defineEmits([
  "update:modelValue",
  "dialogConfirm",
  "dialogCancel",
]);

const dialog = computed({
  get: () => props.modelValue ?? false,
  set: (val: boolean) => emit("update:modelValue", val),
});

watch(
  dialog,
  (val) => {
    if (val) {
      acknowledgedActionDisabled.value = false;
      onOpenedCheckActions();
    }
  },
  { immediate: true }
);

function onDialogConfirm() {
  emit("dialogConfirm");
}

function onDialogCancel() {
  emit("dialogCancel");
}
</script>

<style scoped>
.modern-card {
  border-radius: 16px !important;
  overflow: hidden;
  background: rgb(var(--v-theme-bg));
}

.dialog-header {
  background: rgb(var(--v-theme-bg_light));
  padding: 20px 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.header-icon {
  width: 48px;
  height: 48px;
  background: rgb(var(--v-theme-bg));
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgb(var(--v-theme-bg_dark));
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog-title {
  color: rgb(var(--v-theme-text));
  font-size: 1.375rem;
  margin: 0;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.close-btn {
  opacity: 0.9;
  transition: all 0.2s ease;
}

.close-btn:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.1) !important;
}

.dialog-content {
  padding: 32px 24px !important;
}

.dialog-actions {
  padding: 16px 24px 24px !important;
  gap: 12px;
}

.action-btn {
  min-width: 120px;
  height: 44px;
  border-radius: 12px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: 0.5px;
  transition: all 0.3s ease;
}

.cancel-btn {
  border: 2px solid #90a4ae;
  color: rgb(var(--v-theme-cancel));
}

.cancel-btn:hover {
  background: rgb(var(--v-theme-bg_light));
  border-color: #90a4ae;
  transform: translateY(-1px);
}

.confirm-btn {
  background: linear-gradient(135deg, #3d74b6 0%, #3d74b6 100%);
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.3);
  color: white !important;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #2196f3 0%, #3d74b6 100%);
  transform: translateY(-1px);
}
</style>
