import type { UserAutocomplete } from "@/types/UserAutocomplete";
import type { Ref } from "vue";

import { apiFetch, defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { AWX_CLIENT_BASE, getApiBase } from "@/constants";

export interface AwxNameCheck {
  exists: boolean;
  allowed: boolean;
}

export interface AwxOrgTeamDetails {
  name: string;
  organization: string | null;
  staticMembers: string[];
  ldapMembers: string[];
  otherMembers: string[];
}

export default {
  searchUsers(query: string): Promise<UserAutocomplete[]> {
    return apiFetch<UserAutocomplete[]>(
      `${getApiBase()}${AWX_CLIENT_BASE}/users/autocomplete?query=${encodeURIComponent(query)}`
    );
  },
  getLdapGroupSuggestions(query: string): Promise<string[]> {
    return apiFetch<string[]>(
      `${getApiBase()}${AWX_CLIENT_BASE}/ldap/suggestions?query=${encodeURIComponent(query)}`
    );
  },
  getOrganizationDetails(
    loading: Ref<boolean>,
    name: string
  ): Promise<AwxOrgTeamDetails> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${AWX_CLIENT_BASE}/organizations/details?name=${encodeURIComponent(name)}`,
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
  getTeamDetails(
    loading: Ref<boolean>,
    name: string
  ): Promise<AwxOrgTeamDetails> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${AWX_CLIENT_BASE}/teams/details?name=${encodeURIComponent(name)}`,
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
  getOrganizations(loading: Ref<boolean>): Promise<string[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${AWX_CLIENT_BASE}/organizations`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  getTeams(loading: Ref<boolean>): Promise<string[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${AWX_CLIENT_BASE}/teams`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  checkOrganization(
    loading: Ref<boolean>,
    name: string
  ): Promise<AwxNameCheck> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${AWX_CLIENT_BASE}/organizations/check?name=${encodeURIComponent(name)}`,
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
  checkTeam(loading: Ref<boolean>, name: string): Promise<AwxNameCheck> {
    loading.value = true;
    return fetch(
      `${getApiBase()}${AWX_CLIENT_BASE}/teams/check?name=${encodeURIComponent(name)}`,
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
};
