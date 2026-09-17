import { mount } from "@vue/test-utils";
import { describe, expect, test } from "vitest";
import { VCheckbox, VSelect } from "vuetify/components";

import LoadbalancerOrderListener from "@/components/Loadbalancer/LoadbalancerOrderListener.vue";
import {
  createDefaultLoadbalancerOrder,
  expectValidTlsCombination,
  globalMountOptions,
} from "./test-utils";

function mountListener(protocol: "tcp" | "http" | "https") {
  const ldblOrder = createDefaultLoadbalancerOrder();
  const wrapper = mount(LoadbalancerOrderListener, {
    ...globalMountOptions,
    props: { ldblOrder, protocol },
  });
  return { wrapper, ldblOrder };
}

function listenerProtocolSelect(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAllComponents(VSelect)
    .find((c) => c.props("label") === "Listener Protokoll*")!;
}

describe("LoadbalancerOrderListener.vue — protocol prop -> listener defaults", () => {
  test("pool protocol http offers both options and defaults to https", () => {
    const { ldblOrder } = mountListener("http");
    expect(ldblOrder.listener[0].listener_type).toBe("http");
    expect(ldblOrder.listener[0].clientside_tls).toBe(true);
    expect(ldblOrder.listener[0].port).toBe(443);
  });

  test("pool protocol http never forces serverside_tls (owned by ServerPools only)", () => {
    const ldblOrder = createDefaultLoadbalancerOrder();
    ldblOrder.listener[0].serverside_tls = false;
    mount(LoadbalancerOrderListener, {
      ...globalMountOptions,
      props: { ldblOrder, protocol: "http" },
    });
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);
    expectValidTlsCombination(ldblOrder.listener[0]);
  });

  test("pool protocol https only offers https and forces clientside_tls", () => {
    const { ldblOrder } = mountListener("https");
    expect(ldblOrder.listener[0].listener_type).toBe("http");
    expect(ldblOrder.listener[0].clientside_tls).toBe(true);
    expect(ldblOrder.listener[0].port).toBe(443);
  });

  test("pool protocol tcp forces listener_type to tcp", () => {
    const { ldblOrder } = mountListener("tcp");
    expect(["tcp", "fast-tcp"]).toContain(ldblOrder.listener[0].listener_type);
  });
});

describe("LoadbalancerOrderListener.vue — user selects listener protocol", () => {
  test("selecting HTTP clears clientside_tls, sets port 80, leaves serverside_tls untouched", () => {
    const { wrapper, ldblOrder } = mountListener("http");
    listenerProtocolSelect(wrapper).vm.$emit("update:modelValue", "http");

    expect(ldblOrder.listener[0].clientside_tls).toBe(false);
    expect(ldblOrder.listener[0].port).toBe(80);
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);
    expectValidTlsCombination(ldblOrder.listener[0]);
  });

  test("selecting HTTPS after HTTP resets port to 443 only because it was the default 80", () => {
    const { wrapper, ldblOrder } = mountListener("http");
    listenerProtocolSelect(wrapper).vm.$emit("update:modelValue", "http"); // port -> 80
    listenerProtocolSelect(wrapper).vm.$emit("update:modelValue", "https"); // port 80 -> 443

    expect(ldblOrder.listener[0].clientside_tls).toBe(true);
    expect(ldblOrder.listener[0].port).toBe(443);
  });

  test("a custom (non-80) port is preserved when switching to HTTPS", () => {
    const { wrapper, ldblOrder } = mountListener("http");
    listenerProtocolSelect(wrapper).vm.$emit("update:modelValue", "http");
    ldblOrder.listener[0].port = 8080;
    listenerProtocolSelect(wrapper).vm.$emit("update:modelValue", "https");

    expect(ldblOrder.listener[0].port).toBe(8080);
  });
});

describe("LoadbalancerOrderListener.vue — TCP hardware acceleration checkbox", () => {
  function fastTcpCheckbox(wrapper: ReturnType<typeof mount>) {
    return wrapper
      .findAllComponents(VCheckbox)
      .find(
        (c) => c.props("label") === "TCP Hardwarebeschleunigung aktivieren"
      )!;
  }

  test("enabling it switches to fast-tcp and clears both TLS flags", () => {
    const { wrapper, ldblOrder } = mountListener("tcp");
    ldblOrder.listener[0].clientside_tls = true;
    ldblOrder.listener[0].serverside_tls = true;

    fastTcpCheckbox(wrapper).vm.$emit("update:modelValue", true);

    expect(ldblOrder.listener[0].listener_type).toBe("fast-tcp");
    expect(ldblOrder.listener[0].clientside_tls).toBe(false);
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);
  });

  test("disabling it falls back to plain tcp", () => {
    const { wrapper, ldblOrder } = mountListener("tcp");
    fastTcpCheckbox(wrapper).vm.$emit("update:modelValue", true);
    fastTcpCheckbox(wrapper).vm.$emit("update:modelValue", false);

    expect(ldblOrder.listener[0].listener_type).toBe("tcp");
    expect(ldblOrder.listener[0].clientside_tls).toBe(false);
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);
  });
});

describe("LoadbalancerOrderListener.vue — listener_type -> persistence / TLS", () => {
  test("switching listener_type to tcp clears TLS flags and forces source-address persistence", async () => {
    const { wrapper, ldblOrder } = mountListener("https");
    ldblOrder.listener[0].clientside_tls = true;
    ldblOrder.listener[0].serverside_tls = true;

    ldblOrder.listener[0].listener_type = "tcp";
    await wrapper.vm.$nextTick();

    expect(ldblOrder.listener[0].clientside_tls).toBe(false);
    expect(ldblOrder.listener[0].serverside_tls).toBe(false);
    expect(ldblOrder.listener[0].persistence).toBe("source-address");
  });

  test("switching listener_type back to http defaults persistence to cookie", async () => {
    const { wrapper, ldblOrder } = mountListener("tcp");
    ldblOrder.listener[0].listener_type = "http";
    await wrapper.vm.$nextTick();

    expect(ldblOrder.listener[0].persistence).toBe("cookie");
  });
});
