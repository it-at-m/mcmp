import type Repository from "@/types/Repository";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, REPOSITORY_BASE } from "@/constants";

export default {
  getRepositoriesByServerId(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<Repository[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${REPOSITORY_BASE}/${serverId}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
