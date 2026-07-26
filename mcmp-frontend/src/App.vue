<template>
  <v-app v-if="isUserAuthorized">
    <the-snackbar />
    <v-navigation-drawer
      v-if="!showLockPage"
      :rail="rail"
      permanent
    >
      <v-list nav>
        <v-list-item
          :prepend-icon="rail ? mdiMenu : mdiMenuOpen"
          :title="rail ? undefined : 'Einklappen'"
          @click="rail = !rail"
        />
      </v-list>
      <v-divider />
      <v-list
        v-if="!appStore.isReadOnly && !appStore.isLocked"
        nav
      >
        <shop
          rail-mode
          :collapsed="rail"
        />
      </v-list>
      <v-divider />
      <v-list nav>
        <v-list-item
          v-for="btn in buttonsCenter"
          :key="btn.text"
          :to="{ path: btn.path }"
          :title="btn.text"
          :active="isCenterNavActive(btn.path)"
          color="primary"
        >
          <template #prepend>
            <v-badge
              :model-value="btn.isNew"
              color="info"
              dot
              offset-x="2"
              offset-y="2"
            >
              <v-icon>{{ btn.icon }}</v-icon>
            </v-badge>
          </template>

          <v-tooltip
            v-if="rail"
            activator="parent"
            location="right"
          >
            {{ btn.text }}
          </v-tooltip>
        </v-list-item>
      </v-list>
    </v-navigation-drawer>
    <v-app-bar
      v-if="!showLockPage"
      color="backgroundLight"
      class="appbar"
    >
      <v-row align="center">
        <v-col
          cols="3"
          class="d-flex align-center justify-start"
        >
          <v-tooltip
            :text="version?.version || ''"
            :aria-label="version?.version || ''"
          >
            <template #activator="{ props: tooltipProps }">
              <router-link
                to="/appservice"
                v-bind="tooltipProps"
              >
                <img
                  v-if="currentTheme.dark"
                  src="@/assets/MCMP_transparent.png"
                  alt="Link zur Startseite"
                  height="60"
                  class="mt-2 ml-2"
                />
                <img
                  v-else
                  src="@/assets/MCMP_transparent_black_text.png"
                  alt="Link zur Startseite"
                  height="60"
                  class="mt-2 ml-2"
                />
              </router-link>
            </template>
          </v-tooltip>
        </v-col>
        <v-col
          cols="6"
          class="d-flex align-center justify-center"
        />
        <v-col
          cols="3"
          class="d-flex align-center justify-end"
        >
          <v-tooltip
            v-for="btn in buttonsRight"
            :key="btn.title"
            :text="btn.title"
            location="bottom"
          >
            <template #activator="{ props }">
              <v-btn
                v-if="btn.if"
                v-bind="props"
                icon
                size="large"
                :to="btn.to"
                :href="btn.href"
                class="mr-2"
                :title="btn.title"
                @click="btn.click"
              >
                <v-badge
                  v-if="btn.title === 'History' && notificaton > 0"
                  color="_red"
                  location="top right"
                  :content="notificaton"
                >
                  <v-icon>{{ btn.icon }}</v-icon>
                </v-badge>
                <v-icon v-else>{{ btn.icon }}</v-icon>
              </v-btn>
            </template>
          </v-tooltip>
          <user-menu />
        </v-col>
      </v-row>
    </v-app-bar>
    <v-main class="main">
      <v-alert
        v-if="
          (appStore.isReadOnly ||
            appStore.isInfo ||
            (appStore.isLocked && isAdmin)) &&
          appStore.maintenanceMessage
        "
        color="warning"
        variant="flat"
        rounded="0"
        class="d-flex justify-center align-center py-2 text-center"
        style="color: rgba(0, 0, 0, 0.87) !important"
      >
        <!-- eslint-disable-next-line vue/no-v-html -->
        <span
          class="text-h6 font-weight-bold"
          v-html="appStore.maintenanceMessage"
        ></span>
      </v-alert>
      <v-container
        fluid
        class="main-container"
      >
        <template v-if="showLockPage">
          <v-container class="d-flex justify-center align-center fill-height">
            <v-card
              max-width="600"
              class="text-center pa-6"
            >
              <v-card-title class="text-h4 mb-4">
                <v-icon
                  size="64"
                  color="warning"
                  class="mb-2"
                  >{{ mdiLockAlert }}</v-icon
                >
                <br />Wartungsmodus
              </v-card-title>
              <v-card-text class="text-body-1">
                <!--
                  The maintenance message is converted from Markdown to HTML and sanitized on the backend.
                  Using v-html is necessary here to render the formatted content (e.g., bold text, links).
                -->
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div
                  v-html="
                    appStore.maintenanceMessage ||
                    'Die Anwendung befindet sich aktuell im Wartungsmodus.'
                  "
                ></div>
              </v-card-text>
              <v-card-actions class="justify-center">
                <v-btn
                  color="primary"
                  @click="loadMaintenanceData"
                  >Erneut prüfen</v-btn
                >
              </v-card-actions>
            </v-card>
          </v-container>
        </template>
        <router-view
          v-else
          v-slot="{ Component }"
        >
          <v-fade-transition mode="out-in">
            <component
              :is="Component"
              @get-notification="getNotificaton"
            />
          </v-fade-transition>
        </router-view>
      </v-container>
    </v-main>
  </v-app>
  <v-app v-else-if="userLoaded && !isUserAuthorized">
    <v-main>
      <unauthorized-view />
    </v-main>
  </v-app>
  <v-app v-else>
    <v-main>
      <v-container class="d-flex justify-center align-center fill-height">
        <v-progress-circular
          indeterminate
          size="64"
          color="primary"
        />
        <span class="ml-4">Berechtigungen werden überprüft...</span>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import type { AppVersion } from "@/types/AppVersion.ts";

