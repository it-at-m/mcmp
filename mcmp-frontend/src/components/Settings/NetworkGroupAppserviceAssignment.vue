<template>
  <v-dialog
    v-model="dialog"
    width="80%"
    max-width="920px"
  >
    <template #activator="{ props }">
      <v-btn
        variant="flat"
        v-bind="props"
        :icon="mdiPencil"
        :aria-label="
          'Appservices der Netzwerkgruppe ' + networkgroup.name + ' bearbeiten'
        "
      ></v-btn>
    </template>
    <v-card>
      <v-toolbar>
        <v-toolbar-title class="text-h5">
          "{{ networkgroup.name }}" bearbeiten
        </v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          @click="cancel()"
        />
      </v-toolbar>
      <v-card-text>
        <v-form ref="form">
          <!-- Suchfeld -->
          <v-text-field
            v-model="searchQuery"
            label="Appservices suchen..."
            :prepend-inner-icon="mdiMagnify"
            :append-inner-icon="searchQuery ? mdiClose : undefined"
            @click:append-inner="searchQuery = ''"
            variant="outlined"
            density="compact"
            class="mb-1"
          />

          <!-- Statistik -->
          <div class="mb-3">
            <v-chip
              color="primary"
              variant="outlined"
              class="mr-2"
            >
              {{ toAddAppservices.length }} ausgewählt
            </v-chip>
            <v-chip
              color="info"
              variant="outlined"
            >
              {{ appserviceSelectList.length }} Appservices
            </v-chip>
          </div>

          <!-- Filter und Bulk-Aktionen -->
          <div class="mb-4">
            <v-btn
              variant="outlined"
              size="small"
              @click="setFilter('selected')"
              :color="filterMode === 'selected' ? 'primary' : undefined"
              class="mr-2"
            >
              <v-icon class="mr-1">{{ mdiFilterCheck }}</v-icon>
              Nur ausgewählte
            </v-btn>
            <v-btn
              variant="outlined"
              size="small"
              @click="setFilter('unselected')"
              :color="filterMode === 'unselected' ? 'primary' : undefined"
              class="mr-2"
            >
              <v-icon class="mr-1">{{ mdiFilterRemove }}</v-icon>
              Nur nicht ausgewählte
            </v-btn>
            <v-btn
              variant="outlined"
              size="small"
              @click="selectAllFiltered"
              class="mr-2"
            >
              {{
                searchQuery || filterMode !== "all"
                  ? "Gefilterte auswählen"
                  : "Alle auswählen"
              }}
            </v-btn>
            <v-btn
              variant="outlined"
              size="small"
              @click="deselectAllFiltered"
              class="mr-2"
            >
              {{
                searchQuery || filterMode !== "all"
                  ? "Gefilterte abwählen"
                  : "Alle abwählen"
              }}
            </v-btn>
            <v-btn
              variant="outlined"
              size="small"
              @click="resetToOriginal"
              class="mr-2"
            >
              <v-icon class="mr-1">{{ mdiRestore }}</v-icon>
              Zurücksetzen
              <v-tooltip
                activator="parent"
                location="top"
              >
                Auf ursprüngliche Auswahl zurücksetzen
              </v-tooltip>
            </v-btn>
          </div>

          <!-- Kompakte Liste ohne doppelte Scrollbars -->
          <div class="appservice-list-container">
            <v-list
              density="compact"
              class="appservice-list"
            >
              <template
                v-for="item in displayedAppservices"
                :key="item.id"
              >
                <v-list-item
                  class="appservice-item"
                  :class="{ 'selected-item': isSelected(item.id) }"
                  @click="toggleSelection(item.id)"
                >
                  <template #prepend>
                    <v-checkbox
                      :model-value="isSelected(item.id)"
                      density="compact"
                      hide-details
                      class="appservice-checkbox"
                    />
                  </template>
                  <v-list-item-title class="appservice-title">
                    {{ item.name }}
                  </v-list-item-title>
                  <template
                    #append
                    v-if="isSelected(item.id)"
                  >
                    <v-icon
                      color="primary"
                      size="small"
                      >{{ mdiCheck }}
                    </v-icon>
                  </template>
                </v-list-item>
              </template>
              <div
                v-if="displayedAppservices.length === 0"
                class="no-results"
              >
                <v-icon class="mb-2">{{ mdiInformationOutline }}</v-icon>
                <div>{{ getNoResultsMessage() }}</div>
              </div>
            </v-list>
          </div>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="cancel"
          text
          @click="cancel"
          >Abbrechen
        </v-btn>
        <v-btn
          variant="flat"
          color="do"
          @click="save"
          >Speichern
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type AppserviceList from "@/types/AppserviceList";
import type NetworkGroup from "@/types/NetworkGroup.ts";

import {
  mdiCheck,
  mdiClose,
  mdiFilterCheck,
  mdiFilterRemove,
  mdiInformationOutline,
  mdiMagnify,
  mdiPencil,
  mdiRestore,
} from "@mdi/js";
import { computed, ref, watch } from "vue";

import appserviceService from "@/api/appserviceService";

const props = defineProps<{
  networkgroup: NetworkGroup;
}>();

const emit = defineEmits<{
  (e: "save", networkgroup: NetworkGroup, appservices: number[]): void;
}>();

const form = ref<HTMLFormElement>();
const dialog = ref(false);
const loading = ref(false);
const searchQuery = ref("");
const filterMode = ref<"all" | "selected" | "unselected">("all");
const appserviceSelectList = ref<AppserviceList[]>([]);
const toAddAppservices = ref<number[]>([]);
const originalAppservices = ref<number[]>([]);
const appservicesLoaded = ref(false);

