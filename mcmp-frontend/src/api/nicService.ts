import type Nic from "@/types/Nic";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, NIC_BASE } from "@/constants";

export default {
  getNicsByServerId(loading: Ref<boolean>, serverId: number): Promise<Nic[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${NIC_BASE}/${serverId}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
