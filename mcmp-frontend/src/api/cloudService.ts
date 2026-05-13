import type { Cloud } from "@/types/Cloud";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, CLOUD_BASE } from "@/constants";

export default {
  getClouds(loading: Ref<boolean>): Promise<Cloud[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${CLOUD_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  createConfig(cloud: Cloud, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${CLOUD_BASE}`, postConfig(cloud))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "CLOUD-Konfiguration erfolgreich erstellt."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  deleteConfig(cloudId: number, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${CLOUD_BASE}/${cloudId}`, deleteConfig())
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "CLOUD-Konfiguration erfolgreich gelöscht."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  updateConfig(cloud: Cloud, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    if(cloud.apiPassword == null){
      cloud.apiPassword = undefined;
    }
    return fetch(`${getApiBase()}${CLOUD_BASE}`, putConfig(cloud))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "CLOUD-Konfiguration erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
