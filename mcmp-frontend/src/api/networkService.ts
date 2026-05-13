import type Network from "@/types/Network";
import type NetworkGroup from "@/types/NetworkGroup";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, NETWORK_BASE } from "@/constants";

export default {
  getNetworkGroups(loading: Ref<boolean>): Promise<NetworkGroup[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${NETWORK_BASE}/groups`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getNetworks(loading: Ref<boolean>): Promise<Network[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${NETWORK_BASE}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  getFilteredNetworkGroups(
    loading: Ref<boolean>,
    appserviceId: number,
    database: boolean
  ): Promise<NetworkGroup[]> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${NETWORK_BASE}/filtered_groups?appserviceId=${appserviceId}&database=${database}`,
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

  updateNetworkGroup(
    loading: Ref<boolean>,
    networkGroup: NetworkGroup
  ): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${NETWORK_BASE}/groups`, putConfig(networkGroup))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Netzwerkgruppe erfolgreich aktualisiert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },

  addNetworkGroup(
    loading: Ref<boolean>,
    networkGroup: NetworkGroup
  ): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${NETWORK_BASE}/groups`, postConfig(networkGroup))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Netzwerkgruppe erfolgreich gespeichert."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },

  deleteNetworkGroup(
    loading: Ref<boolean>,
    networkGroupId: number
  ): Promise<void> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${NETWORK_BASE}/groups/${networkGroupId}`,
      deleteConfig()
    )
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Netzwerkgruppe erfolgreich gelöscht."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },

  assignNetworkToGroup(
    loading: Ref<boolean>,
    networkId: number,
    groupId: number
  ): Promise<void> {
    loading.value = true;
    let sendString = `${getApiBase()}${NETWORK_BASE}/assign?networkId=${networkId}&groupId=${groupId}`;
    if (!groupId) {
      sendString = `${getApiBase()}${NETWORK_BASE}/assign?networkId=${networkId}`;
    }
    return fetch(sendString, postConfig(""))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Netzwerk erfolgreich der Gruppe zugewiesen."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },

  assignAppservicesToGroup(
    loading: Ref<boolean>,
    groupId: number,
    appserviceIds: number[]
  ): Promise<void> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${NETWORK_BASE}/appservices/${groupId}`,
      postConfig(appserviceIds)
    )
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Appservices erfolgreich der Gruppe zugewiesen."
        );
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
