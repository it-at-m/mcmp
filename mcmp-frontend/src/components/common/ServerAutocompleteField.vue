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
      <div class="px-4 py-2">Kein Server gefunden!</div>
    </template>
    <template #selection="{ item }">
      <span>{{ item.raw.name }}</span>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import type { ServerAutocomplete } from "@/types/ServerAutocomplete";

import { mdiMagnify } from "@mdi/js";
import { ref } from "vue";

import serverService from "@/api/serverService";

interface Props {
  modelValue: ServerAutocomplete | null;
  label?: string;
  placeholder?: string;
  loading?: boolean; // Global loading state from the parent component
}

interface Emits {
  (e: "update:modelValue", value: ServerAutocomplete | null): void;
}

defineEmits<Emits>();

const props = withDefaults(defineProps<Props>(), {
  label: "Suche Server",
  placeholder: "",
  loading: false,
});

const items = ref<ServerAutocomplete[]>([]);
const isLoading = ref(false);
const searchInput = ref("");
let searchTimeout: ReturnType<typeof setTimeout> | null = null;

const onSearch = (searchText: string) => {
  searchInput.value = searchText;
  // Debounce for 300ms
  if (searchTimeout) clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    if (searchText && searchText.length >= 3) {
      isLoading.value = true;
      serverService
        .getServersForAutocomplete(searchText)
        .then((servers) => {
          items.value = servers.map((server) => ({
            ...server,
            displayName: `${server.name}`,
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
