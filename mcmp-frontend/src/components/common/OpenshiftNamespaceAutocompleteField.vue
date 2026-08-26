<template>
  <v-autocomplete
    :model-value="modelValue"
    :items="items"
    :label="label"
    :placeholder="placeholder"
    :prepend-inner-icon="mdiMagnify"
    :loading="props.loading || isLoading"
    :search="searchInput"
    outlined
    dense
    clearable
    item-title="displayName"
    item-value="id"
    :return-object="true"
    @update:model-value="$emit('update:modelValue', $event)"
    @update:search="onSearch"
  >
    <template #no-data>
      <div class="px-4 py-2">Kein Namespace gefunden!</div>
    </template>
    <template #selection="{ item }">
      <span>{{ item.raw.name }}</span>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import type { OpenshiftNamespaceListItem } from "@/types/OpenshiftNamespaceListItem";

import { mdiMagnify } from "@mdi/js";
import { ref } from "vue";

import openshiftService from "@/api/openshiftService";

interface Props {
  modelValue: OpenshiftNamespaceListItem | null;
  label?: string;
  placeholder?: string;
  loading?: boolean;
}

type Emits = (
  e: "update:modelValue",
  value: OpenshiftNamespaceListItem | null
) => void;

defineEmits<Emits>();

const props = withDefaults(defineProps<Props>(), {
  label: "Suche Namespace",
  placeholder: "",
  loading: false,
});

const items = ref<(OpenshiftNamespaceListItem & { displayName: string })[]>([]);
const isLoading = ref(false);
const loadingRef = ref(false);
const searchInput = ref("");
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const onSearch = (searchText: string) => {
  searchInput.value = searchText;
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    if (searchText && searchText.length >= 3) {
      isLoading.value = true;
      openshiftService
        .getVisibleNamespaces(loadingRef, 0, 20, "name", "asc", searchText)
        .then((res) => {
          items.value = res.content.map((ns) => ({
            ...ns,
            displayName: ns.name,
          }));
        })
        .catch(() => {
          items.value = [];
        })
        .finally(() => {
          isLoading.value = false;
        });
    } else {
      items.value = [];
    }
  }, 300);
};
</script>
