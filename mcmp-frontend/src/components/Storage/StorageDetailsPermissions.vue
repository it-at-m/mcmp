<template>
  <common-card
    v-if="selectedStorageItem.type == 'NFS'"
    title="Export-Policys"
  >
    <template #toolbar-actions>
      <storage-change-nfs-export-policy
        :storage-uuid="selectedStorageItem.uuid"
        :mount-path="selectedStorageItem.nfs_mount_path!"
      />
    </template>
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
    v-else-if="selectedStorageItem.type == 'CIFS'"
    title="Freigabeberechtigungen"
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
import StorageChangeNfsExportPolicy from "@/components/Storage/StorageChangeNfsExportPolicy.vue";

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
}>();
</script>
