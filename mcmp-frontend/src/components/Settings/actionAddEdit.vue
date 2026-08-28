<template>
  <common-dialog
    v-model="dialog"
    :submit-activated="true"
    :title="title"
    :icon="icon"
    show-actions
    @dialog-confirm="save"
    @dialog-cancel="reset"
  >
    <template #activator="{ props: btnProps }">
      <v-btn
        v-tooltip="props.title"
        v-bind="btnProps"
        :icon="icon"
        :aria-label="ariaLabel"
      >
      </v-btn>
    </template>
    <v-form ref="form">
      <v-row v-if="props.copy">
        <v-col cols="12">
          <v-select
            v-model="selectedAction"
            :items="allActions"
            item-title="identifier"
            :item-value="(item) => item"
            label="Vorlage auswählen"
            return-object
          />
        </v-col>
      </v-row>
      <v-row v-if="props.importFile && !fileImported">
        <v-col cols="12">
          <v-file-input
            v-model="importedFile"
            label="Action JSON-Datei auswählen"
            accept="application/json,.json"
            prepend-icon=""
            :prepend-inner-icon="mdiFileUploadOutline"
            @update:model-value="handleFileImport"
          />
        </v-col>
      </v-row>
      <div
        v-if="
          (!props.copy || selectedAction) && (!props.importFile || fileImported)
        "
      >
        <v-row>
          <v-col cols="12">
            <v-toolbar color="backgroundLight">
              <v-toolbar-title>Allgemeines</v-toolbar-title>
            </v-toolbar>
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="3">
            <v-switch
              v-model="actionTmp.enabled"
              color="_blue"
              :label="
                actionTmp.enabled ? 'Aktion aktiviert' : 'Aktion deaktiviert'
              "
            />
          </v-col>
          <v-col cols="3">
            <v-switch
              v-model="actionTmp.isLowPriority"
              color="_blue"
              label="Niedrige Priorität"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.identifier"
              label="Identifier"
              :disabled="!!props.action && !props.copy"
              placeholder="Identifier der Aktion. Kann nicht mehr geändert werden und wird vom Entwicklungsteam vergeben!"
              maxlength="50"
              :rules="[
                rules.notEmptyRule('Identifier ist ein Pflichtfeld.'),
                rules.maxLengthRule(50, 'Maximal 50 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="12">
            <v-textarea
              v-model="actionTmp.comment"
              variant="outlined"
              placeholder="Team / Verantwortliche Person / Kommentar"
              :rules="[
                rules.notEmptyRule(
                  'Bitte geben Sie ein Team oder eine verantwortliche Person an.'
                ),
                rules.maxLengthRule(4096, 'Maximal 4096 Zeichen sind erlaubt.'),
              ]"
              rows="1"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.title"
              label="Titel"
              :rules="[
                rules.notEmptyRule('Darf nicht leer sein.'),
                rules.maxLengthRule(512, 'Maximal 512 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col cols="6">
            <v-textarea
              v-model="actionTmp.description"
              variant="outlined"
              label="Beschreibung"
              placeholder="z.B. Für den Server ${server.fqdn} soll XY durchgeführt werden."
              :rules="[
                rules.maxLengthRule(8192, 'Maximal 8192 Zeichen sind erlaubt.'),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.executionTitle"
              label="Titel (Job Läuft)"
              :rules="[
                rules.notEmptyRule('Darf nicht leer sein.'),
                rules.maxLengthRule(512, 'Maximal 512 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col cols="6">
            <v-textarea
              v-model="actionTmp.executionDescription"
              variant="outlined"
              label="Beschreibung (Job Läuft)"
              placeholder="z.B. Für den Server ${server.fqdn} wird XY durchgeführt."
              :rules="[
                rules.maxLengthRule(8192, 'Maximal 8192 Zeichen sind erlaubt.'),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.successTitle"
              label="Titel (Job Erfolgreich)"
              :rules="[
                rules.notEmptyRule('Darf nicht leer sein.'),
                rules.maxLengthRule(512, 'Maximal 512 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col cols="6">
            <v-textarea
              v-model="actionTmp.successDescription"
              variant="outlined"
              label="Beschreibung (Job Erfolgreich)"
              placeholder="z.B. Für den Server ${server.fqdn} konnte XY erfolgreich durchgeführt werden."
              :rules="[
                rules.maxLengthRule(8192, 'Maximal 8192 Zeichen sind erlaubt.'),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.errorTitle"
              label="Titel (Fehlerfall)"
              :rules="[
                rules.notEmptyRule('Darf nicht leer sein.'),
                rules.maxLengthRule(512, 'Maximal 512 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col cols="6">
            <v-textarea
              v-model="actionTmp.errorDescription"
              variant="outlined"
              label="Beschreibung (Fehlerfall)"
              placeholder="z.B. Für den Server ${server.fqdn} konnte XY nicht erfolgreich durchgeführt werden!"
              :rules="[
                rules.maxLengthRule(8192, 'Maximal 8192 Zeichen sind erlaubt.'),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col cols="12">
            <v-toolbar color="backgroundLight">
              <v-toolbar-title>
                <v-switch
                  v-model="actionTmp.awxJobEnabled"
                  color="_blue"
                  label="AWX Job ausführen"
                  class="mt-5"
                >
                  <template #label>
                    <span class="v-toolbar-title">AWX Job</span>
                  </template>
                </v-switch>
              </v-toolbar-title>
              <action-import
                :current-action="actionTmp"
                :disable="
                  actionTmp.awxConfig == null || !actionTmp.awxJobEnabled
                "
                :all-actions="allActions"
                @imported="onImport"
              />
            </v-toolbar>
          </v-col>
        </v-row>
        <v-row v-if="actionTmp.awxJobEnabled">
          <v-col cols="4">
            <v-select
              v-model="actionTmp.awxConfig"
              label="AWX Config"
              :items="awxConfigs"
              item-title="apiDescription"
              :item-value="(item) => item"
              :rules="
                actionTmp.awxJobEnabled
                  ? [rules.notEmptySelectRule('Pflichtfeld')]
                  : []
              "
              clearable
              :menu-props="{ persistent: true, closeOnContentClick: true }"
            />
          </v-col>
          <v-col cols="4">
            <v-select
              v-model="actionTmp.awxTemplateType"
              label="AWX Template Type"
              :items="templateTypes"
              item-title="text"
              item-value="key"
              :rules="[rules.notEmptyRule('Pflichtfeld')]"
              :menu-props="{ persistent: true, closeOnContentClick: true }"
            />
          </v-col>
          <v-col cols="4">
            <v-text-field
              v-model="actionTmp.awxTemplateId"
              type="number"
              :label="`AWX ${templateTypes.find((t) => t.key == actionTmp.awxTemplateType)?.text ?? 'Template'} ID`"
            />
          </v-col>
        </v-row>
        <v-row v-if="actionTmp.awxJobEnabled">
          <v-col cols="12">
            <div class="text-left">
              Folgende Parameter greifen bei AWX Job Launch nur, wenn im AWX bei
              den jew. Feldern "Prompt on launch" angehakt ist.
            </div>
          </v-col>
        </v-row>
        <v-row v-if="actionTmp.awxJobEnabled">
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxJobTags"
              label="AWX Job Tags"
              placeholder="tag1,tag2,..."
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxSkipTags"
              label="AWX Skip Tags"
              placeholder="tag1,tag2,..."
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
        </v-row>
        <v-textarea
          v-if="actionTmp.awxJobEnabled"
          v-model="actionTmp.awxExtraVars"
          label="AWX Extra Vars"
          placeholder='{"key":"value"}'
          :rules="[
            rules.maxLengthRule(250, 'Maximal 250 Zeichen sind erlaubt.'),
          ]"
          rows="2"
          auto-grow
        >
          <template #append-inner>
            <v-tooltip
              location="left"
              text="Extra Vars die fest definiert sind."
            >
              <template #activator="{ props }">
                <v-icon
                  v-bind="props"
                  :icon="mdiInformation"
                  size="large"
                  aria-label="Extra Vars Infos Link öffnen"
                  @click="openLink"
                />
              </template>
            </v-tooltip>
          </template>
        </v-textarea>
        <v-switch
          v-if="actionTmp.awxJobEnabled"
          v-model="awxAdvancedOptions"
          color="_blue"
          label="Weitere Parameter"
          class="mt-5"
        ></v-switch>
        <v-row v-if="actionTmp.awxJobEnabled && awxAdvancedOptions">
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxScmBranch"
              label="AWX SCM Branch"
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxInventoryId"
              label="AWX Inventory ID"
              type="number"
            />
          </v-col>
        </v-row>
        <v-row v-if="actionTmp.awxJobEnabled && awxAdvancedOptions">
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxLimit"
              label="AWX Limit"
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col
            v-if="
              actionTmp.awxJobEnabled &&
              awxAdvancedOptions &&
              actionTmp.awxTemplateType == 'template'
            "
            cols="6"
          >
            <v-text-field
              v-model="actionTmp.awxCredentials"
              label="AWX Credentials"
              placeholder="[1,2,...]"
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
        </v-row>
        <v-row
          v-if="
            actionTmp.awxJobEnabled &&
            awxAdvancedOptions &&
            actionTmp.awxTemplateType == 'template'
          "
        >
          <v-col cols="6">
            <v-select
              v-model="actionTmp.awxJobType"
              label="AWX Job Type"
              :items="['run', 'check']"
              :menu-props="{ persistent: true, closeOnContentClick: true }"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxVerbosity"
              label="AWX Verbosity"
              type="number"
            />
          </v-col>
        </v-row>
        <v-row
          v-if="
            actionTmp.awxJobEnabled &&
            awxAdvancedOptions &&
            actionTmp.awxTemplateType == 'template'
          "
        >
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxTimeout"
              label="AWX Timeout"
              type="number"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxForks"
              label="AWX Forks"
              type="number"
            />
          </v-col>
        </v-row>
        <v-row
          v-if="
            actionTmp.awxJobEnabled &&
            awxAdvancedOptions &&
            actionTmp.awxTemplateType == 'template'
          "
        >
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxJobSliceCount"
              label="AWX Job Slice Count"
              type="number"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxExecutionEnvironment"
              label="AWX Execution Environment"
              type="number"
            />
          </v-col>
        </v-row>
        <v-row
          v-if="
            actionTmp.awxJobEnabled &&
            awxAdvancedOptions &&
            actionTmp.awxTemplateType == 'template'
          "
        >
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxInstanceGroups"
              label="AWX Instance Groups"
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
              placeholder="[1,2,...]"
            />
          </v-col>
          <v-col cols="6">
            <v-text-field
              v-model="actionTmp.awxLabels"
              label="AWX Labels"
              :rules="[
                rules.maxLengthRule(100, 'Maximal 100 Zeichen sind erlaubt.'),
              ]"
              placeholder="[1,2,...]"
            />
          </v-col>
        </v-row>
        <v-row v-if="actionTmp.awxJobEnabled">
          <v-col cols="12">
            <v-text-field
              v-model="actionTmp.awxEstimatedRuntime"
              label="Geschätzte AWX-Laufzeit in Minuten"
              type="number"
              min="0"
              :rules="[(v) => v >= 0 || 'Wert muss größer oder gleich 0 sein.']"
            >
              <template #append-inner>
                <v-tooltip
                  location="left"
                  text="Minimal geschätzte Laufzeit des AWX-Jobs in Minuten. Die erste Statusabfrage wird erst nach Ablauf der geschätzten Laufzeit ausgeführt."
                >
                  <template #activator="{ props }">
                    <v-icon
                      v-bind="props"
                      :icon="mdiInformation"
                      size="large"
                    />
                  </template>
                </v-tooltip>
              </template>
            </v-text-field>
          </v-col>
        </v-row>
        <v-divider class="my-4" />

        <!-- ServiceNow -->
        <v-row>
          <v-col cols="12">
            <v-toolbar color="backgroundLight">
              <v-toolbar-title>
                <v-switch
                  color="_blue"
                  :model-value="!!actionTmp.snowConfig"
                  class="mt-5"
                  @update:model-value="onSnowEnabledChange"
                >
                  <template #label>
                    <span class="v-toolbar-title">ServiceNow</span>
                  </template>
                </v-switch>
              </v-toolbar-title>
            </v-toolbar>
          </v-col>
        </v-row>
        <v-row v-if="actionTmp.snowConfig">
          <v-col cols="6">
            <v-select
              v-model="actionTmp.snowConfig"
              label="ServiceNow Config"
              :items="snowConfigs"
              item-title="apiDescription"
              :item-value="(item) => item"
              :rules="
                actionTmp.changeRequired
                  ? [rules.notEmptySelectRule('Pflichtfeld')]
                  : []
              "
              clearable
              :menu-props="{ persistent: true, closeOnContentClick: true }"
            />
          </v-col>
          <v-col cols="6">
            <v-switch
              v-if="testing"
              v-model="actionTmp.createIncidents"
              color="_red"
              label="Incidents bei Fehlern erstellen"
              class="mt-5 ml-4"
            />
          </v-col>
        </v-row>

        <!-- ServiceNow Change-Ticket -->
        <v-row
          v-if="actionTmp.snowConfig"
          class="ml-6"
        >
          <v-col cols="12">
            <v-toolbar color="backgroundLight">
              <v-toolbar-title>
                <v-switch
                  v-model="actionTmp.changeRequired"
                  color="_blue"
                  class="mt-5"
                >
                  <template #label>
                    <span class="v-toolbar-title"
                      >Change-Ticket in ServiceNow erforderlich</span
                    >
                  </template>
                </v-switch>
              </v-toolbar-title>
            </v-toolbar>
          </v-col>
        </v-row>
        <v-row
          v-if="actionTmp.changeRequired"
          class="ml-6"
        >
          <v-col cols="6">
            <v-select
              v-model="actionTmp.changeType"
              label="Change Typ"
              :items="[
                { title: 'Normaler Change', value: 'normal' },
                { title: 'Standard Change', value: 'standard' },
              ]"
              :rules="[
                rules.notEmptyRule('Bitte wählen Sie einen Change-Typ aus!'),
              ]"
              :menu-props="{ persistent: true, closeOnContentClick: true }"
            />
          </v-col>
          <v-col
            v-if="actionTmp.changeType === 'normal'"
            key="change-action-field"
            cols="6"
          >
            <v-combobox
              v-model="actionTmp.changeAction"
              label="Change Action"
              :items="['other', 'decommissioning']"
              maxlength="64"
              :rules="[
                (v) => !!v || 'Change Action ist ein Pflichtfeld.',
                rules.maxLengthRule(64, 'Maximal 64 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
          <v-col
            v-if="actionTmp.changeType === 'standard'"
            key="change-template-field"
            cols="6"
          >
            <v-text-field
              v-model="actionTmp.changeTemplate"
              label="Change Template (ServiceNow SysID)"
              maxlength="64"
              :rules="[
                (v) => !!v || 'Change Template ist ein Pflichtfeld.',
                rules.maxLengthRule(64, 'Maximal 64 Zeichen sind erlaubt.'),
              ]"
            />
          </v-col>
        </v-row>
        <v-row
          v-if="actionTmp.changeRequired && actionTmp.changeType === 'normal'"
          class="ml-6 mt-n2"
        >
          <v-col
            cols="12"
            class="py-1"
          >
            <v-textarea
              v-model="actionTmp.changeJustification"
              variant="outlined"
              label="Begründung"
              required
              maxlength="16384"
              :rules="[
                (v) =>
                  actionTmp.changeRequired && actionTmp.changeType === 'normal'
                    ? !!v || 'Begründung ist ein Pflichtfeld.'
                    : true,
                rules.maxLengthRule(
                  16384,
                  'Maximal 16384 Zeichen sind erlaubt.'
                ),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row
          v-if="actionTmp.changeRequired && actionTmp.changeType === 'normal'"
          class="ml-6 mt-n2"
        >
          <v-col
            cols="12"
            class="py-1"
          >
            <v-textarea
              v-model="actionTmp.changeImplementationPlan"
              variant="outlined"
              label="Rolloutplan"
              required
              maxlength="16384"
              :rules="[
                (v) =>
                  actionTmp.changeRequired && actionTmp.changeType === 'normal'
                    ? !!v || 'Rolloutplan ist ein Pflichtfeld.'
                    : true,
                rules.maxLengthRule(
                  16384,
                  'Maximal 16384 Zeichen sind erlaubt.'
                ),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row
          v-if="actionTmp.changeRequired && actionTmp.changeType === 'normal'"
          class="ml-6 mt-n2"
        >
          <v-col
            cols="12"
            class="py-1"
          >
            <v-textarea
              v-model="actionTmp.changeRiskImpactAnalysis"
              variant="outlined"
              label="Risiko- und Auswirkungsanalyse"
              required
              maxlength="16384"
              :rules="[
                (v) =>
                  actionTmp.changeRequired && actionTmp.changeType === 'normal'
                    ? !!v ||
                      'Risiko- und Auswirkungsanalyse ist ein Pflichtfeld.'
                    : true,
                rules.maxLengthRule(
                  16384,
                  'Maximal 16384 Zeichen sind erlaubt.'
                ),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>
        <v-row
          v-if="actionTmp.changeRequired && actionTmp.changeType === 'normal'"
          class="ml-6 mt-n2"
        >
          <v-col
            cols="12"
            class="py-1"
          >
            <v-textarea
              v-model="actionTmp.changeBackoutPlan"
              variant="outlined"
              label="Rollbackplan"
              required
              maxlength="16384"
              :rules="[
                (v) =>
                  actionTmp.changeRequired && actionTmp.changeType === 'normal'
                    ? !!v || 'Rollbackplan ist ein Pflichtfeld.'
                    : true,
                rules.maxLengthRule(
                  16384,
                  'Maximal 16384 Zeichen sind erlaubt.'
                ),
              ]"
              rows="2"
              auto-grow
            />
          </v-col>
        </v-row>

        <v-row
          v-if="actionTmp.snowConfig"
          class="ml-6"
        >
          <v-col cols="12">
            <v-toolbar color="backgroundLight">
              <v-toolbar-title>
                <v-switch
                  v-model="actionTmp.quickdiscovery"
                  color="_blue"
                  class="mt-5"
                >
                  <template #label>
                    <span class="v-toolbar-title"
                      >Quick Discovery in ServiceNow durchführen</span
                    >
                  </template>
                </v-switch>
              </v-toolbar-title>
            </v-toolbar>
          </v-col>
        </v-row>
        <v-row
          v-if="actionTmp.snowConfig"
          class="ml-6"
        >
          <v-col cols="12">
            <v-toolbar color="backgroundLight">
              <v-toolbar-title>
                <v-switch
                  v-model="actionTmp.serverInstallation"
                  color="_blue"
                  class="mt-5"
                >
                  <template #label>
                    <span class="v-toolbar-title"
                      >Server Installation (ServiceNow: Quick Discovery +
                      Tagging)</span
                    >
                  </template>
                </v-switch>
              </v-toolbar-title>
            </v-toolbar>
          </v-col>
        </v-row>
      </div>
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import type Action from "@/types/Action";
import type { AwxConfig } from "@/types/AwxConfig";
import type { SnowConfig } from "@/types/SnowConfig";