import {
  mdiAlphaABox,
  mdiHarddisk,
  mdiHelpCircleOutline,
  mdiHistory,
  mdiKubernetes,
  mdiLockAlert,
  mdiMenu,
  mdiMenuOpen,
  mdiMessageTextOutline,
  mdiMoonWaningCrescent,
  mdiServer,
  mdiSitemap,
  mdiTools,
  mdiWhiteBalanceSunny,
} from "@mdi/js";
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useTheme } from "vuetify";

import appVersionService from "@/api/appVersionService.ts";
import jobService from "@/api/jobService";
import { getUser } from "@/api/user-client";
import userService from "@/api/userService";
import Shop from "@/components/shop/Shop.vue";
import TheSnackbar from "@/components/TheSnackbar.vue";
import UserMenu from "@/components/User/UserMenu.vue";
import { useAppStore } from "@/stores/app";
import { useUserStore } from "@/stores/user";
import User, { UserLocalDevelopment } from "@/types/User";
import UnauthorizedView from "@/views/UnauthorizedView.vue";

const userLoaded = ref(false);

const appStore = useAppStore();
const userStore = useUserStore();
const route = useRoute();

const theme = useTheme();

const rail = ref(true);

const currentTheme = computed(() => theme.global.current.value);
const themeLoading = ref(false);

const isAdmin = computed(() =>
  userStore.getUser?.authorities?.includes("ROLE_ADMIN")
);

const showLockPage = computed(() => appStore.isLocked && !isAdmin.value);

const isUserAuthorized = computed(() => {
  const user = userStore.getUser;
  if (!user) return false;

  // Check if the user has at least one GrantedAuthority and if get the notifications for this User
  if (user.authorities && user.authorities.length > 0) getNotificaton();
  return user.authorities && user.authorities.length > 0;
});

onMounted(() => {
  loadUser();
  loadThemePreference();
  loadVersion();
  loadMaintenanceData();

  setInterval(() => loadUser(), 1000 * 60 * 5);
  setInterval(() => appStore.fetchSystemStatus(), 1000 * 60);
});

watch(
  () => route.path,
  () => {
    appStore.fetchSystemStatus();
  }
);

async function loadMaintenanceData() {
  await appStore.fetchSystemStatus();
}

/**
 * Loads the saved theme preference from localStorage or DB
 */
async function loadThemePreference(): Promise<void> {
  const savedTheme = localStorage.getItem("theme");

  if (savedTheme) {
    // 1. Priority: Use LocalStorage
    theme.change(savedTheme);
  } else {
    // 2. Priority: Load from DB (since nothing is available locally)
    try {
      const isDarkDb = await userService.getDarkMode(themeLoading);
      const newTheme = isDarkDb ? "dark" : "light";

      theme.change(newTheme);
      localStorage.setItem("theme", newTheme);
    } catch (e) {
      console.debug("Error loading dark mode from DB ", e);
    }
  }
}

/**
 * Toggles the theme and saves the preference to localStorage and DB
 */
function toggleTheme(): void {
  const newTheme = theme.global.current.value.dark ? "light" : "dark";
  theme.change(newTheme);
  localStorage.setItem("theme", newTheme);

  userService.setDarkMode(newTheme === "dark", themeLoading).catch((e) => {
    console.debug("Error saving dark mode to DB ", e);
  });
}

/**
 * Loads UserInfo from the backend and sets it in the store.
 */
function loadUser(): void {
  getUser()
    .then((user: User) => {
      userStore.setUser(user);
      userLoaded.value = true;
      sessionStorage.removeItem("mcmp_auth_redirect_reload");
    })
    .catch(() => {
      // No user info received, so fallback
      if (import.meta.env.DEV) {
        userStore.setUser(UserLocalDevelopment());
      } else {
        userStore.setUser(null);
      }
      userLoaded.value = true;
    });
}

