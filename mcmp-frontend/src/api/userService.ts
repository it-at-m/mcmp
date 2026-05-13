import type { AdminUser } from "@/types/AdminUser";
import type { UserAutocomplete } from "@/types/UserAutocomplete";
import type { Ref } from "vue";



import { apiFetch, postConfig, putConfig } from "@/api/fetch-utils";
import { getApiBase, USER_BASE } from "@/constants";


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

  getUsersForAutocomplete(query: string | null): Promise<UserAutocomplete[]> {
    const params = query ? `?query=${encodeURIComponent(query)}` : "";
    return apiFetch<UserAutocomplete[]>(
      `${getApiBase()}${USER_BASE}/autocomplete${params}`,
      {}
    );
  },
};