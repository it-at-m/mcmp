import type { LbServerMembership } from "@/types/LbServerMembership";
import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem.ts";
import type { Page } from "@/types/Page";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { getApiBase, LOADBALANCER_BASE } from "@/constants";

export default {
  getVisibleLoadbalancers(
    loading: Ref<boolean>,
    offset: number,
    limit: number,
    sortBy: string,
    sortOrder: string,
    search?: string,
    favorites = false
  ): Promise<Page<LoadbalancerListItem>> {
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
      `${getApiBase()}${LOADBALANCER_BASE}?${params.toString()}`,
      {},
      loading
    );
  },

  addLoadbalancerToFavorites(lbVirtualServerId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${LOADBALANCER_BASE}/${lbVirtualServerId}/favorite`,
      { method: "PUT" },
      undefined
    );
  },

  removeLoadbalancerFromFavorites(lbVirtualServerId: number): Promise<void> {
    return apiFetch(
      `${getApiBase()}${LOADBALANCER_BASE}/${lbVirtualServerId}/favorite`,
      { method: "DELETE" },
      undefined
    );
  },

  getLoadbalancerById(
    loading: Ref<boolean>,
    id: number
  ): Promise<LoadbalancerDetail> {
    return apiFetch(`${getApiBase()}${LOADBALANCER_BASE}/${id}`, {}, loading);
  },

  getPoolMembershipsByServerId(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<LbServerMembership[]> {
    return apiFetch(
      `${getApiBase()}${LOADBALANCER_BASE}/server/${serverId}`,
      {},
      loading
    );
  },
  getLoadbalancersByAppserviceId(
    loading: Ref<boolean>,
    appserviceId: number
  ): Promise<LoadbalancerListItem[]> {
    return apiFetch(
      `${getApiBase()}${LOADBALANCER_BASE}/appservice/${appserviceId}`,
      {},
      loading
    );
  },
};
