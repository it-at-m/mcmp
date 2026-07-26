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
    <!-- Darstellung (Theme) -->
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
      >
        Hell
      </v-btn>
      <v-btn
        value="dark"
        :prepend-icon="mdiWeatherNight"
      >
        Dunkel
      </v-btn>
    </v-btn-toggle>

    <v-divider class="mb-6" />

    <!-- Startseite Konfiguration -->
    <div class="text-subtitle-1 font-weight-bold mb-2">Startseite</div>
    <div class="text-caption text-disabled mb-2">
      Legen Sie fest, welche Seite beim Aufruf der Anwendung als Erstes
      angezeigt werden soll.
    </div>

    <v-menu
      location="bottom"
      :close-on-content-click="true"
    >
      <template v-slot:activator="{ props }">
        <v-text-field
          v-bind="props"
          :model-value="selectedPageTitle"
          variant="outlined"
          density="comfortable"
          readonly
          append-inner-icon="mdi-menu-down"
          class="mb-6 cursor-pointer"
          :loading="pageLoading"
        />
      </template>
      <v-list>
        <v-list-item
          v-for="page in availableStartPages"
          :key="page.path"
          @click="selectLoginPage(page.path)"
        >
          <v-list-item-title>{{ page.title }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-menu>

    <v-divider class="mb-6" />

    <!-- Rollen -->
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
const pageLoading = ref(false);

const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

// --- Theme Logik ---
const themeName = ref(theme.global.name.value);

watch(themeName, (newTheme) => {
  theme.global.name.value = newTheme;
  localStorage.setItem("theme", newTheme);

  userService.setDarkMode(newTheme === "dark", themeLoading).catch((e) => {
    console.error("Error saving dark mode to DB", e);
  });
});

// --- Startseiten Logik ---
const availableStartPages = [
  { title: "Appservice", path: "/appservice" },
  { title: "Server", path: "/server" },
  { title: "Loadbalancer", path: "/loadbalancer" },
  { title: "Storage", path: "/storage" },
  { title: "Openshift", path: "/openshift" },
];

// Initialen Wert aus dem User-Store lesen
const selectedLoginPage = ref(userStore.getUser?.login_page || "/appservice");

// Aktualisiert die Anzeige, falls sich der User-Store extern ändert
watch(
  () => userStore.getUser?.login_page,
  (newPage) => {
    if (newPage) {
      selectedLoginPage.value = newPage;
    }
  }
);

// Titel für das Eingabefeld ermitteln
const selectedPageTitle = computed(() => {
  const match = availableStartPages.find(
    (p) => p.path === selectedLoginPage.value
  );
  return match ? match.title : selectedLoginPage.value;
});

// Explizite Funktion bei Klick auf ein Item
const selectLoginPage = (newPath: string) => {
  if (selectedLoginPage.value === newPath) return;

  selectedLoginPage.value = newPath;

  // Der userService kümmert sich über pageLoading (Ref) um den Spinner im TextField
  userService
    .setLoginPage(newPath, pageLoading)
    .then(() => {
      const currentUser = userStore.getUser;
      if (currentUser) {
        userStore.setUser({
          ...currentUser,
          login_page: newPath,
        });
      }
    })
    .catch((e) => {
      console.error("Error saving login_page to DB", e);
    });
};
</script>
