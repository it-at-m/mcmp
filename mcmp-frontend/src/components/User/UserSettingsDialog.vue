<template>
  <common-dialog
    v-model="internalValue"
    :title="'Persönliche Einstellungen'"
    :icon="mdiCog"
    :max-width="600"
    :show-actions="true"
    :submit-activated="true"
    @dialog-cancel="internalValue = false"
  >
    <div class="text-subtitle-1 font-weight-bold mb-0">Darstellung</div>
    <v-btn-toggle
      v-model="themeName"
      mandatory
      color="primary"
      variant="outlined"
      class="mb-6"
    >
      <v-btn
        value="light"
        :prepend-icon="mdiWhiteBalanceSunny"
        >Hell</v-btn
      >
      <v-btn
        value="dark"
        :prepend-icon="mdiWeatherNight"
        >Dunkel</v-btn
      >
    </v-btn-toggle>

    <v-divider class="mb-6" />

    <div class="text-subtitle-1 font-weight-bold mb-2">Ihre Rollen</div>
    <div
      v-if="
        userStore.getUser?.authorities &&
        userStore.getUser.authorities.length > 0
      "
      class="d-flex flex-wrap ga-2"
    >
      <v-chip
        v-for="role in userStore.getUser.authorities"
        :key="role"
        size="small"
        color="primary"
        variant="tonal"
        class="font-weight-medium"
      >
        {{ role.replace("ROLE_", "") }}
      </v-chip>
    </div>
    <div
      v-else
      class="text-caption text-disabled font-italic"
    >
      Keine Rollen zugewiesen
    </div>
    <template #actions>
      <v-spacer />
      <v-btn
        color="primary"
        variant="flat"
        size="large"
        rounded="xl"
        class="px-8"
        @click="internalValue = false"
      >
        Schließen
      </v-btn>
    </template>
  </common-dialog>
</template>

<script setup lang="ts">
import { mdiCog, mdiWeatherNight, mdiWhiteBalanceSunny } from "@mdi/js";
import { computed, ref, watch } from "vue";
import { useTheme } from "vuetify";

import userService from "@/api/userService";
import CommonDialog from "@/components/common/CommonDialog.vue";
import { useUserStore } from "@/stores/user";

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits(["update:modelValue"]);

const theme = useTheme();
const userStore = useUserStore();
const themeLoading = ref(false);
const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const themeName = ref(theme.global.name.value);

watch(themeName, (newTheme) => {
  theme.global.name.value = newTheme;
  localStorage.setItem("theme", newTheme);

  userService.setDarkMode(newTheme === "dark", themeLoading).catch((e) => {
    console.error("Error saving dark mode to DB", e);
  });
});
</script>
