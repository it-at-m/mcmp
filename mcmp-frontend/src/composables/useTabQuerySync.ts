import type { Ref } from "vue";

import { watch } from "vue";
import { useRoute, useRouter } from "vue-router";

/**
 * Mirrors a tab ref into the route's query string so browser back/forward
 * restores the tab that was active, instead of always resetting to the
 * default tab on remount.
 */
export function useTabQuerySync(tab: Ref<string>, queryKey = "tab") {
  const route = useRoute();
  const router = useRouter();

  const initial = route.query[queryKey];
  if (typeof initial === "string") {
    tab.value = initial;
  }

  watch(tab, (newTab) => {
    if (route.query[queryKey] === newTab) return;
    void router.replace({ query: { ...route.query, [queryKey]: newTab } });
  });

  watch(
    () => route.query[queryKey],
    (newVal) => {
      if (typeof newVal === "string" && newVal !== tab.value) {
        tab.value = newVal;
      }
    }
  );
}
