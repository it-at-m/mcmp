import type Snapshot from "@/types/Snapshot";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, SNAPSHOT_BASE } from "@/constants";

export default {
  getSnapshotsByServerId(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<Snapshot[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${SNAPSHOT_BASE}/${serverId}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
