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
              <InfoTooltip
                v-if="category.description"
                :text="category.description"
              />
            </h2>

            <v-card
              v-for="faq in getFaqsByCategory(category.id)"
              :key="faq.id"
              :id="`faq-${faq.id}`"
              color="backgroundLight"
              variant="flat"
              class="mb-4 border"
              rounded="lg"
            >
              <v-toolbar
                density="compact"
                color="transparent"
                class="pr-2"
              >
                <div class="text-h6 py-2 px-4 flex-grow-1 custom-question-text">
                  {{ faq.question }}
                </div>

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
                  @click.stop="copyFaqLink(faq.id)"
                  title="Link kopieren"
                >
                  <v-icon size="small">{{ mdiLink }}</v-icon>
                </v-btn>

                <v-btn
                  variant="flat"
                  icon
                  @click="collapsed[faq.id] = !collapsed[faq.id]"
                >
                  <v-icon>{{
                    collapsed[faq.id] ? mdiChevronDown : mdiChevronUp
                  }}</v-icon>
                </v-btn>
              </v-toolbar>

              <v-divider v-if="!collapsed[faq.id]"></v-divider>
              <v-card-text v-if="!collapsed[faq.id]">
                <div
                  class="faq-content"
                  v-html="faq.answerHtml"
                ></div>
              </v-card-text>
            </v-card>
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>

      <v-divider
        v-if="index < sortedCategories.length - 1"
        class="mt-6"
      ></v-divider>
    </div>

    <!-- FAQ Edit Dialog -->
    <v-dialog
      v-model="faqDialog"
      max-width="800px"
    >
      <v-card>
        <v-card-title>{{
          editedFaq.id ? "Eintrag bearbeiten" : "Neuer FAQ Eintrag"
        }}</v-card-title>
        <v-card-text>
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
        </v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn
            variant="text"
            @click="faqDialog = false"
            >Abbrechen</v-btn
          >
          <v-btn
            color="primary"
            @click="saveFaq"
            :loading="loading"
            >Speichern</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Delete Confirm -->
    <v-dialog
      v-model="deleteConfirmDialog"
      max-width="500px"
    >
      <v-card>
        <v-card-title>Eintrag löschen?</v-card-title>
        <v-card-text>
          Sind Sie sicher, dass Sie den Eintrag
          <strong>{{ faqToDelete?.question }}</strong> löschen möchten?
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
            @click="deleteFaq"
            :loading="loading"
            >Löschen</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup>
import {
  mdiChevronDown,
  mdiChevronUp,
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
.custom-question-text {
  white-space: normal;
  line-height: 1.4;
  word-break: break-word;
}
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