import { mdiFileUploadOutline, mdiInformation } from "@mdi/js";
import { onMounted, ref, toRaw, watch } from "vue";

import testenvService from "@/api/testenvService.ts";
import CommonDialog from "@/components/common/CommonDialog.vue";
import ActionImport from "@/components/Settings/actionImport.vue";
import { useRules } from "@/composables/rules";
import { STATUS_INDICATORS } from "@/constants";
import { useSnackbarStore } from "@/stores/snackbar.ts";

const props = defineProps<{
  title: string;
  icon: string;
  action?: Action;
  copy?: boolean;
  importFile?: boolean;
  awxConfigs: AwxConfig[];
  snowConfigs: SnowConfig[];
  allActions: Action[];
}>();

const ariaLabel = ref<string>(
  props.action?.identifier
    ? `Action ${props.action?.identifier} bearbeiten`
    : "Action hinzufügen"
);

const emits = defineEmits<(e: "save", action: Action) => void>();

const dialog = ref(false);
const rules = useRules();
const form = ref<HTMLFormElement>();
const awxAdvancedOptions = ref(false);
const testing = ref(false);
const loadingTestEnv = ref(false);
const selectedAction = ref<Action | null>(null);
const importedFile = ref<File | File[] | null>(null);
const fileImported = ref(false);

