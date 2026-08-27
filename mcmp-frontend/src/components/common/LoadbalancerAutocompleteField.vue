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
      <div class="px-4 py-2">Kein Loadbalancer gefunden!</div>
    </template>
    <template #selection="{ item }">
      <span>{{ item.raw.name }}</span>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem";

import { mdiMagnify } from "@mdi/js";
import { ref } from "vue";

import loadbalancerService from "@/api/loadbalancerService";

interface Props {
  modelValue: LoadbalancerListItem | null;
  label?: string;
  placeholder?: string;
  loading?: boolean;
}

type Emits = (
  e: "update:modelValue",
  value: LoadbalancerListItem | null
) => void;

defineEmits<Emits>();

const props = withDefaults(defineProps<Props>(), {
  label: "Suche Loadbalancer",
  placeholder: "",
  loading: false,
});

const items = ref<(LoadbalancerListItem & { displayName: string })[]>([]);
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
      loadbalancerService
        .getVisibleLoadbalancers(loadingRef, 0, 20, "name", "asc", searchText)
        .then((res) => {
          items.value = res.content.map((lb) => ({
            ...lb,
            displayName: lb.name,
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
