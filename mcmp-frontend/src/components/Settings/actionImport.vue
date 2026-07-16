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

const dialog = ref(false);
const loading = ref(false);
const loadingOrganizations = ref(false);
const templates = ref<any[]>([]);
const organizations = ref<any[]>([]);
const organizationsCache = ref<any[]>([]);
const selectedTemplate = ref<any>(null);
const selectedTemplateDetails = ref<any>(null);
const selectedOrganization = ref<any>(null);

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
  actionService
    .getOrganizationsFromAwx(
      loadingOrganizations,
      props.currentAction.awxConfig.id
    )
    .then((data: any) => {
      organizationsCache.value = Array.isArray(data) ? data : [];
      organizations.value = organizationsCache.value;
    });
}

watch(selectedOrganization, (org) => {
  if (org) {
    templates.value = [];
    selectedTemplate.value = null;
    selectedTemplateDetails.value = null;
    actionService
      .getTemplatesFromAwx(loading, org, props.currentAction.awxConfig.id)
      .then((data: any) => {
        templates.value = Array.isArray(data) ? data : [];
      });
  }
});

watch(selectedTemplate, (template) => {
  if (template) {
    actionService
      .getSingleJobTemplateFromAwx(
        loading,
        template.id,
        props.currentAction.awxConfig.id
      )
      .then((data: any) => {
        selectedTemplateDetails.value = data?.results[0] ?? null;
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

function mapTemplateToAction(t: any): Action {
  const identifier = generateIdentifier(t.name ?? "");
  return {
    ...props.currentAction,
    identifier: identifier,
    snowConfig: props.currentAction.snowConfig,
    awxConfig: props.currentAction.awxConfig,
    title: t.name ?? "",
    description: t.description ?? "",
    comment: "",
    enabled: true,
    quickdiscovery: false,
    serverInstallation: false,
    changeRequired: false,
    changeType: "",
    changeTemplate: "",
    changeTitle: "",
    changeDescription: "",
    errorTitle: "",
    errorDescription: "",
    awxJobEnabled: true,
    awxTemplateType: t.type == "job_template" ? "template" : "workflow",
    awxTemplateId: t.id ?? 0,
    awxInventoryId: t.inventory ?? 0,
    awxCredentials: t.summary_fields?.credentials
      ? t.summary_fields.credentials.map((c: any) => c.id).join(",")
      : "",
    awxJobType: t.job_type ?? "",
    awxLimit: t.limit ?? "",
    awxJobTags: t.job_tags ?? "",
    awxSkipTags: t.skip_tags ?? "",
    awxExtraVars: t.extra_vars ?? "",
    awxScmBranch: t.scm_branch ?? "",
    awxVerbosity: t.verbosity ?? 0,
    awxTimeout: t.timeout ?? 0,
    awxForks: t.forks ?? 0,
    awxJobSliceCount: t.job_slice_count ?? 0,
    awxExecutionEnvironment: t.execution_environment ?? 0,
    awxInstanceGroups: t.instance_groups ?? "",
    awxLabels: t.labels ?? "",
    awxEstimatedRuntime: 0,
    awxTitle: t.name ?? "",
    awxDescription: t.description ?? "",
    awxErrorTitle: "",
    awxErrorDescription: "",
  };
}

const importFields = [
  "awxTemplateType",
  "awxTemplateId",
  "awxInventoryId",
  "awxJobTags",
  "awxSkipTags",
  "awxExtraVars",
  "awxScmBranch",
  "awxCredentials",
  "awxJobType",
  "awxVerbosity",
  "awxTimeout",
  "awxForks",
  "awxJobSliceCount",
  "awxExecutionEnvironment",
  "awxInstanceGroups",
  "awxLabels",
  "awxEstimatedRuntime",
  "awxTitle",
  "awxDescription",
  "awxErrorTitle",
  "awxErrorDescription",
];

function generateIdentifier(title: string): string {
  const base = title
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_");
  let identifier = base;
  let exists = props.allActions.some((a) => a.identifier === identifier);
  while (exists) {
    identifier = base + "_" + Math.floor(Math.random() * 10000);
    exists = props.allActions.some((a) => a.identifier === identifier);
  }
  return identifier;
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