const actionTmp = ref<Action>(
  props.action ? { ...props.action } : getEmptyAction()
);

const templateTypes = [
  { key: "template", text: "Job Template" },
  { key: "workflow", text: "Workflow Job Template" },
];

onMounted(() => {
  testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
    testing.value = enabled;
  });
});

watch(dialog, (val) => {
  if (val) {
    actionTmp.value = props.action ? { ...props.action } : getEmptyAction();
    if (!actionTmp.value.changeType) {
      actionTmp.value.changeType = "normal";
    }
    importedFile.value = null;
    fileImported.value = false;
  }
});

watch(
  () => actionTmp.value.changeType,
  (newType) => {
    if (newType === "standard") {
      actionTmp.value.changeJustification = null;
      actionTmp.value.changeImplementationPlan = null;
      actionTmp.value.changeRiskImpactAnalysis = null;
      actionTmp.value.changeBackoutPlan = null;
      actionTmp.value.changeAction = null;
    }
    if (newType === "normal") {
      actionTmp.value.changeTemplate = null;
    }
  }
);

watch(selectedAction, (action) => {
  if (!action) {
    actionTmp.value = getEmptyAction();
    return;
  }

  actionTmp.value = structuredClone(toRaw(action));

  // Identifier zurücksetzen
  actionTmp.value.identifier = "";
});

