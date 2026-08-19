<!-- SettingsView.vue -->
<template>
  <div>
    <v-row>
      <v-col>
        <v-row class="mb-1 mt-1">
          <v-tabs
            v-model="tab"
            align-tabs="start"
          >
            <v-tab
              v-if="isAdmin"
              value="Actions"
            >
              Actions
              <template #prepend>
                <v-icon size="x-large">{{ mdiRocketLaunch }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin"
              value="Integration"
            >
              Integration
              <template #prepend>
                <v-icon size="x-large">{{ mdiPuzzle }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin"
              value="Admin"
            >
              Admin
              <template #prepend>
                <v-icon size="x-large">{{ mdiAccountCog }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin"
              value="Price"
            >
              Preise
              <template #prepend>
                <v-icon size="x-large">{{ mdiCurrencyEur }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isNetwork || isAdmin"
              value="Netzwerkgruppen"
            >
              Netzwerkgruppen
              <template #prepend>
                <v-icon size="x-large">{{ mdiNetwork }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin || isOtherRole"
              value="Patchnight"
            >
              Patchnight Status
              <template #prepend>
                <v-icon size="x-large">{{ mdiCogOutline }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin"
              value="JobStatistics"
            >
              Job Statistics
              <template #prepend>
                <v-icon size="x-large">{{ mdiChartBar }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin || isOtherRole"
              value="History"
            >
              <template #prepend>
                <v-icon size="x-large">{{ mdiHistory }}</v-icon>
              </template>
              History
            </v-tab>

            <v-tab
              v-if="isAdmin"
              value="AppConfig"
            >
              Status & Health
              <template #prepend>
                <v-icon size="x-large">{{ mdiHeartPulse }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              v-if="isAdmin"
              value="ErrorLog"
            >
              Error Logs
              <template #prepend>
                <v-icon size="x-large">{{ mdiAlertCircleOutline }}</v-icon>
              </template>
            </v-tab>
          </v-tabs>
        </v-row>

        <v-tabs-window v-model="tab">
          <v-tabs-window-item
            v-if="isAdmin"
            value="Actions"
          >
            <actions />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin"
            value="Integration"
          >
            <integration-settings />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin"
            value="Admin"
          >
            <admin-users />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin"
            value="Price"
          >
            <prices />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isNetwork || isAdmin"
            value="Netzwerkgruppen"
          >
            <network-group />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin || isOtherRole"
            value="Patchnight"
          >
            <patchnight-status />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin"
            value="JobStatistics"
          >
            <job-statistics />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin || isOtherRole"
            value="History"
          >
            <admin-history :key="historyKey" />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin"
            value="AppConfig"
          >
            <app-config />
          </v-tabs-window-item>

          <v-tabs-window-item
            v-if="isAdmin"
            value="ErrorLog"
          >
            <error-log />
          </v-tabs-window-item>
        </v-tabs-window>
      </v-col>
    </v-row>
  </div>
</template>

<script setup lang="ts">
import {
  mdiAccountCog,
  mdiAlertCircleOutline,
  mdiChartBar,
  mdiCogOutline,
  mdiCurrencyEur,
  mdiHeartPulse,
  mdiHistory,
  mdiNetwork,
  mdiPuzzle,
  mdiRocketLaunch,
} from "@mdi/js";
import { computed, nextTick, onMounted, provide, ref, watch } from "vue";
import { useRouter } from "vue-router";

import Actions from "@/components/Settings/actions.vue";
import AdminHistory from "@/components/Settings/AdminHistory.vue";
import adminUsers from "@/components/Settings/adminUsers.vue";
import AppConfig from "@/components/Settings/appConfig.vue";
import ErrorLog from "@/components/Settings/ErrorLog.vue";
import IntegrationSettings from "@/components/Settings/IntegrationSettings.vue";
import JobStatistics from "@/components/Settings/JobStatistics.vue";
import NetworkGroup from "@/components/Settings/NetworkGroup.vue";
import PatchnightStatus from "@/components/Settings/PatchnightStatus.vue";
import Prices from "@/components/Settings/Prices.vue";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();
const router = useRouter();
const tab = ref("History");
const historyKey = ref(0);

const hasOpenDialog = ref(false);
provide("registerOpenDialog", () => {
  hasOpenDialog.value = true;
});
provide("unregisterOpenDialog", () => {
  hasOpenDialog.value = false;
});

const isAdmin = computed(
  () => !!userStore.getUser?.authorities?.includes("ROLE_ADMIN")
);

const isNetwork = computed(
  () => !!userStore.getUser?.authorities?.includes("ROLE_NETWORK")
);

const isOtherRole = computed(() => {
  const auths = userStore.getUser?.authorities || [];
  return (
    auths.includes("ROLE_WINDOWS") ||
    auths.includes("ROLE_LINUX") ||
    auths.includes("ROLE_ORACLE") ||
    auths.includes("ROLE_NON-ORACLE") ||
    auths.includes("ROLE_OPERATOR") ||
    auths.includes("ROLE_SECURITY")
  );
});

onMounted(() => {
  redirectIfUnauthorized();
});

watch(tab, (newTab) => {
  if (newTab === "History") {
    // Key ändern um AdminHistory neu zu mounten und Paginierung zurückzusetzen
    historyKey.value++;
  }
});

// Redirect if user is loaded but unauthorized
watch(
  () => userStore.getUser,
  (user) => {
    if (user && user.authorities) {
      nextTick(() => {
        redirectIfUnauthorized();
      });
    }
  },
  { immediate: true }
);

function redirectIfUnauthorized() {
  // Ensure we have authorities before checking roles
  if (!userStore.getUser?.authorities) return;

  if (!isAdmin.value && !isNetwork.value && !isOtherRole.value) {
    router.push("/unauthorized").catch((err) => {
      // Ignore "Navigation duplicated" or "Navigation cancelled" errors
      if (err.name !== "NavigationDuplicated") {
        console.debug("Navigation error:", err);
      }
    });
  }
}
</script>
