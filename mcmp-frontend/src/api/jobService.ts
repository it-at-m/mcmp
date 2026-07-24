import type JobIncidentSummary from "@/types/JobIncidentSummary";
import type JobList from "@/types/JobList.ts";
import type JobNodeHierarchy from "@/types/JobNodeHierarchy.ts";
import type JobStatistics from "@/types/JobStatistics";
import type { Page } from "@/types/Page";
import type { Ref } from "vue";

import {
  apiFetch,
  defaultResponseHandler,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, JOB_BASE } from "@/constants";

export default {
  startJob(
    loading: Ref<boolean>,
    actionIdentifier: string,
    serverId: number,
    awxExtraVars: Record<string, any> | null = null
  ): Promise<void> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${JOB_BASE}/create/${actionIdentifier}?serverId=${serverId}`,
      postConfig(awxExtraVars ? awxExtraVars : {})
    )
      .then((response) => {
        defaultResponseHandler(response, true, "Job erfolgreich gestartet.");
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getJobsByServerId(
    loading: Ref<boolean>,
    serverId: number,
    page = 1,
    itemsPerPage = 10,
    sortBy: string | null = null,
    sortDesc = false
  ): Promise<Page<JobList>> {
    loading.value = true;
    const params = new URLSearchParams();
    params.append("page", page.toString());
    params.append("itemsPerPage", itemsPerPage.toString());

    if (sortBy) {
      params.append("sortBy", sortBy);
      params.append("sortDesc", sortDesc.toString());
    }

    return fetch(
      `${getApiBase()}${JOB_BASE}/server/${serverId}?${params.toString()}`,
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

  getJobsByAppServiceId(
    loading: Ref<boolean>,
    appServiceId: number,
    page = 1,
    itemsPerPage = 10,
    sortBy: string | null = null,
    sortDesc = false
  ): Promise<Page<JobList>> {
    loading.value = true;
    const params = new URLSearchParams();
    params.append("page", page.toString());
    params.append("itemsPerPage", itemsPerPage.toString());

    if (sortBy) {
      params.append("sortBy", sortBy);
      params.append("sortDesc", sortDesc.toString());
    }

    return fetch(
      `${getApiBase()}${JOB_BASE}/appservice/${appServiceId}?${params.toString()}`,
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

  getJobsByUsername(
    loading: Ref<boolean>,
    page = 1,
    itemsPerPage = 10,
    sortBy: string | null = null,
    sortDesc = false
  ): Promise<Page<JobList>> {
    loading.value = true;
    const params = new URLSearchParams();
    params.append("page", page.toString());
    params.append("itemsPerPage", itemsPerPage.toString());

    if (sortBy) {
      params.append("sortBy", sortBy);
      params.append("sortDesc", sortDesc.toString());
    }

    return fetch(
      `${getApiBase()}${JOB_BASE}/user?${params.toString()}`,
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

  getJobStatistics(
    loading: Ref<boolean>,
    startDate: string,
    endDate: string
  ): Promise<JobStatistics[]> {
    return apiFetch<JobStatistics[]>(
      `${getApiBase()}${JOB_BASE}/statistics?startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`,
      {},
      loading
    );
  },

  getJobHierarchy(
    loading: Ref<boolean>,
    jobId: number
  ): Promise<JobNodeHierarchy[]> {
    return apiFetch<JobNodeHierarchy[]>(
      `${getApiBase()}${JOB_BASE}/${jobId}/hierarchy`,
      {},
      loading
    );
  },

  getJobIncidents(
    loading: Ref<boolean>,
    jobId: number
  ): Promise<JobIncidentSummary[]> {
    return apiFetch<JobIncidentSummary[]>(
      `${getApiBase()}${JOB_BASE}/${jobId}/incidents`,
      {},
      loading
    );
  },

  searchJobs(
    loading: Ref<boolean>,
    page: number,
    itemsPerPage: number,
    sortBy: string | null = null,
    sortDesc = false,
    jobId: string | null = null,
    awxJobId: string | null = null,
    createdAtFrom: string | null = null,
    createdAtTo: string | null = null,
    changeStartDateFrom: string | null = null,
    changeStartDateTo: string | null = null,
    userId: number | null = null,
    serverId: number | null = null,
    appserviceId: number | null = null,
    awxVariables: string | null = null,
    actionIdentifier: string | null = null,
    statusIdentifier: string | null = null
  ): Promise<Page<JobList>> {
    loading.value = true;
    const params = new URLSearchParams();
    params.append("page", page.toString());
    params.append("itemsPerPage", itemsPerPage.toString());
    if (sortBy) {
      params.append("sortBy", sortBy);
      params.append("sortDesc", sortDesc.toString());
    }
    if (jobId) params.append("jobId", jobId);
    if (awxJobId) params.append("awxJobId", awxJobId);
    if (createdAtFrom) params.append("createdFrom", createdAtFrom);
    if (createdAtTo) params.append("createdTo", createdAtTo);
    if (changeStartDateFrom)
      params.append("changeStartFrom", changeStartDateFrom);
    if (changeStartDateTo) params.append("changeStartTo", changeStartDateTo);
    if (userId) params.append("userId", userId.toString());
    if (serverId) params.append("serverId", serverId.toString());
    if (appserviceId) params.append("appserviceId", appserviceId.toString());
    if (awxVariables) params.append("awxVariables", awxVariables);
    if (actionIdentifier) params.append("actionIdentifier", actionIdentifier);
    if (statusIdentifier) params.append("statusIdentifier", statusIdentifier);

    return fetch(
      `${getApiBase()}${JOB_BASE}/search?${params.toString()}`,
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

  getNotifications(loading: Ref<boolean>) {
    loading.value = true;
    return fetch(`${getApiBase()}${JOB_BASE}/user/notification`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  resetNotifications(loading: Ref<boolean>) {
    loading.value = true;
    return fetch(`${getApiBase()}${JOB_BASE}/user/notification`, putConfig({}))
      .then((response) => {
        defaultResponseHandler(response);
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getAllActionIdentifiers(loading: Ref<boolean>): Promise<string[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${JOB_BASE}/actions`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },

  getAllStatusIdentifiers(loading: Ref<boolean>): Promise<string[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${JOB_BASE}/status`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