function getEmptyAction(): Action {
  return {
    identifier: "",
    title: "",
    awxConfig: null,
    snowConfig: null,
    description: "",
    comment: "",
    enabled: false,
    quickdiscovery: false,
    serverInstallation: false,
    changeRequired: false,
    changeType: "normal",
    changeAction: "other",
    changeTemplate: null,
    executionTitle: "",
    executionDescription: "",
    successTitle: "",
    successDescription: "",
    errorTitle: "",
    errorDescription: "",
    awxJobEnabled: false,
    awxTemplateType: "template",
    awxTemplateId: 0,
    awxInventoryId: 0,
    awxCredentials: "",
    awxJobType: "",
    awxLimit: "",
    awxJobTags: "",
    awxSkipTags: "",
    awxExtraVars: "",
    awxScmBranch: "",
    awxVerbosity: 0,
    awxTimeout: 0,
    awxForks: 0,
    awxJobSliceCount: 0,
    awxExecutionEnvironment: 0,
    awxInstanceGroups: "",
    awxLabels: "",
    awxEstimatedRuntime: 0,
    changeJustification: null,
    changeImplementationPlan: null,
    changeRiskImpactAnalysis: null,
    changeBackoutPlan: null,
    isLowPriority: false,
    createIncidents: true,
  };
}

