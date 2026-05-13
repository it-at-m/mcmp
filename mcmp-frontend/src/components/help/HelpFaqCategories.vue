<template>
  <div>
    <v-row class="mb-4">
      <v-col class="text-right">
        <v-btn
          color="primary"
          @click="openCategoryDialog()"
        >
          Kategorie hinzufügen
          <v-icon
            start
            class="ml-2"
            >{{ mdiPlus }}</v-icon
          >
        </v-btn>
      </v-col>
    </v-row>

    <v-data-table
      :headers="categoryHeaders"
      :items="categories"
      :loading="loading"
      items-per-page="-1"
      hide-default-footer
      no-data-text="Keine Kategorien vorhanden"
      loading-text="Lade Kategorien..."
    >
      <template #[`item.actions`]="{ item }">
        <v-btn
          icon
          variant="text"
          size="small"
          @click="openCategoryDialog(item)"
        >
          <v-icon>{{ mdiPencil }}</v-icon>
        </v-btn>
        <v-btn
          icon
          variant="text"
          size="small"
          color="error"
          @click="confirmDeleteCategory(item)"
        >
          <v-icon>{{ mdiDelete }}</v-icon>
        </v-btn>
      </template>
    </v-data-table>

    <!-- Dialog for FAQ Category -->
    <v-dialog
      v-model="categoryDialog"
      max-width="500px"
    >
      <v-card>
        <v-card-title>{{
          editedCategory.id ? "Kategorie bearbeiten" : "Neue FAQ Kategorie"
        }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="editedCategory.name"
            label="Name"
            required
          ></v-text-field>
          <v-text-field
            v-model.number="editedCategory.sortOrder"
            label="Sortierung"
            type="number"
            required
          ></v-text-field>
          <v-textarea
            v-model="editedCategory.description"
            label="Beschreibung (optional)"
            rows="3"
          ></v-textarea>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            variant="text"
            @click="categoryDialog = false"
            >Abbrechen</v-btn
          >
          <v-btn
            color="primary"
            @click="saveCategory"
            :loading="loading"
            >Speichern</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Delete Confirmation Dialog -->
    <v-dialog
      v-model="deleteConfirmDialog"
      max-width="500px"
    >
      <v-card>
        <v-card-title class="text-h5">Kategorie löschen?</v-card-title>
        <v-card-text>
          Sind Sie sicher, dass Sie die Kategorie
          <strong>{{ categoryToDelete?.name }}</strong> löschen möchten?
          <v-alert
            type="warning"
            variant="tonal"
            class="mt-4"
          >
            Hinweis: Alle zugeordneten FAQ-Einträge werden ebenfalls gelöscht.
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            variant="text"
            @click="deleteConfirmDialog = false"
            >Abbrechen</v-btn
          >
          <v-btn
            color="error"
            @click="deleteCategory"
            :loading="loading"
            >Endgültig löschen</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup>
import { mdiDelete, mdiPencil, mdiPlus } from "@mdi/js";
import { onMounted, ref } from "vue";

import faqCategoryService from "@/api/faqCategoryService";

const loading = ref(false);
const categories = ref([]);
const categoryDialog = ref(false);
const deleteConfirmDialog = ref(false);

const defaultCategory = { id: null, name: "", sortOrder: 0, description: "" };
const editedCategory = ref({ ...defaultCategory });
const categoryToDelete = ref(null);

const categoryHeaders = [
  { title: "Name", key: "name" },
  { title: "Beschreibung", key: "description" },
  { title: "Sortierung", key: "sortOrder", width: "30px" },
  { title: "Aktionen", key: "actions", sortable: false, align: "end" },
];

async function loadCategories() {
  try {
    categories.value = await faqCategoryService.getAllCategories(loading);
  } catch (error) {
    console.error("Fehler beim Laden der FAQ Kategorien", error);
  }
}

function openCategoryDialog(category = null) {
  if (category) {
    editedCategory.value = { ...category };
  } else {
    editedCategory.value = { ...defaultCategory };
  }
  categoryDialog.value = true;
}

async function saveCategory() {
  try {
    if (editedCategory.value.id) {
      await faqCategoryService.updateCategory(
        loading,
        editedCategory.value.id,
        editedCategory.value
      );
    } else {
      await faqCategoryService.createCategory(loading, editedCategory.value);
    }
    categoryDialog.value = false;
    await loadCategories();
  } catch (error) {
    console.error("Fehler beim Speichern der Kategorie", error);
  }
}

function confirmDeleteCategory(category) {
  categoryToDelete.value = category;
  deleteConfirmDialog.value = true;
}

async function deleteCategory() {
  if (!categoryToDelete.value) return;
  try {
    await faqCategoryService.deleteCategory(loading, categoryToDelete.value.id);
    deleteConfirmDialog.value = false;
    categoryToDelete.value = null;
    await loadCategories();
  } catch (error) {
    console.error("Fehler beim Löschen der Kategorie", error);
  }
}

onMounted(() => {
  loadCategories();
});
</script>
