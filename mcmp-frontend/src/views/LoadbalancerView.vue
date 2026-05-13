<template>
  <v-container>
    <v-row>
      <v-col cols="12" class="d-flex align-center justify-center">
        <img
              :src="commingSoon"
              alt="Comming Soon"
              height="400"
            />
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import commingSoon from "@/assets/commingSoon.png"
</script>
