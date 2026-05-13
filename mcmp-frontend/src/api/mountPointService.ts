import type MountPoint from "@/types/MountPoint";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, MOUNTPOINT_BASE } from "@/constants";

export default {
  getMountPointsByServerId(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<MountPoint[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${MOUNTPOINT_BASE}/${serverId}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
