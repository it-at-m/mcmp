<template>
  <div>
    <v-form ref="form">
      <MariaPostgresMysqlOracle
        v-if="
          (instlServerDetails.categoryType == categoryType.DB ||
            instlServerDetails.categoryType == categoryType.Mixed) &&
          (instlServerDetails.category?.label.match(/MariaDB/) ||
            instlServerDetails.category?.label.match(/PostgreSQL/) ||
            instlServerDetails.category?.label.match(/OracleDB/) ||
            instlServerDetails.category?.label.match(/MySQL/))
        "
        :instlServerDetails="instlServerDetails"
      />
      <MSSQL
        v-if="(instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed) &&
          instlServerDetails.category?.label.match(/MSSQL/)"
        :instlServerDetails="instlServerDetails" />
    </v-form>
  </div>
</template>
<script setup lang="ts">
import MariaPostgresMysqlOracle from "@/components/install/dbSettings/MariaPostgresMysqlOracle.vue";
import installServerDetails, {
  categoryType,
} from "@/types/installServerDetails";
import MSSQL from "@/components/install/dbSettings/MSSQL.vue";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
</script>
