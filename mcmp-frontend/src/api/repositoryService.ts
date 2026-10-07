import type { Page } from "@/types/Page.ts";
import type Repository from "@/types/Repository";
import type { RepositoryDetail } from "@/types/RepositoryDetail";
import type { ServerFqdn } from "@/types/ServerFqdn";
import type { Ref } from "vue";

import { apiFetch, defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { getApiBase, REPOSITORY_BASE } from "@/constants";

export default {
  getRepositoriesByServerId(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<Repository[]> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${REPOSITORY_BASE}/server/${serverId}`,
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
  getServersByRepositoryId(
    loading: Ref<boolean>,
    repositoryId: number
  ): Promise<ServerFqdn[]> {
    return apiFetch(
      `${getApiBase()}${REPOSITORY_BASE}/${repositoryId}/servers`,
      {},
      loading
    );
  },
  getRepositoriesByAppserviceId(
    loading: Ref<boolean>,
    appserviceId: number
  ): Promise<Repository[]> {
    return apiFetch(
      `${getApiBase()}${REPOSITORY_BASE}/appservice/${appserviceId}`,
      {},
      loading
    );
  },
  getVisibleRepositories(
    loading: Ref<boolean>,
    offset: number,
    limit: number,
    sortBy: string,
    sortOrder: string,
    search?: string,
    favorites = false,
    editableOnly = false
  ): Promise<Page<Repository>> {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
      sortBy,
      sortOrder,
      favorites: favorites.toString(),
      editableOnly: editableOnly.toString(),
    });
    if (search?.trim()) {
      params.append("search", search.trim());
    }
    return apiFetch(
      `${getApiBase()}${REPOSITORY_BASE}?${params.toString()}`,
      {},
      loading
    );
  },

  addRepositoryToFavorites(repositoryId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${REPOSITORY_BASE}/${repositoryId}/favorite`,
      { method: "PUT" },
      undefined
    );
  },

  removeRepositoryFromFavorites(repositoryId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${REPOSITORY_BASE}/${repositoryId}/favorite`,
      { method: "DELETE" },
      undefined
    );
  },

  getRepositoryById(
    loading: Ref<boolean>,
    id: number
  ): Promise<RepositoryDetail> {
    return apiFetch(`${getApiBase()}${REPOSITORY_BASE}/${id}`, {}, loading);
  },
};
