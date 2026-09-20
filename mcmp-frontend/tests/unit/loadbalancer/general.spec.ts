import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, test, vi } from "vitest";
import { VTextField } from "vuetify/components";

import infobloxFQDNService from "@/api/infobloxFQDNService.ts";
import LoadbalancerOrderGeneral from "@/components/Loadbalancer/LoadbalancerOrderGeneral.vue";
import {
  createDefaultLoadbalancerOrder,
  globalMountOptions,
} from "./test-utils";

vi.mock("@/api/appserviceService.ts", () => ({
  default: { getAppservices: vi.fn().mockResolvedValue({ content: [] }) },
}));
vi.mock("@/api/infobloxFQDNService.ts", () => ({
  default: { getFreeDnsEntry: vi.fn() },
}));

const appservice = {
  id: 1,
  name: "my-app",
  hasServers: true,
  environment: "prod",
  isFavorite: false,
} as any;

function mountGeneral() {
  const ldblOrder = createDefaultLoadbalancerOrder();
  const wrapper = mount(LoadbalancerOrderGeneral, {
    ...globalMountOptions,
    props: { ldblOrder, hasServers: true },
  });
  return { wrapper, ldblOrder };
}

function dnsField(wrapper: ReturnType<typeof mount>) {
  return wrapper
    .findAllComponents(VTextField)
    .find((c) => c.props("label") === "DNS Eintrag*")!;
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("LoadbalancerOrderGeneral.vue — dns resolution", () => {
  test("ldblOrder.dns stays unset while the debounced lookup is still pending", async () => {
    vi.mocked(infobloxFQDNService.getFreeDnsEntry).mockImplementation(
      () => new Promise(() => {}) // never resolves within the test
    );
    const { wrapper, ldblOrder } = mountGeneral();
    ldblOrder.appservice = appservice;
    dnsField(wrapper).vm.$emit("update:modelValue", "myapp");
    await wrapper.vm.$nextTick();

    // Still nothing written to the actual order object — only the local
    // `dns` ref (bound to the text field) reflects what the user typed.
    expect(ldblOrder.dns).toBe("");
  });

  test("ldblOrder.dns is set once the debounced lookup resolves successfully", async () => {
    vi.mocked(infobloxFQDNService.getFreeDnsEntry).mockResolvedValue(
      "myapp.example.de"
    );
    const { wrapper, ldblOrder } = mountGeneral();
    ldblOrder.appservice = appservice;
    dnsField(wrapper).vm.$emit("update:modelValue", "myapp");

    // wait out the 500ms debounce + promise resolution
    await new Promise((r) => setTimeout(r, 600));
    await wrapper.vm.$nextTick();

    expect(ldblOrder.dns).toBe("myapp.example.de");
    expect(infobloxFQDNService.getFreeDnsEntry).toHaveBeenCalledWith(
      expect.anything(),
      "myapp",
      appservice.id
    );
  });

  test("ldblOrder.dns is left unset when the lookup fails", async () => {
    vi.mocked(infobloxFQDNService.getFreeDnsEntry).mockRejectedValue(
      new Error("taken")
    );
    const { wrapper, ldblOrder } = mountGeneral();
    ldblOrder.appservice = appservice;
    dnsField(wrapper).vm.$emit("update:modelValue", "myapp");

    await new Promise((r) => setTimeout(r, 600));
    await wrapper.vm.$nextTick();

    expect(ldblOrder.dns).toBe("");
  });

  test("changing appservice after a successful lookup re-triggers resolution against the new appservice id", async () => {
    vi.mocked(infobloxFQDNService.getFreeDnsEntry).mockResolvedValue(
      "myapp.example.de"
    );
    const { wrapper, ldblOrder } = mountGeneral();
    ldblOrder.appservice = appservice;
    dnsField(wrapper).vm.$emit("update:modelValue", "myapp");
    await new Promise((r) => setTimeout(r, 600));
    await wrapper.vm.$nextTick();

    ldblOrder.appservice = { ...appservice, id: 2, name: "other-app" };
    await new Promise((r) => setTimeout(r, 600));
    await wrapper.vm.$nextTick();

    expect(infobloxFQDNService.getFreeDnsEntry).toHaveBeenLastCalledWith(
      expect.anything(),
      "myapp",
      2
    );
  });
});
