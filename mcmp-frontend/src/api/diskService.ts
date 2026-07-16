import type Disk from "@/types/Disk";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { DISK_BASE, getApiBase } from "@/constants";

export default {
  getDisksByServerId(loading: Ref<boolean>, serverId: number): Promise<Disk[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${DISK_BASE}/${serverId}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
