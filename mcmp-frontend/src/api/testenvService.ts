import type { Ref } from "vue";

import { apiFetch } from "@/api/fetch-utils";
import { getApiBase, TESTENV_BASE } from "@/constants";

export default {
  getTestEnabled(loading: Ref<boolean>): Promise<boolean> {
    return apiFetch(`${getApiBase()}${TESTENV_BASE}`, {}, loading);
  },
};
