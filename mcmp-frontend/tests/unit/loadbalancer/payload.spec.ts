import { mount } from "@vue/test-utils";
import { describe, expect, test } from "vitest";
import { defineComponent, h, ref } from "vue";
import { VSelect } from "vuetify/components";

import LoadbalancerOrderListener from "@/components/Loadbalancer/LoadbalancerOrderListener.vue";
import LoadbalancerOrderServerPools from "@/components/Loadbalancer/LoadbalancerOrderServerPools.vue";
import {
  createDefaultLoadbalancerOrder,
  expectValidTlsCombination,
  globalMountOptions,
  serializedPayload,
} from "./test-utils";

// Mirrors how LoadbalancerOrder.vue wires ServerPools + Listener together:
// one shared reactive ldblOrder and one shared `protocol` ref (v-model).
function mountWizard(
  servers: { name: string; ip: string; ports: number[] }[] = []
) {
  const ldblOrder = createDefaultLoadbalancerOrder();

  const Host = defineComponent({
    setup() {
      const protocol = ref<"tcp" | "http" | "https">("http");
      return { protocol, ldblOrder };
    },
    render() {
      return h("div", [
        h(LoadbalancerOrderServerPools, {
          ldblOrder: this.ldblOrder,
          servers,
          protocol: this.protocol,
          "onUpdate:protocol": (v: "tcp" | "http" | "https") =>
            (this.protocol = v),
        }),
        h(LoadbalancerOrderListener, {
          ldblOrder: this.ldblOrder,
          protocol: this.protocol,
        }),
      ]);
    },
  });

  return { wrapper: mount(Host, globalMountOptions), ldblOrder };
}

function selectByLabel(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper
    .findAllComponents(VSelect)
    .find((c) => c.props("label") === label)!;
}

describe("Loadbalancer order wizard — full payload assembly", () => {
  test("TCP pool + TCP listener: no TLS, tcp monitor", async () => {
    const { wrapper, ldblOrder } = mountWizard();
    selectByLabel(wrapper, "Protokoll*").vm.$emit("update:modelValue", "tcp");
    await wrapper.vm.$nextTick();

    const payload = serializedPayload(ldblOrder);
    expect(payload.listener[0]).toMatchObject({
      listener_type: "tcp",
      clientside_tls: false,
      serverside_tls: false,
      persistence: "source-address",
    });
    expect(payload.server_pools[0].monitors).toEqual(["tcp"]);
  });

  test("HTTP pool + HTTP listener: both TLS flags false, no serverside_tls leak", async () => {
    const { wrapper, ldblOrder } = mountWizard();
    // both default to "http" already; explicitly pick HTTP for the listener
    selectByLabel(wrapper, "Listener Protokoll*").vm.$emit(
      "update:modelValue",
      "http"
    );
    await wrapper.vm.$nextTick();

    const payload = serializedPayload(ldblOrder);
    expect(payload.listener[0].clientside_tls).toBe(false);
    expect(payload.listener[0].serverside_tls).toBe(false);
    expectValidTlsCombination(payload.listener[0]);
  });

  test("HTTP pool + HTTPS listener: clientside_tls true, serverside_tls false (the regression this suite guards against)", async () => {
    const { wrapper, ldblOrder } = mountWizard();
    selectByLabel(wrapper, "Protokoll*").vm.$emit("update:modelValue", "http");
    await wrapper.vm.$nextTick();
    selectByLabel(wrapper, "Listener Protokoll*").vm.$emit(
      "update:modelValue",
      "https"
    );
    await wrapper.vm.$nextTick();

    const payload = serializedPayload(ldblOrder);
    expect(payload.listener[0].clientside_tls).toBe(true);
    expect(payload.listener[0].serverside_tls).toBe(false);
  });

  test("HTTPS pool + HTTPS listener: both TLS flags true", async () => {
    const { wrapper, ldblOrder } = mountWizard();
    selectByLabel(wrapper, "Protokoll*").vm.$emit("update:modelValue", "https");
    await wrapper.vm.$nextTick();

    const payload = serializedPayload(ldblOrder);
    expect(payload.listener[0].clientside_tls).toBe(true);
    expect(payload.listener[0].serverside_tls).toBe(true);
    expectValidTlsCombination(payload.listener[0]);
  });

  test("selected members and their ports serialize as plain data", async () => {
    const servers = [{ name: "srv1", ip: "10.0.0.1", ports: [80, 8080] }];
    const { wrapper, ldblOrder } = mountWizard(servers);
    selectByLabel(wrapper, "Server Auswahl*").vm.$emit(
      "update:modelValue",
      servers
    );
    await wrapper.vm.$nextTick();

    const payload = serializedPayload(ldblOrder);
    expect(payload.server_pools[0].member).toEqual([
      { name: "srv1", ip: "10.0.0.1", ports: [80, 8080] },
    ]);
  });
});
