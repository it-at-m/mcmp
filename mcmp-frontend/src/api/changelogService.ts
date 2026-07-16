import type Changelog from "@/types/Changelog";
import type { Page } from "@/types/Page";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { CHANGELOG_BASE, getApiBase } from "@/constants";

const BASE_URL = () => `${getApiBase()}${CHANGELOG_BASE}`;

export default {
  getAllChangelogs(
    loading: Ref<boolean>,
    offset: number,
    limit: number
  ): Promise<Page<Changelog>> {
    return apiFetch<Page<Changelog>>(
      `${BASE_URL()}?offset=${offset}&limit=${limit}`,
      {},
      loading
    );
  },

  createChangelog(
    loading: Ref<boolean>,
    changelog: Changelog
  ): Promise<Changelog> {
    return apiFetch<Changelog>(
      BASE_URL(),
      {
        method: "POST",
        body: JSON.stringify(changelog),
      },
      loading
    );
  },

  updateChangelog(
    loading: Ref<boolean>,
    id: number,
    changelog: Changelog
  ): Promise<Changelog> {
    return apiFetch<Changelog>(
      `${BASE_URL()}/${id}`,
      {
        method: "PUT",
        body: JSON.stringify(changelog),
      },
      loading
    );
  },

  deleteChangelog(loading: Ref<boolean>, id: number): Promise<void> {
    return apiFetch<void>(
      `${BASE_URL()}/${id}`,
      {
        method: "DELETE",
      },
      loading
    );
  },
};
