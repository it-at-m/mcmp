import type { Page } from "@/types/Page";
import type { ServerAutocomplete } from "@/types/ServerAutocomplete.ts";
import type { ServerList } from "@/types/ServerList";
import type { ServerListExtended } from "@/types/ServerListExtended";
import type { Ref } from "vue";

import { apiFetch, defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, SERVER_BASE } from "@/constants";
import Server from "@/types/Server";

export default {
  getVisibleServers(
    loading: Ref<boolean>,
    offset: number,
    limit: number,
    sortBy: string,
    sortOrder: string,
    search: string,
    status: string[],
    os: string,
    favorites: boolean,
    installFailed = false
  ): Promise<Page<ServerList>> {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
      sortBy: sortBy,
      sortOrder: sortOrder,
      status: status.join(","),
      os: os,
      favorites: favorites.toString(),
      installFailed: installFailed.toString(),
    });

    const trimmedSearch = search?.trim();
    if (trimmedSearch) {
      params.append("search", trimmedSearch);
    }

    return apiFetch(
      `${getApiBase()}${SERVER_BASE}?${params.toString()}`,
      {},
      loading
    );
  },

  getServerById(loading: Ref<boolean>, serverId: number): Promise<Server> {
    loading.value = true;
    return fetch(`${getApiBase()}${SERVER_BASE}/${serverId}`, getConfig())
      .then((response) => {
        if (!response.ok) {
          throw Object.assign(
            new Error("Server konnte nicht geladen werden."),
            {
              status: response.status,
            }
          );
        }
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getServersByAppserviceId(
    loading: Ref<boolean>,
    appserviceId: number
  ): Promise<ServerListExtended[]> {
    return apiFetch(
      `${getApiBase()}${SERVER_BASE}/appservice/${appserviceId}`,
      {},
      loading
    );
  },

  getFullServersByAppserviceId(
    loading: Ref<boolean>,
    appserviceId: number
  ): Promise<Server[]> {
    return fetch(
      `${getApiBase()}${SERVER_BASE}/appservice/${appserviceId}/full`,
      getConfig()
    )
      .then((response) => {
        if (response.status === 404) {
          return [];
        }
        defaultResponseHandler(response.clone());
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getPatchnightErrorServers(loading: Ref<boolean>): Promise<Server[]> {
    return apiFetch(
      `${getApiBase()}${SERVER_BASE}/patchnight/errors`,
      {},
      loading
    );
  },

  getServersForAutocomplete(
    query: string | null
  ): Promise<ServerAutocomplete[]> {
    const params = query ? `?query=${encodeURIComponent(query)}` : "";
    return apiFetch<ServerAutocomplete[]>(
      `${getApiBase()}${SERVER_BASE}/autocomplete${params}`,
      {}
    );
  },

  addServerToFavorites(serverId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${SERVER_BASE}/${serverId}/favorite`,
      { method: "PUT" },
      {} // Leeres Objekt verhindert Lade-Ringe auf allen Zeilen
    );
  },

  removeServerFromFavorites(serverId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${SERVER_BASE}/${serverId}/favorite`,
      { method: "DELETE" },
      {} // Leeres Objekt verhindert Lade-Ringe auf allen Zeilen
    );
  },
};
