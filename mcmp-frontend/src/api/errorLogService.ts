import type { ErrorLog } from "@/types/ErrorLog";
import type { Page } from "@/types/Page";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { ERROR_LOG_BASE, getApiBase } from "@/constants";

const BASE_URL = () => `${getApiBase()}${ERROR_LOG_BASE}`;

export default {
  getErrorLogs(
    loading: Ref<boolean>,
    offset: number,
    limit: number
  ): Promise<Page<ErrorLog>> {
    return apiFetch<Page<ErrorLog>>(
      `${BASE_URL()}?offset=${offset}&limit=${limit}`,
      {},
      loading
    );
  },

  searchByReference(
    loading: Ref<boolean>,
    reference: string
  ): Promise<Page<ErrorLog>> {
    return apiFetch<Page<ErrorLog>>(
      `${BASE_URL()}?reference=${encodeURIComponent(reference)}`,
      {},
      loading
    );
  },

  getErrorLogDetail(loading: Ref<boolean>, id: number): Promise<ErrorLog> {
    return apiFetch<ErrorLog>(`${BASE_URL()}/${id}`, {}, loading);
  },
};
