import { ref } from "vue";

import testenvService from "@/api/testenvService";

// shared between all components, the testenv endpoint is only called once
const isTestEnv = ref(false);
let request: Promise<void> | null = null;

/**
 * Whether the app runs in a test environment. Stays false until the backend confirms it,
 * so features that are only enabled in test environments stay hidden in prod.
 */
export function useTestEnv() {
  if (!request) {
    request = testenvService
      .getTestEnabled(ref(false))
      .then((enabled) => {
        isTestEnv.value = enabled;
      })
      .catch(() => {
        isTestEnv.value = false;
        request = null; // retry on next use
      });
  }
  return { isTestEnv };
}
