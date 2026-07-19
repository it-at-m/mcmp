<template>
  <v-row>
    <v-col cols="6">
      <v-select
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_version
        "
        label="Datenbank Version*"
        :items="dbVersionItems"
        rounded
        :rules="
          validationRules.getDynamicRules(
            'db_version',
            instlServerDetails.category!.label
          )
        "
        :menu-props="{ persistent: true, closeOnContentClick: true }"
      />
    </v-col>
    <v-col cols="6">
      <v-number-input
        v-if="instlServerDetails.category?.label.match(/OracleDB/)"
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.oracle_datasize
        "
        label="Datenbank Daten Größe (in GB)*"
        rounded
        :rules="
          validationRules.getDynamicRules(
            'oracle_datasize',
            instlServerDetails.category!.label
          )
        "
        :min="15"
        :max="500"
        control-variant="split"
      />
    </v-col>
  </v-row>
  <v-row>
    <v-col cols="6">
      <v-text-field
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_name
        "
        label="Datenbank Name*"
        rounded
        :rules="
          validationRules.getDynamicRules(
            'customer_db_name',
            instlServerDetails.category!.label
          )
        "
      />
    </v-col>
    <v-col cols="6">
      <v-text-field
        v-if="!instlServerDetails.category?.label.match(/OracleDB/)"
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_user
        "
        label="Datenbank Benutzer*"
        rounded
        :rules="
          validationRules.getDynamicRules(
            'customer_db_user',
            instlServerDetails.category!.label
          )
        "
      />
    </v-col>
  </v-row>
  <v-row>
    <v-col cols="6">
      <v-text-field
        v-if="instlServerDetails.category?.label.match(/PostgreSQL/)"
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle
            .customer_db_schema
        "
        label="Datenbank Schema*"
        rounded
        :rules="
          validationRules.getDynamicRules(
            'customer_db_schema',
            instlServerDetails.category.label
          )
        "
      />
      <v-select
        v-if="
          instlServerDetails.category?.label.match(/MariaDB/) ||
          instlServerDetails.category?.label.match(/OracleDB/) ||
          instlServerDetails.category?.label.match(/MySQL/)
        "
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle
            .customer_db_charset
        "
        label="Datenbank Zeichensatz*"
        :items="
          instlServerDetails.category?.label.match(/OracleDB/)
            ? [
                'AL32UTF8',
                'WE8MSWIN1252',
                'WE8ISO8859P1',
                'WE8ISO8859P15',
                'WE8ISO8859P9',
              ]
            : ['utf8mb4', 'utf8', 'latin1']
        "
        rounded
        :rules="
          validationRules.getDynamicRules(
            'customer_db_charset',
            instlServerDetails.category!.label
          )
        "
        :menu-props="{ persistent: true, closeOnContentClick: true }"
      />
    </v-col>
    <v-col cols="6">
      <v-autocomplete
        v-if="instlServerDetails.category?.label.match(/PostgreSQL/)"
        v-model="instlServerDetails.dbParams!.mariaPostgresMysqlOracle.postgis"
        label="PostGIS Erweiterungen"
        :items="[
          'postgis',
          'postgis_topology',
          'postgis_raster',
          'postgis_sfcgal',
          'postgis_tiger_geocoder',
        ]"
        multiple
        chips
        rounded
      />
    </v-col>
  </v-row>
  <v-row v-if="!instlServerDetails.category?.label.match(/OracleDB/)">
    <v-col cols="4">
      <v-checkbox
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.conn_dima_admin
        "
        label="DIMA Admin Verbindung erlauben"
        rounded
      />
    </v-col>
    <v-col cols="4">
      <v-checkbox
        v-model="instlServerDetails.dbParams!.mariaPostgresMysqlOracle.conn_cap"
        label="CAP Verbindung erlauben"
        rounded
      />
    </v-col>
    <v-col cols="4">
      <v-checkbox
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.conn_app_server
        "
        label="Anwendungsserver Verbindung erlauben"
        rounded
        @click="
          getServers();
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_app_server =
            [];
        "
      />
    </v-col>
  </v-row>
  <v-row>
    <v-col cols="12">
      <v-autocomplete
        v-if="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle.conn_app_server
        "
        v-model="
          instlServerDetails.dbParams!.mariaPostgresMysqlOracle
            .customer_app_server
        "
        label="Anwendungsserver"
        :items="servers"
        item-title="name"
        :loading="loading"
        multiple
        chips
        rounded
        clearable
      />
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
import type { ServerList } from "@/types/ServerList.ts";

import { computed, ref, watch } from "vue";

import serverService from "@/api/serverService.ts";
import { useRules } from "@/composables/rules.ts";
import installServerDetails from "@/types/installServerDetails";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();

const loading = ref(false);
const servers = ref<ServerList[]>([]);
const validationRules = useRules();

const dbVersionItems = computed<string[]>(() => {
  const map = props.instlServerDetails.category?.allowedDBVersions as
    Record<string, string[]> | undefined;
  const os = props.instlServerDetails.osVersion;
  if (!map || !os) return [];
  if (Array.isArray(os)) {
    return os.flatMap((o: string) => map[o] ?? []);
  }
  return map[os] ?? [];
});

function getServers() {
  serverService
    .getServersByAppserviceId(loading, props.instlServerDetails.appservice!.id)
    .then((response) => {
      servers.value = response;
    });
}

watch(
  () => props.instlServerDetails.category,
  (newCategory) => {
    if (newCategory) {
      props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_schema =
        "";
      props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_charset =
        "";
      props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.postgis = [];
      if (newCategory.label.match(/MariaDB/)) {
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_type =
          "mariadb";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_version =
          "11.4";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_charset =
          "utf8mb4";
      } else if (newCategory.label.match(/PostgreSQL/)) {
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_type =
          "postgresql";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_version =
          "18";
      } else if (newCategory.label.match(/MySQL/)) {
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_type =
          "mysql";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_version =
          "8.4";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_charset =
          "utf8mb4";
      } else if (newCategory.label.match(/OracleDB/)) {
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_type =
          "oracle";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.db_version =
          "19c";
        props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_charset =
          "AL32UTF8";
      }
    }
  },
  { immediate: true }
);
watch(
  () =>
    props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle
      .customer_db_name,
  (newDbName) => {
    if (props.instlServerDetails.category?.label.match(/PostgreSQL/)) {
      props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_schema =
        newDbName;
    }
    props.instlServerDetails.dbParams!.mariaPostgresMysqlOracle.customer_db_user =
      newDbName;
  }
);
</script>