const notificaton = ref();
const loading = ref(false);
function getNotificaton(): void {
  jobService.getNotifications(loading).then((notifi) => {
    notificaton.value = notifi;
  });
}

const buttonsRight = computed(() => [
  {
    title: "History",
    icon: mdiHistory,
    to: { path: "/history" },
    click: "",
    if: true,
    href: "",
  },
  {
    title: "Feedback",
    icon: mdiMessageTextOutline,
    to: "",
    click: "",
    if: true,
    href: "mailto:itm.mcmp@muenchen.de",
  },
  {
    title: "Hilfe",
    icon: mdiHelpCircleOutline,
    to: { path: "/help" },
    click: "",
    if: true,
    href: "",
  },
  {
    title: currentTheme.value.dark
      ? "Wechsel zu hellem Modus"
      : "Wechsel zu dunklem Modus",
    icon: currentTheme.value.dark
      ? mdiWhiteBalanceSunny
      : mdiMoonWaningCrescent,
    to: "",
    click: toggleTheme,
    if: true,
    href: "",
  },
  {
    title: "Admin Einstellungen",
    icon: mdiTools,
    to: { path: "/settings" },
    click: "",
    if:
      userStore.getUser?.authorities?.includes("ROLE_ADMIN") ||
      userStore.getUser?.authorities?.includes("ROLE_NETWORK") ||
      userStore.getUser?.authorities?.includes("ROLE_WINDOWS") ||
      userStore.getUser?.authorities?.includes("ROLE_LINUX") ||
      userStore.getUser?.authorities?.includes("ROLE_ORACLE") ||
      userStore.getUser?.authorities?.includes("ROLE_NON-ORACLE") ||
      userStore.getUser?.authorities?.includes("ROLE_OPERATOR") ||
      userStore.getUser?.authorities?.includes("ROLE_SECURITY"),
    href: "",
  },
]);

const NEW_NAV_PATHS = ["/loadbalancer", "/openshift"];
const NEW_NAV_STORAGE_KEY = "mcmp_seen_new_nav";

const seenNewNavPaths = ref<string[]>(
  JSON.parse(localStorage.getItem(NEW_NAV_STORAGE_KEY) || "[]")
);

function markNavSeen(path: string) {
  if (seenNewNavPaths.value.includes(path)) return;
  seenNewNavPaths.value = [...seenNewNavPaths.value, path];
  localStorage.setItem(
    NEW_NAV_STORAGE_KEY,
    JSON.stringify(seenNewNavPaths.value)
  );
}

watch(
  () => route.path,
  (path) => {
    const matched = NEW_NAV_PATHS.find(
      (p) => path === p || path.startsWith(`${p}/`)
    );
    if (matched) markNavSeen(matched);
  },
  { immediate: true }
);

const buttonsCenter = computed(() => [
  { text: "Server", icon: mdiServer, path: "/server", isNew: false },
  {
    text: "Anwendungsservice",
    icon: mdiAlphaABox,
    path: "/appservice",
    isNew: false,
  },
  { text: "Storage", icon: mdiHarddisk, path: "/storage", isNew: false },
  {
    text: "Loadbalancer",
    icon: mdiSitemap,
    path: "/loadbalancer",
    isNew: !seenNewNavPaths.value.includes("/loadbalancer"),
  },
  {
    text: "Openshift",
    icon: mdiKubernetes,
    path: "/openshift",
    isNew: !seenNewNavPaths.value.includes("/openshift"),
  },
]);

function isCenterNavActive(path: string): boolean {
  return route.path === path || route.path.startsWith(`${path}/`);
}

const version = ref<AppVersion | null>(null);
const versionLoading = ref(false);
const versionError = ref<string | null>(null);

async function loadVersion() {
  versionError.value = null;
  try {
    version.value = await appVersionService.getVersion(versionLoading);
  } catch (e) {
    versionError.value = e instanceof Error ? e.message : String(e);
    version.value = null;
  }
}
</script>

<style scoped>
.main {
  /* noinspection CssUnresolvedCustomProperty */
  background-color: rgb(var(--v-theme-bg));
  height: 100%;
}

.main-container {
  height: 100%;
  padding: 0 !important;
}

.app-content {
  height: 100%;
  padding: 0;
  min-height: 0;
}

.appbar {
  box-shadow:
    0 1px 5px rgba(0, 0, 0, 0.12),
    0 1px 3px rgba(0, 0, 0, 0.24) !important;
}

@media print {
  .appbar {
    display: none !important;
  }

  .main {
    padding-top: 0 !important;
  }

  :global(.v-container) {
    padding: 0 !important;
    margin: 0 !important;
    max-width: none !important;
  }
}

:global(.v-btn--disabled) {
  opacity: 0.5 !important;
}

:global(.v-btn--disabled .v-btn__overlay) {
  opacity: 0 !important;
}
</style>
