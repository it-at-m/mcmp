<template>
  <v-container class="d-flex justify-center align-center fill-height">
    <v-card
      max-width="500"
      class="text-center pa-6"
    >
      <v-card-title class="text-h4 mb-4">
        <v-icon
          size="64"
          color="error"
          class="mb-2"
          >{{ mdiLockAlert }}</v-icon
        >
        <br />
        {{ sessionExpired ? "Sitzung abgelaufen" : "Nicht berechtigt" }}
      </v-card-title>
      <v-card-text>
        <template v-if="sessionExpired">
          Ihre Sitzung ist abgelaufen.<br />
          Bitte melden Sie sich erneut an.
        </template>
        <template v-else>
          Sie haben keine ausreichenden Berechtigungen für diese Anwendung oder
          Ihre Sitzung ist abgelaufen.<br />
          <br />
          <a
            :href="faqLink"
            target="_blank"
          >
            Warum bin ich nicht berechtigt?
          </a>
        </template>
      </v-card-text>
      <v-card-actions class="justify-center">
        <v-btn
          v-if="sessionExpired"
          color="primary"
          @click="reload"
        >
          Neu anmelden
        </v-btn>
        <template v-else>
          <v-btn
            color="primary"
            @click="toHome"
          >
            Zurück zur Startseite
          </v-btn>
          <v-btn
            color="primary"
            @click="reload"
          >
            Erneut anmelden
          </v-btn>
        </template>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { mdiLockAlert } from "@mdi/js";

withDefaults(
  defineProps<{
    sessionExpired?: boolean;
  }>(),
  {
    sessionExpired: false,
  }
);

function toHome(): void {
  window.location.href = "/";
}

function reload(): void {
  window.location.assign("/oauth2/authorization/sso");
}

const faqLink = "https://go.muenchen.de/sp/KB0023236";
</script>
