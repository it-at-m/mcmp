import type { Ref } from "vue";

import { computed, provide, reactive } from "vue";

export interface CardExpandStore {
  isExpanded: (key: string, fallback: boolean) => boolean;
  setExpanded: (key: string, value: boolean) => void;
}

/**
 * Enables a "collapse/expand all" toggle for CommonCard instances within
 * the currently active tab. Cards scope their own key by the tab they were
 * created under (see CommonCard's "currentTab" inject), so this only needs
 * to store/filter by that key - it never re-scopes a card that's kept
 * mounted (v-show) in an inactive tab.
 */
export function useCollapsibleCards(tab: Ref<string>) {
  const cardExpandState = reactive<Record<string, boolean>>({});
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
