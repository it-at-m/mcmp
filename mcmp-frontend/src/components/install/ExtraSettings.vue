<template>
  <div>
    <v-form ref="form">
      <maria-postgres-mysql-oracle
        v-if="
          (instlServerDetails.categoryType == categoryType.DB ||
            instlServerDetails.categoryType == categoryType.Mixed) &&
          (instlServerDetails.category?.label.match(/MariaDB/) ||
            instlServerDetails.category?.label.match(/PostgreSQL/) ||
            instlServerDetails.category?.label.match(/OracleDB/) ||
            instlServerDetails.category?.label.match(/MySQL/))
        "
        :instl-server-details="instlServerDetails"
      />
      <m-s-s-q-l
        v-if="
          (instlServerDetails.categoryType == categoryType.DB ||
            instlServerDetails.categoryType == categoryType.Mixed) &&
          instlServerDetails.category?.label.match(/MSSQL/)
        "
        :instl-server-details="instlServerDetails"
      />
    </v-form>
  </div>
</template>
<script setup lang="ts">
import MariaPostgresMysqlOracle from "@/components/install/dbSettings/MariaPostgresMysqlOracle.vue";
import MSSQL from "@/components/install/dbSettings/MSSQL.vue";
import installServerDetails, {
  categoryType,
} from "@/types/installServerDetails";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
</script>
