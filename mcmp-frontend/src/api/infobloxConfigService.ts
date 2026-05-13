import type { InfobloxConfig } from "@/types/InfobloxConfig";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, INFOBLOX_CONFIG_BASE } from "@/constants";

export default {
  getInfobloxConfigs(loading: Ref<boolean>): Promise<InfobloxConfig[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${INFOBLOX_CONFIG_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  createConfig(
    infobloxConfig: InfobloxConfig,
    loading: Ref<boolean>
  ): Promise<void> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${INFOBLOX_CONFIG_BASE}`,
      postConfig(infobloxConfig)
    )
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Infoblox-Konfiguration erfolgreich erstellt."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  deleteConfig(infobloxConfigId: number, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${INFOBLOX_CONFIG_BASE}/${infobloxConfigId}`,
      deleteConfig()
    )
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Infoblox-Konfiguration erfolgreich gelöscht."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  updateConfig(
    infobloxConfig: InfobloxConfig,
    loading: Ref<boolean>
  ): Promise<void> {
    loading.value = true;
    if (infobloxConfig.apiPassword == null) {
      infobloxConfig.apiPassword = undefined;
    }
    return fetch(
      `${getApiBase()}${INFOBLOX_CONFIG_BASE}`,
      putConfig(infobloxConfig)
    )
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Infoblox-Konfiguration erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
