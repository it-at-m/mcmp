import { createApp } from "vue";

import App from "@/App.vue";
import { registerPlugins } from "@/plugins";
import { setRuntimeConfig } from "./constants";

import "@fontsource/roboto/latin-100.css";
import "@fontsource/roboto/latin-300.css";
import "@fontsource/roboto/latin-400.css";
import "@fontsource/roboto/latin-500.css";
import "@fontsource/roboto/latin-700.css";
import "@fontsource/roboto/latin-900.css";
import "@/assets/material-action-btn.css";
import "@/assets/split-view-layout.css";
import "@/assets/links.css";
import "@/assets/confirm-entity-info.css";
import "@/assets/dialog-action-btn.css";

async function loadConfig() {
  try {
    const response = await fetch("/config.json");
    if (response.ok) {
      return await response.json();
    }
  } catch {
    console.warn("config.json nicht gefunden, nutze .env Fallback");
  }

  // Fallback auf VITE_ Umgebungsvariablen (für lokale Entwicklung)
  return {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || "http://localhost:8083",
    ad2ImageUrl: import.meta.env.VITE_AD2IMAGE_URL || "http://localhost:8084",
  };
}

loadConfig().then((config) => {
  setRuntimeConfig(config);

  const app = createApp(App);

  app.config.performance = import.meta.env.DEV; // Browser Performance Profiling Infos in DEV

  registerPlugins(app); // ERST Plugins registrieren

  app.provide("config", config);
  app.mount("#app"); // DANN mounten
});
