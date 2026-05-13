import type { AppVersion } from "@/types/AppVersion.ts";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, APP_VERSION_BASE } from "@/constants";

export default {
  getVersion(loading: Ref<boolean>): Promise<AppVersion> {
    loading.value = true;

    return fetch(`${getApiBase()}${APP_VERSION_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
