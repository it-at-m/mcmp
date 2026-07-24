import type { OpenshiftNamespaceDetail } from "@/types/OpenshiftNamespaceDetail";
import type {
  OpenshiftNamespaceListItem,
  OpenshiftNamespaceRef,
} from "@/types/OpenshiftNamespaceListItem";
import type { Page } from "@/types/Page";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { getApiBase, OPENSHIFT_NAMESPACE_BASE } from "@/constants";

export default {
  getVisibleNamespaces(
    loading: Ref<boolean>,
    offset: number,
    limit: number,
    sortBy: string,
    sortOrder: string,
    search?: string,
    favorites = false
  ): Promise<Page<OpenshiftNamespaceListItem>> {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
      sortBy,
      sortOrder,
      favorites: favorites.toString(),
    });
    if (search?.trim()) {
      params.append("search", search.trim());
    }
    return apiFetch(
      `${getApiBase()}${OPENSHIFT_NAMESPACE_BASE}?${params.toString()}`,
      {},
      loading
    );
  },

  addNamespaceToFavorites(namespaceId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${OPENSHIFT_NAMESPACE_BASE}/${namespaceId}/favorite`,
      { method: "PUT" },
      undefined
    );
  },

  removeNamespaceFromFavorites(namespaceId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${OPENSHIFT_NAMESPACE_BASE}/${namespaceId}/favorite`,
      { method: "DELETE" },
      undefined
    );
  },

  getNamespaceById(
    loading: Ref<boolean>,
    id: number
  ): Promise<OpenshiftNamespaceDetail> {
    return apiFetch(
      `${getApiBase()}${OPENSHIFT_NAMESPACE_BASE}/${id}`,
      {},
      loading
    );
  },

  getNamespacesByAppserviceId(
    loading: Ref<boolean>,
    appserviceId: number
  ): Promise<OpenshiftNamespaceRef[]> {
    return apiFetch(
      `${getApiBase()}${OPENSHIFT_NAMESPACE_BASE}/appservice/${appserviceId}`,
      {},
      loading
    );
  },
};
