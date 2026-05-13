import type Action from "@/types/Action";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { ACTION_BASE, getApiBase } from "@/constants";

export default {
  getActions(loading: Ref<boolean>): Promise<Action[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${ACTION_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  updateAction(action: Action, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${ACTION_BASE}`, putConfig(action))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Aktion erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  saveAction(action: Action, loading: Ref<boolean>): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${ACTION_BASE}`, postConfig(action))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Aktion erfolgreich gespeichert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
  getTemplatesFromAwx(
    loading: Ref<boolean>,
    department: string,
    awxConfigId: number
  ): Promise<JSON> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${ACTION_BASE}/getTemplatesFromAwx?department=${department}&awxConfigId=${awxConfigId}`,
      getConfig()
    )
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Aktionen erfolgreich von AWX importiert."
        );
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  getSingleJobTemplateFromAwx(
    loading: Ref<boolean>,
    templateId: number,
    awxConfigId: number
  ): Promise<JSON> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${ACTION_BASE}/getSingleJobTemplateFromAwx?templateId=${templateId}&awxConfigId=${awxConfigId}`,
      getConfig()
    )
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  getOrganizationsFromAwx(
    loading: Ref<boolean>,
    awxConfigId: number
  ): Promise<JSON> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${ACTION_BASE}/getOrganizationsFromAwx?awxConfigId=${awxConfigId}`,
      getConfig()
    )
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  getActionEnabled(
    loading: Ref<boolean>,
    actionIdentifier: string
  ): Promise<boolean> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${ACTION_BASE}/enabled?actionIdentifier=${actionIdentifier}`,
      getConfig()
    )
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
