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
        Nicht berechtigt
      </v-card-title>
      <v-card-text>
        Sie haben keine ausreichenden Berechtigungen,<br />
        um auf diese Anwendung zuzugreifen.<br />
        <br />
        <a
          :href="faqLink"
          target="_blank"
        >
          Warum bin ich nicht berechtigt?
        </a>
      </v-card-text>
      <v-card-actions class="justify-center">
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
          Berechtigungen neu laden
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { mdiLockAlert } from "@mdi/js";

function toHome(): void {
  window.location.href = "/";
}

function reload(): void {
  window.location.reload();
}

const faqLink = "https://go.muenchen.de/sp/KB0023236";
</script>
