import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";
import type { LoadbalancerListItem } from "@/types/LoadbalancerListItem.ts";
import type { LbServerMembership } from "@/types/LbServerMembership";
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
    search?: string
  ): Promise<Page<LoadbalancerListItem>> {
    const params = new URLSearchParams({
      offset: offset.toString(),
      limit: limit.toString(),
      sortBy,
      sortOrder,
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

  getLoadbalancerById(
    loading: Ref<boolean>,
    id: number
  ): Promise<LoadbalancerDetail> {
    return apiFetch(
      `${getApiBase()}${LOADBALANCER_BASE}/${id}`,
      {},
      loading
    );
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
};
