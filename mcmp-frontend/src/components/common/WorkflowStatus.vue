<template>
  <div class="workflow-container">
    <template
      v-for="(step, index) in activeSteps"
      :key="step.label"
    >
      <div
        class="workflow-step"
        :class="{ 'hide-labels': hideLabels }"
      >
        <div class="step-icon-row">
          <div class="step-icon-container">
            <v-tooltip location="top">
              <template #activator="{ props: tooltipProps }">
                <div v-bind="tooltipProps">
                  <job-status-icon
                    v-if="step.status"
                    :status="step.status"
                    hide-tooltip
                  />
                  <span v-else>–</span>
                </div>
              </template>
              <span>{{ step.label }}: {{ step.status ?? "unbekannt" }}</span>
            </v-tooltip>
          </div>
          <div
            v-if="index < activeSteps.length - 1"
            class="step-connector"
            :class="{ 'main-connector': step.isMain }"
          ></div>
        </div>
        <div class="step-label-container">
          <span
            v-if="!hideLabels"
            class="step-label"
            :class="{ 'main-status': step.isMain }"
          >
            {{ step.label }}
          </span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type JobList from "@/types/JobList";
import type { TaggingAction } from "@/types/TaggingAction";

import { computed } from "vue";

import JobStatusIcon from "@/components/common/JobStatusIcon.vue";
import { taggingActionOptions } from "@/types/TaggingAction";

const props = defineProps<{
  job: JobList;
  hideLabels?: boolean;
}>();

interface WorkflowStep {
  label: string;
  status: string | null;
  isMain?: boolean;
}

/**
 * Leere Strings / Whitespace aus dem Backend werden als "kein Status" behandelt.
 */
function normalizeStatus(value?: string | null): string | null {
  if (value == null) return null;
  const trimmed = value.trim();
  return trimmed === "" || trimmed === "null" ? null : trimmed;
}

/**
 * Prüft strikt gegen die bekannten Tagging-Action-Keys.
 * Fängt damit auch "", "null" und unbekannte Werte ab.
 */
function isTaggingActionSet(value?: string | null): boolean {
  return taggingActionOptions.some((option) => option.key === value);
}

const activeSteps = computed<WorkflowStep[]>(() => {
  const steps: WorkflowStep[] = [];

  steps.push({
    label: "Gesamt",
    status: normalizeStatus(props.job.status),
    isMain: true,
  });

  if (props.job.changeRequired) {
    steps.push({ label: "Change", status: props.job.changeStatus });
  }
  if (props.job.awxJobEnabled) {
    steps.push({ label: "AWX", status: props.job.awxStatus });
  }

  // Discovery & Tagging nur, wenn die Action es wirklich aktiviert.
  if (isTaggingActionSet(props.job.taggingAction)) {
    steps.push({
      label: "Discovery",
      status: normalizeStatus(props.job.quickdiscoveryStatus),
    });
    steps.push({
      label: "Tagging",
      status: normalizeStatus(props.job.taggingStatus),
    });
  }

  return steps;
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.workflow-container {
  display: flex;
  align-items: flex-start;
}

.workflow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  width: 65px;
}

.workflow-step.hide-labels {
  width: 30px;
}

.workflow-step.hide-labels:first-child {
  width: 31px; /* 30px + 1px border */
  padding-right: 0;
  border-right: 1px solid rgba(var(--v-theme-on-surface), 0.2);
}

.step-icon-row {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  position: relative;
}

.step-icon-container {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 24px;
  height: 24px;
  z-index: 1;
  background-color: rgb(var(--v-theme-surface));
  border-radius: 50%;
}

.step-connector {
  position: absolute;
  left: 50%;
  width: 100%;
  height: 2px;
  background-color: rgba(var(--v-theme-on-surface), 0.12);
  z-index: 0;
}

.step-label-container {
  width: 100%;
  display: flex;
  justify-content: center;
  margin-top: 4px;
}

.step-label {
  font-size: 11px;
  white-space: nowrap;
  color: rgba(var(--v-theme-on-surface), 0.7);
  text-align: center;
}

.main-status {
  font-weight: bold;
  color: rgb(var(--v-theme-on-surface));
}

.workflow-step:first-child:not(.hide-labels) {
  width: auto;
  padding-right: 12px;
  border-right: 1px solid rgba(var(--v-theme-on-surface), 0.2);
  margin-right: 0;
}

.workflow-step:last-child .step-connector {
  display: none;
}

.main-connector {
  display: none;
}
</style>
