<template>
  <div>
    <v-row class="mb-4">
      <v-col class="text-right">
        <template v-if="isAdmin">
          <v-btn
            v-if="categories.length > 0"
            color="primary"
            class="mr-2"
            @click="openFaqDialog()"
          >
            FAQ Eintrag hinzufügen
            <v-icon
              start
              class="ml-2"
              >{{ mdiPlus }}</v-icon
            >
          </v-btn>
          <v-alert
            v-else-if="!loading"
            type="info"
            variant="tonal"
            density="compact"
            class="text-left mb-2"
          >
            Es muss zuerst eine FAQ-Kategorie erstellt werden, bevor Einträge
            hinzugefügt werden können.
          </v-alert>
        </template>
        <v-btn
          variant="outlined"
          border
          elevation="2"
          rounded="lg"
          color="backgroundLight"
          @click="toggleAll"
        >
          {{ allCollapsed ? "Alle ausklappen" : "Alle einklappen" }}
          <v-icon class="ml-2">
            {{
              allCollapsed ? mdiUnfoldMoreHorizontal : mdiUnfoldLessHorizontal
            }}
          </v-icon>
        </v-btn>
      </v-col>
    </v-row>

    <div
      v-if="loading && categories.length === 0"
      class="text-center py-10"
    >
      <v-progress-circular
        indeterminate
        color="primary"
      ></v-progress-circular>
    </div>

    <div
      v-for="(category, index) in sortedCategories"
      :key="category.id"
      class="mb-8"
    >
      <v-expansion-panels :model-value="0">
        <v-expansion-panel :title="category.name">
          <v-expansion-panel-text>
            <h2 class="text-h5 mb-4">
              <info-tooltip
                v-if="category.description"
                :text="category.description"
              />
            </h2>

            <common-card
              v-for="faq in getFaqsByCategory(category.id)"
              :id="`faq-${faq.id}`"
              :key="faq.id"
              :title="faq.question"
              :card-id="`faq-${faq.id}`"
              top-margin="0"
              :model-value="!collapsed[faq.id]"
              @update:model-value="(v) => (collapsed[faq.id] = !v)"
            >
              <template #toolbar-actions>
                <template v-if="isAdmin">
                  <v-chip
                    v-if="!faq.isPublished"
                    size="x-small"
                    color="warning"
                    variant="flat"
                    class="mr-1"
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

                  <div
                    class="text-caption text-medium-emphasis mr-2"
                    style="white-space: nowrap"
                  >
                    Sort: {{ faq.sortOrder }}
                  </div>

                  <v-btn
                    icon
                    size="small"
                    variant="text"
                    @click.stop="openFaqDialog(faq)"
                  >
                    <v-icon size="small">{{ mdiPencil }}</v-icon>
                  </v-btn>
                  <v-btn
                    icon
                    size="small"
                    variant="text"
                    color="error"
                    @click.stop="confirmDeleteFaq(faq)"
                  >
                    <v-icon size="small">{{ mdiDelete }}</v-icon>
                  </v-btn>
                  <v-divider
                    vertical
                    class="mx-2 my-2"
                  ></v-divider>
                </template>

                <v-btn
                  icon
                  size="small"
                  variant="text"
                  title="Link kopieren"
                  @click.stop="copyFaqLink(faq.id)"
                >
                  <v-icon size="small">{{ mdiLink }}</v-icon>
                </v-btn>
              </template>

              <div
                class="faq-content"
                v-html="faq.answerHtml"
              ></div>
            </common-card>
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>

      <v-divider
        v-if="index < sortedCategories.length - 1"
        class="mt-6"
      ></v-divider>
    </div>

    <!-- FAQ Edit Dialog -->
    <common-dialog
      v-model="faqDialog"
      :title="editedFaq.id ? 'Eintrag bearbeiten' : 'Neuer FAQ Eintrag'"
      :max-width="800"
      :submit-activated="true"
      :show-actions="true"
      @dialog-cancel="faqDialog = false"
      @dialog-confirm="saveFaq"
    >
      <v-select
        v-model="editedFaq.categoryId"
        :items="categories"
        item-title="name"
        item-value="id"
        label="Kategorie"
        required
        :menu-props="{ persistent: true, closeOnContentClick: true }"
      ></v-select>
      <v-text-field
        v-model="editedFaq.question"
        label="Frage"
        required
      ></v-text-field>
      <v-textarea
        v-model="editedFaq.answerMarkdown"
        label="Antwort (Markdown)"
        rows="8"
        hint="Formatierung mit Markdown möglich"
        persistent-hint
        required
      ></v-textarea>
      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model.number="editedFaq.sortOrder"
            label="Sortierung"
            type="number"
          ></v-text-field>
        </v-col>
        <v-col cols="6">
          <v-checkbox
            v-model="editedFaq.isPublished"
            label="Veröffentlicht"
          ></v-checkbox>
        </v-col>
      </v-row>
    </common-dialog>

    <!-- Delete Confirm -->
    <common-dialog
      v-model="deleteConfirmDialog"
      title="Eintrag löschen?"
      :max-width="500"
      :submit-activated="true"
      :show-actions="true"
      @dialog-cancel="deleteConfirmDialog = false"
      @dialog-confirm="deleteFaq"
    >
      Sind Sie sicher, dass Sie den Eintrag
      <strong>{{ faqToDelete?.question }}</strong> löschen möchten?
    </common-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  mdiDelete,
  mdiLink,
  mdiPencil,
  mdiPlus,
  mdiUnfoldLessHorizontal,
  mdiUnfoldMoreHorizontal,
} from "@mdi/js";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";

