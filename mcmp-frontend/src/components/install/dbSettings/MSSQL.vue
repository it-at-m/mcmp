<template>
  <v-select
    v-model="instlServerDetails.dbParams!.mssql.mssql_serversort"
    label="Serversortierungen*"
    :items="[
      'SQL_Latin1_General_CP1_CI_AS',
      'Latin1_General_CI_AS',
      'Latin1_General_CS_AS',
      'SQL_Latin1_General_CP1_CS_AS',
      'Latin1_General_100_CI_AS_KS_WS',
    ]"
    rounded
    :rules="[rules.notEmptySelectRule('Darf nicht leer sein')]"
    :menu-props="{ persistent: true, closeOnContentClick: true }"
  />
</template>

<script setup lang="ts">
import { useRules } from "@/composables/rules.ts";
import installServerDetails from "@/types/installServerDetails.ts";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
const rules = useRules();
</script>