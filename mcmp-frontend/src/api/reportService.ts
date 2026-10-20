import type ForemanIpMismatchServer from "@/types/ForemanIpMismatchServer";
import type UnmappedForemanHost from "@/types/UnmappedForemanHost";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { getApiBase, REPORT_BASE } from "@/constants";

const BASE_URL = () => `${getApiBase()}${REPORT_BASE}`;

export default {
  /**
   * Fetches a list of unmapped Foreman host objects from the server.
   *
   * @param {Ref<boolean>} loading - A reactive reference indicating the loading state of the request.
   * @return {Promise<UnmappedForemanHost[]>} A promise that resolves to an array of unmapped Foreman host objects.
   */
  getUnmappedForemanHosts(
    loading: Ref<boolean>
  ): Promise<UnmappedForemanHost[]> {
    return apiFetch<UnmappedForemanHost[]>(
      `${BASE_URL()}/unmapped-foreman-hosts`,
      {},
      loading
    );
  },

  /**
   * Fetches a list of Foreman IP mismatches from the server.
   *
   * @param {Ref<boolean>} loading - A reactive reference to track the loading state of the request.
   * @return {Promise<ForemanIpMismatchServer[]>} A promise that resolves to an array of Foreman IP mismatch records.
   */
  getForemanIpMismatches(
    loading: Ref<boolean>
  ): Promise<ForemanIpMismatchServer[]> {
    return apiFetch<ForemanIpMismatchServer[]>(
      `${BASE_URL()}/foreman-ip-mismatches`,
      {},
      loading
    );
  },
};
