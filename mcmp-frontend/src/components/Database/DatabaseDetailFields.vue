<template>
  <v-row dense>
    <v-col
      v-for="entry in fields"
      :key="entry.label"
      v-bind="columnProps(entry)"
    >
      <div class="detail-field">
        <div class="detail-field-label">{{ entry.label }}</div>
        <div class="d-flex align-center ga-1">
          <div
            class="detail-field-value"
            :class="{ 'detail-field-value--code': entry.monospace }"
          >
            {{ entry.value }}
          </div>
          <v-tooltip
            v-if="entry.copyable"
            :text="copiedLabel === entry.label ? 'Kopiert' : 'Kopieren'"
          >
            <template #activator="{ props }">
              <v-btn
                v-bind="props"
                :icon="mdiContentCopy"
                size="x-small"
                variant="text"
                density="comfortable"
                class="flex-shrink-0"
                aria-label="Wert in die Zwischenablage kopieren"
                @click="copyValue(entry)"
              />
            </template>
          </v-tooltip>
        </div>
        <div
          v-if="entry.hint"
          class="detail-field-hint"
        >
          {{ entry.hint }}
        </div>
      </div>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
import type { DatabaseDetailField } from "@/types/Database";

import { mdiContentCopy } from "@mdi/js";
import { ref } from "vue";

defineProps<{
  fields: DatabaseDetailField[];
}>();

const copiedLabel = ref<string | null>(null);

/** Felder mit fullWidth nehmen die komplette Zeile ein, alle anderen wie bisher. */
function columnProps(field: DatabaseDetailField) {
  const full = field.fullWidth === true;
  return {
    cols: 12,
    sm: full ? 12 : 6,
    md: full ? 12 : 4,
    lg: full ? 12 : 3,
  };
}

async function copyValue(entry: DatabaseDetailField) {
  try {
    // clipboard-API gibt es nur im Secure Context – im Intranet (http) Fallback
    if (window.isSecureContext && navigator.clipboard) {
      await navigator.clipboard.writeText(entry.value);
    } else {
      fallbackCopy(entry.value);
    }
    copiedLabel.value = entry.label;
    window.setTimeout(() => {
      if (copiedLabel.value === entry.label) {
        copiedLabel.value = null;
      }
    }, 2000);
  } catch {
    copiedLabel.value = null;
  }
}

function fallbackCopy(text: string) {
  const area = document.createElement("textarea");
  area.value = text;
  area.setAttribute("readonly", "");
  area.style.position = "fixed";
  area.style.top = "-1000px";
  area.style.opacity = "0";
  document.body.appendChild(area);
  area.select();
  document.execCommand("copy");
  document.body.removeChild(area);
}
</script>

<style scoped>
.detail-field {
  padding: 4px 0;
}

.detail-field-label {
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.6);
  margin-bottom: 2px;
}

.detail-field-value {
  font-size: 0.95rem;
  word-break: break-word;
}

/* Code-Optik für Connection-Strings: eigene Zeile, eigener Rahmen */
.detail-field-value--code {
  flex: 1 1 auto;
  min-width: 0;
  padding: 6px 10px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  border-radius: 6px;
  background: rgba(var(--v-theme-on-surface), 0.04);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.85rem;
  line-height: 1.5;
  word-break: break-all;
}

.detail-field-hint {
  margin-top: 4px;
  font-size: 0.7rem;
  color: rgba(var(--v-theme-on-surface), 0.55);
}
</style>
