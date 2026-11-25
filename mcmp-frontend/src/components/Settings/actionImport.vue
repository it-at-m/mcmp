<template>
  <v-dialog
    v-model="dialog"
    width="600"
  >
    <template #activator="{ props: btnProps }">
      <v-btn
        v-bind="btnProps"
        :icon="mdiImport"
        aria-label="AWX Template importieren"
        :disabled="props.disable"
        @click="openDialog"
      />
    </template>
    <v-card>
      <v-card-title>AWX Template importieren</v-card-title>
      <v-card-text>
        <v-progress-linear
          v-if="loading"
          indeterminate
        />
        <v-autocomplete
          v-if="!loading"
          v-model="selectedOrganization"
          :items="organizations"
          item-title="name"
          item-value="name"
          label="AWX Organisation"
          return-object
        >
          <template #append>
            <v-btn
              :icon="mdiRefresh"
              size="small"
              :loading="loadingOrganizations"
              aria-label="Organisationen neu laden"
              variant="text"
              @click="fetchOrganizations(true)"
            />
          </template>
        </v-autocomplete>

        <v-autocomplete
          v-if="!loading && selectedOrganization"
          v-model="selectedTemplate"
          :items="templates"
          item-title="name"
          item-value="id"
          label="AWX Template"
          return-object
        />
        <div
          v-if="selectedTemplate && changedFields.length && !loading"
          class="mt-4"
        >
          <v-row>
            <v-col
              v-for="change in changedFields"
              :key="change.field"
              cols="6"
            >
              <div>
                <strong>{{ change.field }}</strong>
                <div>
                  <del v-if="change.old !== undefined && change.old !== null"
                    >{{ change.old }}
                  </del>
                </div>
                <div>
                  <span>{{ change.new }}</span>
                </div>
              </div>
            </v-col>
          </v-row>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-btn
          color="cancel"
          @click="closeDialog"
          >Abbrechen
        </v-btn>
        <v-btn
          color="do"
          :disabled="!selectedTemplate"
          @click="confirm"
          >Importieren
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type Action from "@/types/Action";

import { mdiImport, mdiRefresh } from "@mdi/js";
import { computed, ref, watch } from "vue";

import actionService from "@/api/actionService";

const props = defineProps<{
  currentAction: Action;
  allActions: Action[];
  disable?: boolean;
}>();

interface AwxTemplate {
  id?: number;
  name?: string;
  type?: string;
}

const dialog = ref(false);
const loading = ref(false);
const loadingOrganizations = ref(false);
const templates = ref<AwxTemplate[]>([]);
const organizations = ref<string[]>([]);
const organizationsCache = ref<string[]>([]);
const selectedTemplate = ref<AwxTemplate | null>(null);
const selectedTemplateDetails = ref<AwxTemplate | null>(null);
const selectedOrganization = ref<string | null>(null);

function openDialog() {
  dialog.value = true;
  selectedOrganization.value = null;
  selectedTemplate.value = null;
  selectedTemplateDetails.value = null;
  templates.value = [];
  loading.value = true;
  fetchOrganizations();
  loading.value = false;
}

function fetchOrganizations(force = false) {
  if (organizationsCache.value.length && !force) {
    organizations.value = organizationsCache.value;
    return;
  }
  const awxConfigId = props.currentAction.awxConfig?.id;
  if (awxConfigId === undefined) return;
  actionService
    .getOrganizationsFromAwx(loadingOrganizations, awxConfigId)
    .then((data: unknown) => {
      organizationsCache.value = Array.isArray(data) ? data : [];
      organizations.value = organizationsCache.value;
    });
}

watch(selectedOrganization, (org) => {
  if (org) {
    templates.value = [];
    selectedTemplate.value = null;
    selectedTemplateDetails.value = null;
    const awxConfigId = props.currentAction.awxConfig?.id;
    if (awxConfigId === undefined) return;
    actionService
      .getTemplatesFromAwx(loading, org, awxConfigId)
      .then((data: unknown) => {
        templates.value = Array.isArray(data) ? data : [];
      });
  }
});

watch(selectedTemplate, (template) => {
  if (template) {
    const awxConfigId = props.currentAction.awxConfig?.id;
    if (template.id === undefined || awxConfigId === undefined) return;
    actionService
      .getSingleJobTemplateFromAwx(loading, template.id, awxConfigId)
      .then((data: unknown) => {
        const results = (data as { results?: AwxTemplate[] } | null)?.results;
        selectedTemplateDetails.value = results?.[0] ?? null;
      });
  }
});

function closeDialog() {
  dialog.value = false;
  selectedTemplate.value = null;
  selectedOrganization.value = null;
  templates.value = [];
  selectedTemplateDetails.value = null;
}

const emit = defineEmits<(e: "imported", action: Action) => void>();

// only set Type and ID
const importFields = ["awxTemplateType", "awxTemplateId"] as const;

function mapTemplateToAction(t: AwxTemplate): Action {
  return {
    ...props.currentAction,
    awxTemplateType: t.type == "job_template" ? "template" : "workflow",
    awxTemplateId: t.id ?? 0,
  };
}

const changedFields = computed(() => {
  if (!selectedTemplateDetails.value) return [];
  const imported = mapTemplateToAction(selectedTemplateDetails.value);
  return importFields
    .filter((field) => props.currentAction[field] !== imported[field])
    .map((field) => ({
      field,
      old: props.currentAction[field],
      new: imported[field],
    }));
});

function confirm() {
  if (!selectedTemplateDetails.value) return;
  emit("imported", mapTemplateToAction(selectedTemplateDetails.value));
  closeDialog();
}
</script>

<style scoped>
del {
  color: #b71c1c;
  text-decoration: line-through;
  display: block;
}
</style>
