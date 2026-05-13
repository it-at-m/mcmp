import type FaqCategory from "@/types/FaqCategory";
import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { FAQ_CATEGORY_BASE, getApiBase } from "@/constants";

const BASE_URL = () => `${getApiBase()}${FAQ_CATEGORY_BASE}`;

export default {
  getAllCategories(loading: Ref<boolean>): Promise<FaqCategory[]> {
    return apiFetch<FaqCategory[]>(BASE_URL(), {}, loading);
  },

  getCategoryById(loading: Ref<boolean>, id: number): Promise<FaqCategory> {
    return apiFetch<FaqCategory>(`${BASE_URL()}/${id}`, {}, loading);
  },

  createCategory(
    loading: Ref<boolean>,
    category: FaqCategory
  ): Promise<FaqCategory> {
    return apiFetch<FaqCategory>(
      BASE_URL(),
      {
        method: "POST",
        body: JSON.stringify(category),
      },
      loading
    );
  },

  updateCategory(
    loading: Ref<boolean>,
    id: number,
    category: FaqCategory
  ): Promise<FaqCategory> {
    return apiFetch<FaqCategory>(
      `${BASE_URL()}/${id}`,
      {
        method: "PUT",
        body: JSON.stringify(category),
      },
      loading
    );
  },

  deleteCategory(loading: Ref<boolean>, id: number): Promise<void> {
    return apiFetch<void>(
      `${BASE_URL()}/${id}`,
      {
        method: "DELETE",
      },
      loading
    );
  },
};
