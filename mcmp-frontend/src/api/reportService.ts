import type ForemanIpMismatchServer from "@/types/ForemanIpMismatchServer";
import type UnmanagedServerDTO from "@/types/UnmanagedServerDTO.ts";
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

  /**
   * Fetches a list of unmanaged (not maintained by MCMP) virtual machines.
   *
   * The filters support SQL-LIKE patterns (e.g. `esxi%`, `%-IAAS%`) and are
   * evaluated case-insensitively by the backend. Both lists are optional; when
   * omitted, the backend returns all unmanaged VMs.
   *
   * The patterns are sent as a single comma-separated query parameter value
   * instead of repeating the parameter name: the backend rejects requests with
   * duplicated query parameters ("parameter pollution"). Spring still binds the
   * value to a `List<String>` by splitting on the comma.
   *
   * @param {Ref<boolean>} loading - A reactive reference to track the loading state of the request.
   * @param {string[]} excludeNames - Name patterns of VMs that should not be reported.
   * @param {string[]} excludeClusters - Cluster patterns that should not be reported.
   * @return {Promise<UnmanagedServerDTO.ts[]>} A promise that resolves to an array of unmanaged VMs.
   */
  getUnmanagedVms(
    loading: Ref<boolean>,
    excludeNames: string[] = [],
    excludeClusters: string[] = []
  ): Promise<UnmanagedServerDTO[]> {
    const params = new URLSearchParams();

    const nameValue = toPatternValue(excludeNames);
    const clusterValue = toPatternValue(excludeClusters);

    if (nameValue) {
      params.append("excludeName", nameValue);
    }
    if (clusterValue) {
      params.append("excludeCluster", clusterValue);
    }

    const query = params.toString();

    return apiFetch<UnmanagedServerDTO[]>(
      `${BASE_URL()}/unmanaged-vms${query ? `?${query}` : ""}`,
      {},
      loading
    );
  },
};

/**
 * Normalizes the filter patterns and joins them into a single comma-separated
 * value. Empty entries are dropped so no stray delimiters are sent.
 */
function toPatternValue(patterns: string[]): string {
  return patterns
    .map((pattern) => pattern.trim())
    .filter((pattern) => pattern.length > 0)
    .join(",");
}