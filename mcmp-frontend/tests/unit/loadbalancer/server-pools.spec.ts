import { mdiMinusCircle, mdiPlusCircle } from "@mdi/js";
import { mount } from "@vue/test-utils";
import { describe, expect, test } from "vitest";
import { VBtn, VRadioGroup, VSelect } from "vuetify/components";

import LoadbalancerOrderServerPools from "@/components/Loadbalancer/LoadbalancerOrderServerPools.vue";
import {
  createDefaultLoadbalancerOrder,
  globalMountOptions,
} from "./test-utils";

function mountServerPools(
  servers: { name: string; ip: string; ports: number[] }[] = [],
  protocol: "tcp" | "http" | "https" = "http"
) {
  const ldblOrder = createDefaultLoadbalancerOrder();
  const wrapper = mount(LoadbalancerOrderServerPools, {
    ...globalMountOptions,
    props: { ldblOrder, servers, protocol },
  });
  return { wrapper, ldblOrder };
}

function protocolSelect(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAllComponents(VSelect)
    .find((c) => c.props("label") === "Protokoll*")!;
}

function loadbalancingModeSelect(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAllComponents(VSelect)
    .find((c) => c.props("label") === "Loadbalancing Modus*")!;
}

function memberSelect(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAllComponents(VSelect)
    .find((c) => c.props("label") === "Server Auswahl*")!;
}

function monitorRadios(wrapper: ReturnType<typeof mount>) {
  return wrapper.findComponent(VRadioGroup);
}

describe("LoadbalancerOrderServerPools.vue — protocol -> serverside_tls", () => {
  test("derives serverside_tls purely from the selected protocol, correcting a bad initial value", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    // start deliberately wrong, mount should not have corrected it yet
    ldblOrder.listener[0].serverside_tls = true;

    protocolSelect(wrapper).vm.$emit("update:modelValue", "https");
    await wrapper.vm.$nextTick();
    expect(ldblOrder.listener[0].serverside_tls).toBe(true);

    protocolSelect(wrapper).vm.$emit("update:modelValue", "http");
    await wrapper.vm.$nextTick();
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);

    protocolSelect(wrapper).vm.$emit("update:modelValue", "https");
    await wrapper.vm.$nextTick();
    protocolSelect(wrapper).vm.$emit("update:modelValue", "tcp");
    await wrapper.vm.$nextTick();
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);
  });

  test("protocol tcp forces listener persistence to source-address", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    protocolSelect(wrapper).vm.$emit("update:modelValue", "tcp");
    await wrapper.vm.$nextTick();
    expect(ldblOrder.listener[0].persistence).toBe("source-address");
  });
});

describe("LoadbalancerOrderServerPools.vue — loadbalancing_mode", () => {
  test("Least Connections option sends the backend-expected value 'least-connections-member'", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    loadbalancingModeSelect(wrapper).vm.$emit(
      "update:modelValue",
      "least-connections-member"
    );
    await wrapper.vm.$nextTick();
    expect(ldblOrder.server_pools[0].loadbalancing_mode).toBe(
      "least-connections-member"
    );
  });

  test("Round Robin is the default", () => {
    const { ldblOrder } = mountServerPools();
    expect(ldblOrder.server_pools[0].loadbalancing_mode).toBe("round-robin");
  });
});