import faqCategoryService from "@/api/faqCategoryService";
import faqService from "@/api/faqService";
import CommonCard from "@/components/common/CommonCard.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";

const props = defineProps({
  isAdmin: { type: Boolean, default: false },
});

const route = useRoute();

const loading = ref(false);
const faqs = ref([]);
const categories = ref([]);
const collapsed = ref({});

const faqDialog = ref(false);
const deleteConfirmDialog = ref(false);
const faqToDelete = ref(null);

const defaultFaq = {
  id: null,
  categoryId: null,
  question: "",
  answerMarkdown: "",
  answerHtml: "",
  sortOrder: 0,
  isPublished: true,
};
const editedFaq = ref({ ...defaultFaq });
const allCollapsed = computed(() => {
  return Object.values(collapsed.value).every((val) => val === true);
});
const sortedCategories = computed(() => {
  return [...categories.value].sort((a, b) => a.sortOrder - b.sortOrder);
});

async function loadData() {
  try {
    const [cats, allFaqs] = await Promise.all([
      faqCategoryService.getAllCategories(loading),
      faqService.getAllFaqs(loading),
    ]);
    categories.value = cats;
    faqs.value = allFaqs;

    // Initial alle einklappen
    allFaqs.forEach((f) => {
      if (collapsed.value[f.id] === undefined) {
        collapsed.value[f.id] = true;
      }
    });

    // Prüfen, ob eine ID in der URL ist und dahin scrollen
    scrollToFaq(route.params.faqId);
  } catch (error) {
    console.error("Fehler beim Laden der FAQ Daten", error);
  }
}

function getFaqsByCategory(categoryId) {
  return faqs.value
    .filter(
      (f) => f.categoryId === categoryId && (props.isAdmin || f.isPublished)
    )
    .sort((a, b) => a.sortOrder - b.sortOrder);
}

function toggleAll() {
  const targetState = !allCollapsed.value;
  faqs.value.forEach((f) => {
    collapsed.value[f.id] = targetState;
  });
}

function openFaqDialog(faq = null) {
  if (faq) {
    editedFaq.value = { ...faq };
  } else {
    editedFaq.value = { ...defaultFaq, categoryId: categories.value[0]?.id };
  }
  faqDialog.value = true;
}

async function saveFaq() {
  try {
    if (editedFaq.value.id) {
      await faqService.updateFaq(loading, editedFaq.value.id, editedFaq.value);
    } else {
      await faqService.createFaq(loading, editedFaq.value);
    }
    faqDialog.value = false;
    await loadData();
  } catch (error) {
    console.error("Fehler beim Speichern der FAQ", error);
  }
}

function confirmDeleteFaq(faq) {
  faqToDelete.value = faq;
  deleteConfirmDialog.value = true;
}

async function deleteFaq() {
  if (!faqToDelete.value) return;
  try {
    await faqService.deleteFaq(loading, faqToDelete.value.id);
    deleteConfirmDialog.value = false;
    await loadData();
  } catch (error) {
    console.error("Fehler beim Löschen der FAQ", error);
  }
}

function copyFaqLink(faqId) {
  const url = `${window.location.origin}${window.location.pathname}#/help/${faqId}`;
  navigator.clipboard.writeText(url);
}

function scrollToFaq(faqId) {
  if (!faqId || faqs.value.length === 0) return;

  const targetFaq = faqs.value.find((f) => f.id === parseInt(faqId));
  if (targetFaq) {
    collapsed.value[targetFaq.id] = false;
    // Scroll zur FAQ nach kurzem Delay
    setTimeout(() => {
      const element = document.getElementById(`faq-${faqId}`);
      element?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 100);
  }
}

watch(
  () => route.params.faqId,
  (newId) => {
    scrollToFaq(newId);
  }
);

onMounted(loadData);
</script>

<style scoped>
.faq-content :deep(a) {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: underline;
}
.faq-content :deep(p) {
  margin-bottom: 1rem;
}
.faq-content :deep(p:last-child) {
  margin-bottom: 0;
}
.faq-content :deep(p:last-child) {
  margin-bottom: 0;
}
.faq-content :deep(ul),
.faq-content :deep(ol) {
  padding-left: 24px;
  margin-bottom: 12px;
}
.faq-content :deep(li) {
  margin-bottom: 4px;
}
</style>
