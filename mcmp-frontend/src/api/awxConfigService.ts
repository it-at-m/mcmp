import type { AwxConfig } from "@/types/AwxConfig";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { AWX_BASE, getApiBase } from "@/constants";

export default {
  getAwxConfigs(loading: Ref<boolean>): Promise<AwxConfig[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${AWX_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  createConfig(awxConfig: AwxConfig, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${AWX_BASE}`, postConfig(awxConfig))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "AWX-Konfiguration erfolgreich erstellt."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  deleteConfig(awxConfigId: number, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${AWX_BASE}/${awxConfigId}`, deleteConfig())
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "AWX-Konfiguration erfolgreich gelöscht."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  updateConfig(awxConfig: AwxConfig, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    if (awxConfig.apiPassword == null) {
      awxConfig.apiPassword = undefined;
    }
    return fetch(`${getApiBase()}${AWX_BASE}`, putConfig(awxConfig))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "AWX-Konfiguration erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
