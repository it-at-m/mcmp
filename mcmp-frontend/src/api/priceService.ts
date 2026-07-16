import type { Ref } from "vue";

import {
  defaultResponseHandler,
  deleteConfig,
  getConfig,
  postConfig,
  putConfig,
} from "@/api/fetch-utils";
import { getApiBase, PRICE_BASE } from "@/constants";
import Price from "@/types/Price";

export default {
  async getPrices(loading: Ref<boolean>): Promise<Price[]> {
    loading.value = true;
    try {
      const response = await fetch(`${getApiBase()}${PRICE_BASE}`, getConfig());
      defaultResponseHandler(response);
      return response.json();
    } finally {
      loading.value = false;
    }
  },
  createPrice(loading: Ref<boolean>, price: Price): Promise<Price> {
    loading.value = true;
    return fetch(`${getApiBase()}${PRICE_BASE}`, postConfig(price))
      .then((response) => {
        defaultResponseHandler(response, true, "Preis erfolgreich erstellt.");
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  updatePrice(loading: Ref<boolean>, price: Price): Promise<Price> {
    loading.value = true;
    return fetch(`${getApiBase()}${PRICE_BASE}`, putConfig(price))
      .then((response) => {
        defaultResponseHandler(
          response,
          true,
          "Preis erfolgreich aktualisiert."
        );
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
  deletePrice(loading: Ref<boolean>, priceName: string): Promise<void> {
    loading.value = true;
    return fetch(`${getApiBase()}${PRICE_BASE}/${priceName}`, deleteConfig())
      .then((response) => {
        defaultResponseHandler(response, true, "Preis erfolgreich gelöscht.");
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
