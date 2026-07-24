import type { Ref } from "vue";

import { nextTick, onBeforeUnmount, onMounted, watch } from "vue";
import { useRoute } from "vue-router";

// Module-level so positions survive component unmount/remount (e.g. navigating
// away to a linked detail page and back), but not a full page reload.
const scrollPositions = new Map<string, number>();

/**
 * Restores the scroll position of a scrollable container when returning to
 * a route (e.g. via browser back), keyed by the full path including query
 * (so switching tabs, which is reflected in the query, keeps its own
 * position too).
 */
export function useScrollRestoration(
  elRef: Ref<HTMLElement | null | undefined>
) {
  const route = useRoute();
  let currentKey = route.fullPath;

  function saveCurrent() {
    if (elRef.value) {
      scrollPositions.set(currentKey, elRef.value.scrollTop);
    }
  }

  function restore(key: string) {
    const target = scrollPositions.get(key) ?? 0;
    const deadline = Date.now() + 3000;

    const waitForEl = () => {
      const el = elRef.value;
      if (!el || !el.isConnected) {
        if (Date.now() < deadline) {
          requestAnimationFrame(waitForEl);
        }
        return;
      }

      el.scrollTop = target;
      if (target === 0) return;

      const tick = () => {
        if (!el.isConnected) return;
        el.scrollTop = target;
        if (el.scrollTop < target && Date.now() < deadline) {
          requestAnimationFrame(tick);
        }
      };
      requestAnimationFrame(tick);
    };

    void nextTick(waitForEl);
  }

  function onScroll() {
    saveCurrent();
  }

  onMounted(() => {
    elRef.value?.addEventListener("scroll", onScroll, { passive: true });
    restore(currentKey);
  });

  onBeforeUnmount(() => {
    saveCurrent();
    elRef.value?.removeEventListener("scroll", onScroll);
  });

  watch(
    () => route.fullPath,
    (newPath) => {
      saveCurrent();
      currentKey = newPath;
      restore(newPath);
    }
  );
}
