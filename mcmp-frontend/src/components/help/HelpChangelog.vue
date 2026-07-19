<template>
  <div>
    <v-row
      v-if="isAdmin"
      class="mb-4"
    >
      <v-col class="text-right">
        <v-btn
          color="primary"
          @click="openEditDialog()"
        >
          Eintrag hinzufügen
          <v-icon
            start
            class="ml-2"
            >{{ mdiPlus }}</v-icon
          >
        </v-btn>
      </v-col>
    </v-row>

    <v-progress-linear
      v-if="loading"
      indeterminate
      color="primary"
      class="mb-4"
    ></v-progress-linear>

    <v-card
      v-for="entry in changelogs"
      :key="entry.id"
      outlined
      border
      elevation="2"
      class="mb-4"
      rounded="lg"
      :style="!entry.isPublished ? 'border-left: 4px solid orange' : ''"
    >
      <v-card-title class="d-flex justify-space-between align-center">
        <div>
          <span class="text-h6">Version {{ entry.appVersion }}</span>
          <template v-if="isAdmin">
            <v-chip
              v-if="!entry.isPublished"
              color="warning"
              size="x-small"
              class="ml-2"
              variant="flat"
            >
              Entwurf
            </v-chip>
            <v-chip
              v-else
              size="x-small"
              color="success"
              variant="tonal"
              class="mr-1"
            >
              Veröffentlicht
            </v-chip>
          </template>
          <span class="text-subtitle-2 ml-4 text-grey">{{
            formatToBerlinDate(entry.createdAt)
          }}</span>
        </div>
        <div v-if="isAdmin">
          <v-btn
            icon
            variant="text"
            size="small"
            @click="openEditDialog(entry)"
          >
            <v-icon>{{ mdiPencil }}</v-icon>
          </v-btn>
          <v-btn
            icon
            variant="text"
            size="small"
            color="error"
            @click="deleteEntry(entry.id)"
          >
            <v-icon>{{ mdiDelete }}</v-icon>
          </v-btn>
        </div>
      </v-card-title>
      <v-divider></v-divider>
      <v-card-text>
        <div
          class="changelog-content"
          v-html="entry.contentHtml || entry.contentMarkdown"
        ></div>
      </v-card-text>
    </v-card>

    <v-alert
      v-if="!loading && changelogs.length === 0"
      type="info"
      variant="tonal"
    >
      Keine Changelog-Einträge gefunden.
    </v-alert>

    <v-pagination
      v-if="totalPages > 1"
      v-model="currentPage"
      :length="totalPages"
      :total-visible="5"
      class="mt-4"
      @update:model-value="loadChangelogs"
    ></v-pagination>

    <!-- Dialog for Create/Edit Changelog -->
    <v-dialog
      v-model="editDialog"
      max-width="800px"
    >
      <v-card>
        <v-card-title>{{
          editedEntry.id ? "Eintrag bearbeiten" : "Neuer Changelog Eintrag"
        }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="editedEntry.appVersion"
            label="App Version (z.B. 1.0.5)"
            required
          ></v-text-field>
          <v-text-field
            v-model="editedEntry.createdAt"
            label="Datum"
            type="date"
            required
          ></v-text-field>
          <v-textarea
            v-model="editedEntry.contentMarkdown"
            label="Inhalt (Markdown)"
            rows="10"
            required
            hint="Verwenden Sie Markdown für die Formatierung."
          ></v-textarea>
          <v-checkbox
            v-model="editedEntry.isPublished"
            label="Veröffentlichen"
            hint="Wenn nicht gesetzt, ist der Eintrag nur für Admins sichtbar."
            persistent-hint
          ></v-checkbox>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            variant="text"
            @click="editDialog = false"
            >Abbrechen</v-btn
          >
          <v-btn
            color="primary"
            :loading="loading"
            @click="saveEntry"
            >Speichern</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup>
import { mdiDelete, mdiPencil, mdiPlus } from "@mdi/js";
import { onMounted, ref } from "vue";

import changelogService from "@/api/changelogService";
import { useFormatter } from "@/composables/formatter";

defineProps({
  isAdmin: {
    type: Boolean,
    default: false,
  },
});

const { formatToBerlinDate } = useFormatter();
const loading = ref(false);
const changelogs = ref([]);
const editDialog = ref(false);
const currentPage = ref(1);
const totalPages = ref(0);
const itemsPerPage = 5;

const defaultEntry = {
  id: null,
  appVersion: "",
  contentMarkdown: "",
  isPublished: false,
  createdAt: new Date().toISOString().substr(0, 10),
};
const editedEntry = ref({ ...defaultEntry });

async function loadChangelogs() {
  try {
    const offset = (currentPage.value - 1) * itemsPerPage;
    const response = await changelogService.getAllChangelogs(
      loading,
      offset,
      itemsPerPage
    );
    if (response.page) {
      changelogs.value = response.content || [];
      totalPages.value = response.page.totalPages || 0;
    } else {
      changelogs.value = response.content || [];
      totalPages.value = response.totalPages || 0;
    }
  } catch (error) {
    console.error("Fehler beim Laden der Changelogs", error);
  }
}

function openEditDialog(entry = null) {
  if (entry) {
    const datePart = entry.createdAt
      ? entry.createdAt.split("T")[0]
      : new Date().toISOString().substr(0, 10);
    editedEntry.value = { ...entry, createdAt: datePart };
  } else {
    editedEntry.value = { ...defaultEntry };
  }
  editDialog.value = true;
}

async function saveEntry() {
  try {
    const payload = { ...editedEntry.value };
    if (payload.createdAt) {
      payload.createdAt = new Date(payload.createdAt).toISOString();
    }
    if (editedEntry.value.id) {
      await changelogService.updateChangelog(
        loading,
        editedEntry.value.id,
        payload
      );
    } else {
      await changelogService.createChangelog(loading, payload);
    }
    editDialog.value = false;
    await loadChangelogs();
  } catch (error) {
    console.error("Fehler beim Speichern", error);
  }
}

async function deleteEntry(id) {
  if (confirm("Möchten Sie diesen Eintrag wirklich löschen?")) {
    try {
      await changelogService.deleteChangelog(loading, id);
      await loadChangelogs();
    } catch (error) {
      console.error("Fehler beim Löschen", error);
    }
  }
}

onMounted(() => {
  loadChangelogs();
});
</script>

<style scoped>
.changelog-content :deep(ul),
.changelog-content :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}

.changelog-content :deep(li) {
  margin-bottom: 4px;
}
</style>
