import { defineStore } from "pinia";
import { computed, ref } from "vue";

import User from "@/types/User";
import userService from "@/api/userService"; // Passe den Pfad an, falls nötig

export const useUserStore = defineStore("user", () => {
  const user = ref<User | null>(null);
  const loginPage = ref<string | null>(null);
  const loading = ref<boolean>(false);

  const getUser = computed((): User | null => {
    return user.value;
  });

  const getLoginPage = computed((): string | null => {
    return loginPage.value;
  });

  function setUser(payload: User | null): void {
    user.value = payload;
  }

  // Login-Page aus der Datenbank laden
  async function fetchLoginPage(): Promise<void> {
    try {
      loginPage.value = await userService.getLoginPage(loading);
    } catch (error) {
      console.error("Fehler beim Laden der Login-Page:", error);
      loginPage.value = null;
    }
  }

  return {
    getUser,
    setUser,
    getLoginPage,
    fetchLoginPage,
    loading
  };
});