// Gefilterte Liste basierend auf Suchbegriff
const filteredAppservices = computed(() => {
  if (!searchQuery.value) return appserviceSelectList.value;

  const query = searchQuery.value.toLowerCase();
  return appserviceSelectList.value.filter((item) =>
    item.name.toLowerCase().includes(query)
  );
});

// Angezeigte Liste basierend auf Filter (alle, nur ausgewählte oder nur nicht ausgewählte)
const displayedAppservices = computed(() => {
  switch (filterMode.value) {
    case "selected":
      return filteredAppservices.value.filter((item) => isSelected(item.id));
    case "unselected":
      return filteredAppservices.value.filter((item) => !isSelected(item.id));
    default:
      return filteredAppservices.value;
  }
});

watch(
  () => dialog.value,
  (newValue) => {
    if (newValue) {
      // Lazy Load: Appservices nur beim ersten Öffnen laden
      if (!appservicesLoaded.value) {
        getAppservices();
      }

      // Aktualisiere die ursprüngliche Auswahl basierend auf den aktuellen Props
      const currentAppservices =
        props.networkgroup.appservices.map((appservice) => appservice.id) || [];
      originalAppservices.value = [...currentAppservices];
      resetForm();
    }
  }
);

// Überwache auch Änderungen an den Props für den Fall, dass sich die Netzwerkgruppe ändert
watch(
  () => props.networkgroup.appservices,
  (newAppservices) => {
    const currentAppservices =
      newAppservices.map((appservice) => appservice.id) || [];
    // Nur aktualisieren, wenn der Dialog nicht geöffnet ist
    if (!dialog.value) {
      originalAppservices.value = [...currentAppservices];
      toAddAppservices.value = [...currentAppservices];
    }
  },
  { deep: true }
);

function isSelected(id: number): boolean {
  return toAddAppservices.value.includes(id);
}

function toggleSelection(id: number) {
  const index = toAddAppservices.value.indexOf(id);
  if (index > -1) {
    toAddAppservices.value.splice(index, 1);
  } else {
    toAddAppservices.value.push(id);
  }
}

function setFilter(mode: "all" | "selected" | "unselected") {
  if (filterMode.value === mode) {
    filterMode.value = "all";
  } else {
    filterMode.value = mode;
  }
}

// Neue Funktionen für bessere Handhabung der Auswahl
function selectAllFiltered() {
  // Fügt nur die aktuell angezeigten, noch nicht ausgewählten Items hinzu
  const currentIds = displayedAppservices.value.map((item) => item.id);
  const newSelections = currentIds.filter(
    (id) => !toAddAppservices.value.includes(id)
  );
  toAddAppservices.value.push(...newSelections);
}

function deselectAllFiltered() {
  // Entfernt nur die aktuell angezeigten Items aus der Auswahl
  const currentIds = displayedAppservices.value.map((item) => item.id);
  toAddAppservices.value = toAddAppservices.value.filter(
    (id) => !currentIds.includes(id)
  );
}

function resetToOriginal() {
  toAddAppservices.value = [...originalAppservices.value];
  filterMode.value = "all";
  searchQuery.value = "";
}

function getNoResultsMessage(): string {
  switch (filterMode.value) {
    case "selected":
      return "Keine Appservices ausgewählt";
    case "unselected":
      return "Alle verfügbaren Appservices sind bereits ausgewählt";
    default:
      return "Keine Ergebnisse gefunden";
  }
}

function getAppservices() {
  appserviceService
    .getAppservices(loading, 0, -1, "asc", "")
    .then((appservices) => {
      appserviceSelectList.value = appservices.content;
      appserviceSelectList.value = appserviceSelectList.value.sort((a, b) =>
        a.name.localeCompare(b.name)
      );
      appserviceSelectList.value = appserviceSelectList.value.filter(
        (appservice) => {
          return (
            String(appservice.environment) ===
            String(props.networkgroup.environment)
          );
        }
      );
      appservicesLoaded.value = true;
    });
}

function cancel() {
  dialog.value = false;
  resetForm();
}

function resetForm() {
  searchQuery.value = "";
  filterMode.value = "all";
  toAddAppservices.value = [...originalAppservices.value];
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emit("save", props.networkgroup, toAddAppservices.value);
      dialog.value = false;
      resetForm();
    }
  });
}
</script>

<style scoped>
.appservice-list-container {
  height: 400px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  overflow: hidden;
}

.appservice-list {
  height: 100%;
  overflow-y: auto;
  padding: 0;
}

.appservice-item {
  min-height: 40px !important;
  padding: 4px 16px !important;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.appservice-item:hover {
  background-color: rgba(0, 0, 0, 0.04);
}

.appservice-item.selected-item {
  background-color: rgba(25, 118, 210, 0.08);
}

.appservice-item.selected-item:hover {
  background-color: rgba(25, 118, 210, 0.12);
}

.appservice-checkbox {
  margin-right: 12px;
}

.appservice-checkbox :deep(.v-selection-control) {
  min-height: 24px;
  align-items: center;
}

.appservice-checkbox :deep(.v-selection-control__wrapper) {
  height: 24px;
}

.appservice-title {
  line-height: 1.2;
  font-size: 14px;
}

.no-results {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: rgba(0, 0, 0, 0.6);
  text-align: center;
}

/* Scrollbar-Styling für bessere Optik */
.appservice-list::-webkit-scrollbar {
  width: 8px;
}

.appservice-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.appservice-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.appservice-list::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
