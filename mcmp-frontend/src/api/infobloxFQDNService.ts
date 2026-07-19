import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils.ts";
import { getApiBase, INFOBLOX_FQDN_BASE } from "@/constants.ts";

export default {
  getFreeServerFQDN(
    loading: Ref<boolean>,
    prefix: string,
    application: string,
    serverType: string,
    appserviceId: number,
    domain: string,
    customNumber?: number
  ): Promise<string> {
    loading.value = true;
    prefix = prefix ? prefix.trim() : "";
    return fetch(
      `${getApiBase()}${INFOBLOX_FQDN_BASE}/getFreeFqdn?prefix=${prefix}&application=${application}
    &serverType=${serverType}&appserviceId=${appserviceId}
    &domain=${domain}${customNumber ? `&customNumber=${customNumber}` : ""}`,
      getConfig()
    )
      .then((response) => {
        defaultResponseHandler(response);
        return response.text();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getFreeDnsEntry(
    loading: Ref<boolean>,
    dnsName: string,
    appserviceId: number
  ): Promise<string> {
    loading.value = true;
    dnsName = dnsName ? dnsName.trim() : "";
    return fetch(
      `${getApiBase()}${INFOBLOX_FQDN_BASE}/getFreeDnsEntry?dnsName=${dnsName}&appserviceId=${appserviceId}`,
      getConfig()
    )
      .then((response) => {
        defaultResponseHandler(response, false, undefined, true);
        return response
          .json()
          .then((body) =>
            body && typeof body.dnsEntry === "string" ? body.dnsEntry : ""
          );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
