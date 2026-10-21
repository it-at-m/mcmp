import { dirname, resolve } from "node:path";
import { fileURLToPath, URL } from "node:url";

import VueI18nPlugin from "@intlify/unplugin-vue-i18n/vite";
import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";
import vuetify, { transformAssetUrls } from "vite-plugin-vuetify";
import VueRouter from "vue-router/vite";

import { EncodeBracketsPlugin, extendRoute } from "./encode-brackets-plugin";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const isDevelopment = mode === "development";
  return {
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes("node_modules")) {
              if (id.includes("vuetify")) return "vendor-vuetify";
              if (id.includes("echarts")) return "vendor-echarts";
              return "vendor";
            }
          },
        },
      },
    },
    plugins: [
      VueRouter({
        routesFolder: {
          src: "src/routes",
        },
        dts: "./route-map.d.ts",
        ...(isDevelopment && { extendRoute }),
      }),
      vue({
        template: { transformAssetUrls },
        features: {
          optionsAPI: isDevelopment,
        },
      }),
      vuetify(),
      VueI18nPlugin({
        include: resolve(
          dirname(fileURLToPath(import.meta.url)),
          "./src/locales/*.json"
        ),
      }),
      {
        ...EncodeBracketsPlugin(),
        apply: "serve", // Ensures plugin is only applied during serve, see https://vite.dev/guide/using-plugins#conditional-application
      },
    ],
    server: {
      host: true,
      port: 8081,
      proxy: {
        "/api": "http://localhost:8083",
        "/actuator": "http://localhost:8083",
      },
      allowedHosts: [
        "host.docker.internal",
        "host.containers.internal",
        "localhost",
      ], // required to use frontend behind proxy (e.g. API Gateway)
      headers: {
        "x-frame-options": "SAMEORIGIN", // required to use devtools behind proxy (e.g. API Gateway)
      },
    },
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
  };
});
