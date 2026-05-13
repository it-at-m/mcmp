<template>
  <common-card
    title="Export-Policys"
    v-if="selectedStorageItem.type == 'NFS'"
  >
    <v-row>
      <v-col cols="3">
        <h3>Server</h3>
      </v-col>
      <v-col cols="3" />
      <v-col cols="3">
        <h3>Berechtigungen</h3>
      </v-col>
    </v-row>
    <div
      v-for="(rule, idx) in selectedStorageItem.nfs_export_policy!
        .ontapExportPolicyRules"
    >
      <v-row class="ma-1">
        <v-col cols="3">
          {{ rule.clients.join(", ") }}
        </v-col>
        <v-col cols="3" />
        <v-col cols="3">
          {{ rule.rwRules == "never" ? "read-only" : "read-write" }}
        </v-col>
      </v-row>
      <v-divider
        v-if="
          selectedStorageItem.nfs_export_policy!.ontapExportPolicyRules!
            .length >
          idx + 1
        "
      />
    </div>
  </common-card>
  <common-card
    title="Freigabeberechtigungen"
    v-else-if="selectedStorageItem.type == 'CIFS'"
  >
    <v-row>
      <v-col cols="3">
        <h3>Benutzer, Gruppe, Computerkonto</h3>
      </v-col>
      <v-col cols="3" />
      <v-col cols="3">
        <h3>Berechtigungen</h3>
      </v-col>
    </v-row>
    <div v-for="(acllist, idx) in selectedStorageItem.cifs_share_acl_list">
      <v-row class="ma-1">
        <v-col cols="3">
          {{ acllist.userOrGroup }}
        </v-col>
        <v-col cols="3" />
        <v-col cols="3">
          {{ acllist.permission }}
        </v-col>
      </v-row>
      <v-divider
        v-if="selectedStorageItem.cifs_share_acl_list!.length > idx + 1"
      />
    </div>
  </common-card>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";

import CommonCard from "@/components/common/CommonCard.vue";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
}>();
</script>
