<template>
  <v-container>
    <h1 class="mb-6">Hilfe &amp; FAQ</h1>

    <v-tabs
      v-model="activeTab"
      class="mb-6"
      color="primary"
    >
      <v-tab
        v-if="isAdmin"
        value="faqCategories"
        >FAQ Kategorien</v-tab
      >
      <v-tab value="faq">FAQ</v-tab>
      <v-tab value="changelog">Changelog</v-tab>
      <v-tab value="version">Version</v-tab>
    </v-tabs>

    <v-window v-model="activeTab">
      <!-- FAQ Categories Tab (Admin only) -->
      <v-window-item
        v-if="isAdmin"
        value="faqCategories"
      >
        <help-faq-categories v-if="activeTab === 'faqCategories'" />
      </v-window-item>

      <!-- FAQ Tab -->
      <v-window-item value="faq">
        <help-faq
          v-if="activeTab === 'faq'"
          :is-admin="isAdmin"
        />
      </v-window-item>

      <!-- Changelog Tab -->
      <v-window-item value="changelog">
        <help-changelog
          v-if="activeTab === 'changelog'"
          :is-admin="isAdmin"
        />
      </v-window-item>

      <!-- Version Tab -->
      <v-window-item value="version">
        <app-version
          v-if="activeTab === 'version'"
          :is-admin="isAdmin"
        />
      </v-window-item>
    </v-window>
  </v-container>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";

import AppVersion from "@/components/help/AppVersion.vue";
import HelpChangelog from "@/components/help/HelpChangelog.vue";
import HelpFaq from "@/components/help/HelpFaq.vue";
import HelpFaqCategories from "@/components/help/HelpFaqCategories.vue";
import { useTabQuerySync } from "@/composables/useTabQuerySync";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();

const activeTab = ref("faq");
const isAdmin = computed(
  () => userStore.getUser?.authorities?.includes("ROLE_ADMIN") || false
);

useTabQuerySync(activeTab);
</script>