describe("LoadbalancerOrderServerPools.vue — member selection & ports", () => {
  test("selecting a server copies it (with its ports) into server_pools[0].member", async () => {
    const servers = [{ name: "srv1", ip: "10.0.0.1", ports: [80] }];
    const { wrapper, ldblOrder } = mountServerPools(servers);

    memberSelect(wrapper).vm.$emit("update:modelValue", [servers[0]]);
    await wrapper.vm.$nextTick();

    expect(ldblOrder.server_pools[0].member).toEqual([
      { name: "srv1", ip: "10.0.0.1", ports: [80] },
    ]);
  });

  test("increasing/decreasing port count is clamped between 1 and 10, per server", async () => {
    const servers = [
      { name: "srv1", ip: "10.0.0.1", ports: [80] },
      { name: "srv2", ip: "10.0.0.2", ports: [80] },
    ];
    const { wrapper, ldblOrder } = mountServerPools(servers);
    memberSelect(wrapper).vm.$emit("update:modelValue", servers);
    await wrapper.vm.$nextTick();
    await wrapper.vm.$nextTick();

    const plusButtons = wrapper
      .findAllComponents(VBtn)
      .filter((b) => b.props("icon") === mdiPlusCircle);
    const minusButtons = wrapper
      .findAllComponents(VBtn)
      .filter((b) => b.props("icon") === mdiMinusCircle);
    expect(plusButtons.length).toBe(2);
    expect(minusButtons.length).toBe(2);

    // increase srv1's port count 12 times, should clamp at 10
    for (let i = 0; i < 12; i++) {
      await plusButtons[0].trigger("click");
    }
    expect(ldblOrder.server_pools[0].member[0].ports.length).toBe(10);
    // srv2 is untouched by srv1's increments
    expect(ldblOrder.server_pools[0].member[1].ports.length).toBe(1);

    // decrease srv1's port count 20 times, should clamp at 1 (never removes the last port)
    for (let i = 0; i < 20; i++) {
      await minusButtons[0].trigger("click");
    }
    expect(ldblOrder.server_pools[0].member[0].ports.length).toBe(1);
  });
});

describe("LoadbalancerOrderServerPools.vue — monitors", () => {
  test("tcp monitor selection sends the literal array ['tcp']", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    monitorRadios(wrapper).vm.$emit("update:modelValue", "tcp");
    await wrapper.vm.$nextTick();
    expect(ldblOrder.server_pools[0].monitors).toEqual(["tcp"]);
  });

  test("http monitor selection sends a full MonitorType object using the current DNS as Host header", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    ldblOrder.dns = "myapp.example.de";
    monitorRadios(wrapper).vm.$emit("update:modelValue", "http");
    await wrapper.vm.$nextTick();

    expect(ldblOrder.server_pools[0].monitors).toEqual([
      {
        type: "http",
        method: "GET",
        path: "/status",
        headers: { Host: "myapp.example.de" },
        receive_string: "200",
      },
    ]);
  });

  test("https monitor selection sends type 'https'", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    monitorRadios(wrapper).vm.$emit("update:modelValue", "https");
    await wrapper.vm.$nextTick();
    expect((ldblOrder.server_pools[0].monitors[0] as any).type).toBe("https");
  });

  test("switching pool protocol to https while an HTTP monitor is active falls back to tcp monitor", async () => {
    const { wrapper, ldblOrder } = mountServerPools();
    monitorRadios(wrapper).vm.$emit("update:modelValue", "http");
    await wrapper.vm.$nextTick();

    protocolSelect(wrapper).vm.$emit("update:modelValue", "https");
    await wrapper.vm.$nextTick();

    expect(ldblOrder.server_pools[0].monitors).toEqual(["tcp"]);
  });

  test("switching pool protocol to http while an HTTPS monitor is active falls back to tcp monitor", async () => {
    const { wrapper, ldblOrder } = mountServerPools([], "https");
    monitorRadios(wrapper).vm.$emit("update:modelValue", "https");
    await wrapper.vm.$nextTick();

    protocolSelect(wrapper).vm.$emit("update:modelValue", "http");
    await wrapper.vm.$nextTick();

    expect(ldblOrder.server_pools[0].monitors).toEqual(["tcp"]);
  });

  test("switching pool protocol to tcp intentionally leaves an active HTTP/HTTPS monitor in place", async () => {
    // Unlike the http<->https conflict resets above, switching to "tcp" does
    // NOT reset the monitor: an HTTP/HTTPS monitor is allowed to check a TCP
    // pool member, so the selection is left untouched.
    const { wrapper, ldblOrder } = mountServerPools();
    monitorRadios(wrapper).vm.$emit("update:modelValue", "http");
    await wrapper.vm.$nextTick();

    protocolSelect(wrapper).vm.$emit("update:modelValue", "tcp");
    await wrapper.vm.$nextTick();

    expect((ldblOrder.server_pools[0].monitors[0] as any).type).toBe("http");
  });
});
