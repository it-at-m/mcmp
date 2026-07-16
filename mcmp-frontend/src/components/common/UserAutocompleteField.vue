<template>
  <v-autocomplete
    :model-value="modelValue"
    :items="items"
    :label="label"
    :placeholder="placeholder"
    :prepend-inner-icon="mdiMagnify"
    :loading="isLoading"
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
      <div class="px-4 py-2">Kein Benutzer gefunden!</div>
    </template>
    <template #selection="{ item }">
      <span>{{ item.raw.username }}</span>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import type { UserAutocomplete } from "@/types/UserAutocomplete";

import { mdiMagnify } from "@mdi/js";
import { ref } from "vue";

import userService from "@/api/userService";

interface Props {
  modelValue: UserAutocomplete | null; // The selected user object
  label?: string;
  placeholder?: string;
  loading?: boolean; // Passed from parent but not used
}

type Emits = (e: "update:modelValue", value: UserAutocomplete | null) => void;

defineEmits<Emits>();

const props = withDefaults(defineProps<Props>(), {
  label: "Suche Username",
  placeholder: "",
  loading: false,
});

const items = ref<UserAutocomplete[]>([]);
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
      userService
        .getUsersForAutocomplete(searchText)
        .then((users) => {
          // Prepare for display: Combine username and name
          items.value = users.map((user) => ({
            ...user,
            displayName: `${user.username} (${user.name})`, // Display: user1 (Max Mustermann)
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
