import type { Ref } from "vue";

import { computed, provide, reactive } from "vue";
import { useRoute } from "vue-router";

export interface CardExpandStore {
  isExpanded: (key: string, fallback: boolean) => boolean;
  setExpanded: (key: string, value: boolean) => void;
}

// Module-level so state survives component unmount/remount (e.g. navigating
// away to a linked detail page and back), but not a full page reload. Keyed
// by route path so different views/records don't share one collapse state.
const cardExpandStates = new Map<string, Record<string, boolean>>();

/**
 * Enables a "collapse/expand all" toggle for CommonCard instances within
 * the currently active tab. Cards scope their own key by the tab they were
 * created under (see CommonCard's "currentTab" inject), so this only needs
 * to store/filter by that key - it never re-scopes a card that's kept
 * mounted (v-show) in an inactive tab.
 */
export function useCollapsibleCards(tab: Ref<string>) {
  const route = useRoute();
  const stateKey = route.path;

  let stored = cardExpandStates.get(stateKey);
  if (!stored) {
    stored = {};
    cardExpandStates.set(stateKey, stored);
  }
  const cardExpandState = reactive<Record<string, boolean>>(stored);
  const cardKeyPrefix = computed(() => `${tab.value}::`);

  function isCardExpanded(key: string, fallback: boolean) {
    return key in cardExpandState
      ? (cardExpandState[key] ?? fallback)
      : fallback;
  }
  function setCardExpanded(key: string, value: boolean) {
    cardExpandState[key] = value;
  }

  provide<CardExpandStore>("cardExpandStore", {
    isExpanded: isCardExpanded,
    setExpanded: setCardExpanded,
  });
  provide<Ref<string>>("currentTab", tab);

  const currentTabCardValues = computed(() =>
    Object.entries(cardExpandState)
      .filter(([key]) => key.startsWith(cardKeyPrefix.value))
      .map(([, value]) => value)
  );
  const allCardsExpanded = computed(
    () =>
      currentTabCardValues.value.length > 0 &&
      currentTabCardValues.value.every(Boolean)
  );

  function toggleAllCards() {
    const target = !allCardsExpanded.value;
    for (const key of Object.keys(cardExpandState)) {
      if (key.startsWith(cardKeyPrefix.value)) {
        cardExpandState[key] = target;
      }
    }
  }

  return { allCardsExpanded, toggleAllCards };
}
