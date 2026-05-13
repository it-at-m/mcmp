import type { SnowConfig } from "@/types/SnowConfig";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, SNOW_BASE, STATUS_INDICATORS } from "@/constants";
import { useSnackbarStore } from "@/stores/snackbar";

export default {
  getSnowConfigs(loading: Ref<boolean>): Promise<SnowConfig[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${SNOW_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  createConfig(snowConfig: SnowConfig, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${SNOW_BASE}`, postConfig(snowConfig))
      .then((response) => {
        useSnackbarStore().showMessage({
          message: "ServiceNow-Konfiguration erfolgreich erstellt.",
          level: STATUS_INDICATORS.SUCCESS,
        });
        defaultResponseHandler(response);
      })
      .finally(() => {
        loading.value = false;
      });
  },
  deleteConfig(snowConfigId: number, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${SNOW_BASE}/${snowConfigId}`, deleteConfig())
      .then((response) => {
        useSnackbarStore().showMessage({
          message: "ServiceNow-Konfiguration erfolgreich gelöscht.",
          level: STATUS_INDICATORS.SUCCESS,
        });
        defaultResponseHandler(response);
      })
      .finally(() => {
        loading.value = false;
      });
  },
  updateConfig(snowConfig: SnowConfig, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${SNOW_BASE}`, putConfig(snowConfig))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "ServiceNow-Konfiguration erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
