import type { SystemStatus } from "@/types/AppConfig";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { APP_CONFIG_BASE, getApiBase } from "@/constants";

const BASE_URL = () => `${getApiBase()}${APP_CONFIG_BASE}`;

export default {
  getSystemStatus(loading: Ref<boolean>): Promise<SystemStatus> {
    return apiFetch<SystemStatus>(
      `${BASE_URL()}/system-status`,
      {},
      loading,
      true
    );
  },

  updateSystemStatus(
    loading: Ref<boolean>,
    status: SystemStatus
  ): Promise<void> {
    return apiFetch(
      `${BASE_URL()}/system-status`,
      {
        method: "PUT",
        body: JSON.stringify(status),
      },
      loading
    ) as Promise<void>;
  },
};
