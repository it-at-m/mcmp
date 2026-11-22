import type { AdminUser } from "@/types/AdminUser";
import type { UserHistoryItem } from "@/types/User";
import type { UserAutocomplete } from "@/types/UserAutocomplete";
import type { Ref } from "vue";

import { apiFetch, postConfig, putConfig } from "@/api/fetch-utils";
import { getApiBase, USER_BASE } from "@/constants";

export interface PageResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export default {
  getAdminUsers(loading: Ref<boolean>): Promise<AdminUser[]> {
    return apiFetch<AdminUser[]>(
      `${getApiBase()}${USER_BASE}/admin`,
      {},
      loading
    );
  },

  getNotAdminUsers(loading: Ref<boolean>): Promise<AdminUser[]> {
    return apiFetch<AdminUser[]>(
      `${getApiBase()}${USER_BASE}/notAdmin`,
      {},
      loading
    );
  },

  updateAdminPermission(user: AdminUser, loading: Ref<boolean>): Promise<void> {
    return apiFetch<void>(
      `${getApiBase()}${USER_BASE}/admin`,
      putConfig(user),
      loading
    );
  },

  setDarkMode(darkMode: boolean, loading: Ref<boolean>): Promise<void> {
    return apiFetch<void>(
      `${getApiBase()}${USER_BASE}/darkmode?darkMode=${darkMode}`,
      postConfig(undefined),
      loading
    );
  },

  getDarkMode(loading: Ref<boolean>): Promise<boolean> {
    return apiFetch<boolean>(
      `${getApiBase()}${USER_BASE}/darkmode`,
      {},
      loading
    );
  },

  setLoginPage(loginPage: string, loading: Ref<boolean>): Promise<void> {
    return apiFetch<void>(
      `${getApiBase()}${USER_BASE}/loginpage?loginPage=${encodeURIComponent(loginPage)}`,
      { method: "PUT" },
      loading
    );
  },

  getLoginPage(loading: Ref<boolean>): Promise<string> {
    return apiFetch<string>(
      `${getApiBase()}${USER_BASE}/loginpage`,
      {},
      loading
    );
  },

  getUsersForAutocomplete(query: string | null): Promise<UserAutocomplete[]> {
    const params = query ? `?query=${encodeURIComponent(query)}` : "";
    return apiFetch<UserAutocomplete[]>(
      `${getApiBase()}${USER_BASE}/autocomplete${params}`,
      {}
    );
  },

  searchUsers(
    loading: Ref<boolean>,
    page: number,
    itemsPerPage: number,
    sortBy: string | null,
    sortDesc: boolean,
    searchText: string | null,
    updatedAtFrom: string | null
  ): Promise<PageResponse<UserHistoryItem>> {
    const params = new URLSearchParams();
    params.append("page", String(page - 1));
    params.append("size", String(itemsPerPage));

    if (sortBy) {
      params.append("sort", `${sortBy},${sortDesc ? "desc" : "asc"}`);
    }
    if (searchText) params.append("searchText", searchText);
    if (updatedAtFrom) params.append("updatedAtFrom", updatedAtFrom);

    return apiFetch<PageResponse<UserHistoryItem>>(
      `${getApiBase()}${USER_BASE}/search?${params.toString()}`,
      {},
      loading
    );
  },
};
