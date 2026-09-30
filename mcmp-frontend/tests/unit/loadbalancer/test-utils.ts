import { createPinia } from "pinia";
import { expect } from "vitest";
import { reactive } from "vue";
import { createVuetify } from "vuetify";
import * as components from "vuetify/components";
import * as directives from "vuetify/directives";

import LoadbalancerOrder from "@/types/LoadbalancerOrder.ts";

export const pinia = createPinia();
export const vuetify = createVuetify({ components, directives });
export const globalMountOptions = { global: { plugins: [pinia, vuetify] } };

// Mirrors createDefaultLoadbalancerOrder() in LoadbalancerOrder.vue.
// Wrapped in reactive() because the real LoadbalancerOrderProp is a deeply
// reactive ref — a plain object here would let mutations silently bypass
// the components' watchers, hiding real bugs instead of exposing them.
export function createDefaultLoadbalancerOrder(): LoadbalancerOrder {
  return reactive(
    new LoadbalancerOrder(
      null,
      "",
      [
        {
          port: 443,
          server_pool: "default",
          listener_type: "http",
          clientside_tls: false,
          serverside_tls: false,
          x_forwarded_for: true,
          persistence: "cookie",
          wss: false,
        },
      ],
      [
        {
          member: [],
          monitors: [
            {
              type: "http",
              method: "GET",
              path: "/status",
              headers: { Host: "example.muenchen.de" },
              receive_string: "200",
            },
          ],
          loadbalancing_mode: "round-robin",
        },
      ]
    )
  );
}

// The payload eventually sent to the backend is
// JSON.parse(JSON.stringify(LoadbalancerOrderProp.value)) — assert against
// that serialized shape, not the live reactive object, so tests catch
// serialization issues (proxies, getters) too.
export function serializedPayload(ldblOrder: LoadbalancerOrder) {
  return JSON.parse(JSON.stringify(ldblOrder));
}

// A listener that terminates TLS towards the server pool but not towards
// the client is not a valid F5 configuration.
export function expectValidTlsCombination(
  listener: LoadbalancerOrder["listener"][0]
) {
  if (listener.serverside_tls) {
    expect(listener.clientside_tls).toBe(true);
  }
}