function openLink() {
  window.open(
    "https://confluence.muenchen.de/pages/viewpage.action?pageId=1138559249",
    "_blank"
  );
}

function onImport(action: Action) {
  actionTmp.value = action;
}

function handleFileImport(files: File | File[] | null) {
  const file = Array.isArray(files) ? files[0] : files;
  if (!file) return;
  file
    .text()
    .then((text) => {
      const parsed = JSON.parse(text);
      actionTmp.value = { ...getEmptyAction(), ...parsed };
      fileImported.value = true;
    })
    .catch(() => {
      useSnackbarStore().showMessage({
        message:
          "Die ausgewählte Datei konnte nicht als Action importiert werden. Bitte prüfen Sie das JSON-Format.",
        level: STATUS_INDICATORS.ERROR,
      });
      importedFile.value = null;
    });
}

function reset() {
  form.value?.resetValidation();
  dialog.value = false;
  actionTmp.value = getEmptyAction();
  awxAdvancedOptions.value = false;
  selectedAction.value = null;
  importedFile.value = null;
  fileImported.value = false;
}

function save() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      emits("save", actionTmp.value);
      reset();
    }
  });
}

function onSnowEnabledChange(enabled: boolean | null) {
  if (!enabled) {
    actionTmp.value.snowConfig = null;
    actionTmp.value.changeRequired = false;
    actionTmp.value.quickdiscovery = false;
    actionTmp.value.serverInstallation = false;
    // Reset change details
    actionTmp.value.changeJustification = null;
    actionTmp.value.changeImplementationPlan = null;
    actionTmp.value.changeRiskImpactAnalysis = null;
    actionTmp.value.changeBackoutPlan = null;
    actionTmp.value.changeTemplate = null;
    actionTmp.value.changeAction = "other";
  } else if (props.snowConfigs.length > 0) {
    actionTmp.value.snowConfig = props.snowConfigs[0] as SnowConfig;
    actionTmp.value.createIncidents = true;
  }
}
</script>
