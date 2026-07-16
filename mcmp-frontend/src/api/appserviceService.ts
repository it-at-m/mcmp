import type Appservice from "@/types/Appservice.ts";
import type AppserviceList from "@/types/AppserviceList.ts";
import type { Page } from "@/types/Page";
import type { Ref } from "vue";

import {
  defaultResponseHandler,
  getConfig,
} from "@/api/fetch-utils";
import { APPSERVICE_BASE, getApiBase } from "@/constants";

export default {
  getAppservices(
    loading: Ref<boolean>,
    offset: number,
    limit: number,
    sortOrder: string,
    search: string | null
  ): Promise<Page<AppserviceList>> {
    loading.value = true;
    const encodedSearch = search ? encodeURIComponent(search) : "";
    return fetch(
      `${getApiBase()}${APPSERVICE_BASE}?offset=${offset}&limit=${limit}&sortOrder=${sortOrder}&search=${encodedSearch}`,
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

  getAppservice(loading: Ref<boolean>, id: number): Promise<Appservice> {
    loading.value = true;
    return fetch(`${getApiBase()}${APPSERVICE_BASE}/${id}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
