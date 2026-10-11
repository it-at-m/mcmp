<template>
  <v-autocomplete
    :model-value="modelValue"
    :items="items"
    :label="label"
    :loading="loading"
    :rules="
      props.required
        ? [
            (v: Repository | null) =>
              !!v || 'Es muss ein Repository ausgewählt werden.',
          ]
        : []
    "
    item-title="name"
    item-value="id"
    return-object
    no-filter
    rounded
    clearable
    variant="outlined"
    @update:model-value="emit('update:modelValue', $event)"
    @update:search="onSearch"
  >
    <template #no-data>
      <div class="px-4 py-2">Kein Repository gefunden</div>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import type Repository from "@/types/Repository";

import { onMounted, ref } from "vue";

import repositoryService from "@/api/repositoryService";

const props = withDefaults(
  defineProps<{
    modelValue: Repository | null;
    label?: string;
    required?: boolean;
    excludeLocked?: boolean;
    /** Only repositories the user can edit (exactly one appservice and member of its change group). */
    editableOnly?: boolean;
  }>(),
  {
    label: "Repository*",
    required: true,
    excludeLocked: true,
    editableOnly: false,
  }
);

const emit =
  defineEmits<(e: "update:modelValue", value: Repository | null) => void>();

const items = ref<Repository[]>([]);
const loading = ref(false);
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

async function load(search: string) {
  const res = await repositoryService.getVisibleRepositories(
    loading,
    0,
    50,
    "name",
    "asc",
    search || undefined,
    false,
    props.editableOnly
  );
  items.value = props.excludeLocked
    ? res.content.filter((r) => r.lockStatus !== "LOCKED")
    : res.content;
}

function onSearch(search: string | null) {
  // don't search again when the field just shows the selected repository
  if (search === null || search === props.modelValue?.name) return;
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => void load(search), 300);
}

onMounted(() => void load(""));
</script>
