<template>
  <v-menu>
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        variant="text"
        icon
        class="avatar-btn"
      >
        <ad2-image-avatar
          v-if="userStore.getUser !== null"
          :username="userStore.getUser.username"
        />
      </v-btn>
    </template>
    <v-card min-width="300">
      <v-card-text class="pa-4">
        <div v-if="userStore.getUser !== null">
          <div class="text-h6 mb-0">
            {{ userStore.getUser.givenname }} {{ userStore.getUser.surname }}
          </div>
        </div>
      </v-card-text>

      <v-divider />

      <v-list
        density="compact"
        nav
        class="pa-2"
      >
        <v-list-item
          :prepend-icon="mdiCog"
          rounded="lg"
          color="primary"
          @click="showSettings = true"
        >
          <v-list-item-title class="text-body-2">
            Einstellungen
          </v-list-item-title>
        </v-list-item>
      </v-list>
    </v-card>
  </v-menu>

  <user-settings-dialog v-model="showSettings" />
</template>

<script setup lang="ts">
import { mdiCog } from "@mdi/js";
import { ref } from "vue";

import Ad2ImageAvatar from "@/components/User/Ad2ImageAvatar.vue";
import UserSettingsDialog from "@/components/User/UserSettingsDialog.vue";
import { useUserStore } from "@/stores/user.ts";

const userStore = useUserStore();
const showSettings = ref(false);
</script>

<style scoped>
.avatar-btn :deep(.v-btn__overlay) {
  opacity: 0 !important;
}

.avatar-btn:hover :deep(.v-btn__overlay) {
  opacity: 0 !important;
}
</style>
