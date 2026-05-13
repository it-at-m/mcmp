import type Faq from "@/types/Faq.ts";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { FAQ_BASE, getApiBase } from "@/constants";

const BASE_URL = () => `${getApiBase()}${FAQ_BASE}`;

export default {
  getAllFaqs(loading: Ref<boolean>): Promise<Faq[]> {
    return apiFetch<Faq[]>(BASE_URL(), {}, loading);
  },

  getFaqById(loading: Ref<boolean>, id: number): Promise<Faq> {
    return apiFetch<Faq>(`${BASE_URL()}/${id}`, {}, loading);
  },

  createFaq(loading: Ref<boolean>, faq: Faq): Promise<Faq> {
    return apiFetch<Faq>(
      BASE_URL(),
      {
        method: "POST",
        body: JSON.stringify(faq),
      },
      loading
    );
  },

  updateFaq(loading: Ref<boolean>, id: number, faq: Faq): Promise<Faq> {
    return apiFetch<Faq>(
      `${BASE_URL()}/${id}`,
      {
        method: "PUT",
        body: JSON.stringify(faq),
      },
      loading
    );
  },

  deleteFaq(loading: Ref<boolean>, id: number): Promise<void> {
    return apiFetch<void>(
      `${BASE_URL()}/${id}`,
      {
        method: "DELETE",
      },
      loading
    );
  },
};
