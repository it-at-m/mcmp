import type { BaasConfig } from "@/types/BaasConfig";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, BAAS_BASE } from "@/constants";

export default {
  getBaasConfigs(loading: Ref<boolean>): Promise<BaasConfig[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${BAAS_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  createConfig(
    baasConfig: BaasConfig,
    loading: Ref<boolean>
  ): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${BAAS_BASE}`, postConfig(baasConfig))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Baas-Konfiguration erfolgreich erstellt."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  deleteConfig(baasConfigId: number, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${BAAS_BASE}/${baasConfigId}`, deleteConfig())
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Baas-Konfiguration erfolgreich gelöscht."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  updateConfig(
    baasConfig: BaasConfig,
    loading: Ref<boolean>
  ): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${BAAS_BASE}`, putConfig(baasConfig))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Baas-Konfiguration erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
