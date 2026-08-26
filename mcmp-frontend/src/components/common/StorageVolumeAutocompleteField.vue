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
    item-value="uuid"
    :return-object="true"
    @update:model-value="$emit('update:modelValue', $event)"
    @update:search="onSearch"
  >
    <template #no-data>
      <div class="px-4 py-2">Kein Storage-Volume gefunden!</div>
    </template>
    <template #selection="{ item }">
      <span>{{ item.raw.name }}</span>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";

import { mdiMagnify } from "@mdi/js";
import { ref } from "vue";

import storageService from "@/api/storageService";

interface Props {
  modelValue: UnifiedStorageItemList | null;
  label?: string;
  placeholder?: string;
  loading?: boolean;
}

type Emits = (
  e: "update:modelValue",
  value: UnifiedStorageItemList | null
) => void;

defineEmits<Emits>();

const props = withDefaults(defineProps<Props>(), {
  label: "Suche Storage-Volume (NFS/CIFS)",
  placeholder: "",
  loading: false,
});

const items = ref<(UnifiedStorageItemList & { displayName: string })[]>([]);
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
      storageService
        .getUnifiedStorage(loadingRef, 0, 20, "name", "asc", searchText)
        .then((res) => {
          // Job history linkage is currently only resolved for NFS/CIFS volumes.
          items.value = res.content
            .filter((item) => item.type === "NFS" || item.type === "CIFS")
            .map((item) => ({
              ...item,
              displayName: `${item.name} (${item.type})`,
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
