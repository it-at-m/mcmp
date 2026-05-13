<template>
  <div class="avatar-wrapper">
    <v-avatar class="custom-avatar">
      <img
        v-if="!hasError"
        :src="avatarUrl"
        :alt="altText"
        style="width: 42px; height: 42px"
        @error="onError"
      />
      <v-icon
        v-else
        size="32"
        :color="iconColor"
      >
        {{ mdiAccount }}
      </v-icon>
    </v-avatar>
  </div>
</template>

<script setup lang="ts">
import { mdiAccount } from "@mdi/js";
import { computed, ref, watch } from "vue";
import { useTheme } from "vuetify";

import { DefaultLhmAvatarService } from "@/api/ad2image-avatar-client.ts";

const { username, avatarSize = "64" } = defineProps<{
  username: string;
  avatarSize?: string;
}>();

const theme = useTheme();
const hasError = ref(false);

const avatarMode = computed(() =>
  theme.global.current.value.dark ? "fallbackGenericDark" : "fallbackGeneric"
);

const avatarUrl = computed(() => {
  return DefaultLhmAvatarService.avatarHref(
    username,
    avatarMode.value,
    avatarSize
  );
});

const altText = computed(() => `Bild von ${username}`);

const iconColor = computed(() =>
  theme.global.current.value.dark ? "white" : "black"
);

function onError() {
  hasError.value = true;
}

watch(avatarUrl, () => {
  hasError.value = false;
});
</script>

<style scoped>
.avatar-wrapper :deep(.custom-avatar) {
  background-color: transparent !important;
}

.avatar-wrapper :deep(.v-avatar) {
  background-color: transparent !important;
}
</style>
