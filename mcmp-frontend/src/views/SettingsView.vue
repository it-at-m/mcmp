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
              value="Actions"
              v-if="isAdmin"
            >
              Actions
              <template #prepend>
                <v-icon size="x-large">{{ mdiRocketLaunch }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="Integration"
              v-if="isAdmin"
            >
              Integration
              <template #prepend>
                <v-icon size="x-large">{{ mdiPuzzle }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="Admin"
              v-if="isAdmin"
            >
              Admin
              <template #prepend>
                <v-icon size="x-large">{{ mdiAccountCog }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="Deployment vCenter C"
              v-if="isAdmin"
            >
              Deployment vCenter C
              <template #prepend>
                <v-icon size="x-large">{{ mdiServer }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="Price"
              v-if="isAdmin"
            >
              Preise
              <template #prepend>
                <v-icon size="x-large">{{ mdiCurrencyEur }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="Netzwerkgruppen"
              v-if="isNetwork || isAdmin"
            >
              Netzwerkgruppen
              <template #prepend>
                <v-icon size="x-large">{{ mdiNetwork }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="Patchnight"
              v-if="isAdmin || isOtherRole"
            >
              Patchnight Status
              <template #prepend>
                <v-icon size="x-large">{{ mdiCogOutline }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="JobStatistics"
              v-if="isAdmin"
            >
              Job Statistics
              <template #prepend>
                <v-icon size="x-large">{{ mdiChartBar }}</v-icon>
              </template>
            </v-tab>

            <v-tab
              value="History"
              v-if="isAdmin || isOtherRole"
            >
              <template #prepend>
                <v-icon size="x-large">{{ mdiHistory }}</v-icon>
              </template>
              History
            </v-tab>

            <v-tab
              value="AppConfig"
              v-if="isAdmin"
            >
              Status & Health
              <template #prepend>
                <v-icon size="x-large">{{ mdiHeartPulse }}</v-icon>
              </template>
            </v-tab>
          </v-tabs>
        </v-row>

        <v-tabs-window v-model="tab">
          <v-tabs-window-item
            value="Actions"
            v-if="isAdmin"
          >
            <Actions />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="Integration"
            v-if="isAdmin"
          >
            <integrationSettings />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="Admin"
            v-if="isAdmin"
          >
            <adminUsers />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="Deployment vCenter C"
            v-if="isAdmin"
          >
            <v-centerc />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="Price"
            v-if="isAdmin"
          >
            <Prices />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="Netzwerkgruppen"
            v-if="isNetwork || isAdmin"
          >
            <NetworkGroup />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="Patchnight"
            v-if="isAdmin || isOtherRole"
          >
            <PatchnightStatus />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="JobStatistics"
            v-if="isAdmin"
          >
            <JobStatistics />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="History"
            v-if="isAdmin || isOtherRole"
          >
            <AdminHistory :key="historyKey" />
          </v-tabs-window-item>

          <v-tabs-window-item
            value="AppConfig"
            v-if="isAdmin"
          >
            <app-config />
          </v-tabs-window-item>
        </v-tabs-window>
      </v-col>
    </v-row>
  </div>
</template>

<script setup lang="ts">
import { mdiAccountCog, mdiChartBar, mdiCogOutline, mdiCurrencyEur, mdiHeartPulse, mdiHistory, mdiNetwork, mdiPuzzle, mdiRocketLaunch, mdiServer } from "@mdi/js";
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";



import Actions from "@/components/Settings/actions.vue";
import AdminHistory from "@/components/Settings/AdminHistory.vue";
import adminUsers from "@/components/Settings/adminUsers.vue";
import AppConfig from "@/components/Settings/appConfig.vue";
import IntegrationSettings from "@/components/Settings/IntegrationSettings.vue";
import JobStatistics from "@/components/Settings/JobStatistics.vue";
import NetworkGroup from "@/components/Settings/NetworkGroup.vue";
import PatchnightStatus from "@/components/Settings/PatchnightStatus.vue";
import Prices from "@/components/Settings/Prices.vue";
import VCenterc from "@/components/Settings/vCenterc.vue";
import { useUserStore } from "@/stores/user";


const userStore = useUserStore();
const router = useRouter();
const tab = ref("History");
const historyKey = ref(0);

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