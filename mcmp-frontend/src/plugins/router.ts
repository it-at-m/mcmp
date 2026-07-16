// Composables
import { createRouter, createWebHashHistory } from "vue-router";
import {
  routes as fileBasedRoutes,
  handleHotUpdate,
} from "vue-router/auto-routes";

import AppserviceView from "@/views/AppserviceView.vue";
import HilfeView from "@/views/HilfeView.vue";
import HistoryView from "@/views/HistoryView.vue";
import LoadbalancerView from "@/views/LoadbalancerView.vue";
import ServerView from "@/views/ServerView.vue";
import SettingsView from "@/views/SettingsView.vue";
import StorageView from "@/views/StorageView.vue";
import UnauthorizedView from "@/views/UnauthorizedView.vue";

const manualRoutes = [
  {
    path: "/server/:id",
    name: "serverWithId",
    component: ServerView,
    props: true,
  },
  {
    path: "/appservice/:appId",
    name: "serverWithAppServiceId",
    component: AppserviceView,
    props: true,
  },
  {
    path: "/appservice",
    name: "appservice",
    component: AppserviceView,
  },
  {
    path: "/server",
    name: "server",
    component: ServerView,
  },
  {
    path: "/storage/:type/:id",
    name: "storageWithTypeAndId",
    component: StorageView,
    props: true,
  },
  {
    path: "/storage",
    name: "storage",
    component: StorageView,
  },
  {
    path: "/loadbalancer",
    name: "Loadbalancer",
    component: LoadbalancerView,
  },
  {
    path: "/loadbalancer/:id",
    name: "LoadbalancerDetail",
    component: LoadbalancerView,
  },
  {
    path: "/help",
    name: "Hilfe",
    component: HilfeView,
  },
  {
    path: "/help/:faqId?",
    name: "Hilfe",
    component: HilfeView,
    props: true,
  },
  {
    path: "/history",
    name: "History",
    component: HistoryView,
  },
  {
    path: "/settings",
    name: "Settings",
    component: SettingsView,
  },
  {
    path: "/unauthorized",
    name: "Unauthorized",
    component: UnauthorizedView,
  },
];

const routes = [
  ...fileBasedRoutes,
  ...manualRoutes,
  { path: "/:catchAll(.*)*", redirect: "/appservice" }, // CatchAll route
];

const history = createWebHashHistory();

const router = createRouter({
  history,
  routes,
  scrollBehavior() {
    return {
      top: 0,
      left: 0,
    };
  },
});

if (import.meta.hot) {
  handleHotUpdate(router);
}

export default router;
