import type { UnifiedStorageItem } from "@/types/Storage";
import type { UnifiedStorageItemList } from "@/types/UnifiedStorageItemList";
import type { Page } from "@/types/Page";
import type { UnifiedStorageMountItem } from "@/types/UnifiedStorageMountItem.ts";
import type { UnifiedStorageSnapshotItem } from "@/types/UnifiedStorageSnapshotItem.ts";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { getApiBase, STORAGE_BASE } from "@/constants";

export default {
  getUnifiedStorage(
    loading: Ref<boolean>,
    page: number,
    size: number,
    sortBy: string,
    sortOrder: string,
    search?: string,
    types?: string[]
  ): Promise<Page<UnifiedStorageItemList>> {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sort: sortBy ? `${sortBy},${sortOrder}` : "",
    });

    if (search) {
      params.append("search", search);
    }

    if (types && types.length > 0) {
      params.append("types", types.join(","));
    }

    return apiFetch(
      `${getApiBase()}${STORAGE_BASE}/unified?${params.toString()}`,
      {},
      loading
    );
  },

  getUnifiedStorageItem(
    loading: Ref<boolean>,
    type: string,
    uuid: string
  ): Promise<UnifiedStorageItem> {
    return apiFetch(
      `${getApiBase()}${STORAGE_BASE}/unified/${type}/${uuid}`,
      {},
      loading
    );
  },
  getUnifiedStorageMountItemsForServer(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<UnifiedStorageMountItem[]> {
    return apiFetch(
      `${getApiBase()}${STORAGE_BASE}/unified/server/${serverId}/mounts`,
      {},
      loading
    );
  },
  getUnifiedStorageSnapshotItems(
    loading: Ref<boolean>,
    type: string,
    uuid: string
  ): Promise<UnifiedStorageSnapshotItem[]> {
    return apiFetch(
      `${getApiBase()}${STORAGE_BASE}/unified/${type}/${uuid}/snapshots`,
      {},
      loading
    );
  },
};
