<template>
  <common-card
    v-if="selectedStorageItem.type == 'NFS'"
    title="Export-Policys"
  >
    <template #toolbar-actions>
      <storage-change-nfs-export-policy
        :selected-storage="selectedStorageItem"
      />
    </template>
    <v-row>
      <v-col cols="4">
        <h3>Server</h3>
      </v-col>
      <v-col cols="4">
        <h3>Berechtigungen</h3>
      </v-col>
      <v-col cols="4" />
    </v-row>
    <div
      v-for="(rule, idx) in selectedStorageItem.nfs_export_policy
        ?.ontapExportPolicyRules || []"
      :key="`nfs-export-rule-${idx}-${rule.clients?.join(',')}`"
    >
      <v-row class="ma-1">
        <v-col cols="4">
          {{ rule.clients?.join(", ") }}
        </v-col>
        <v-col cols="4">
          {{ getPermissionLabel(rule.rwRules) }}
        </v-col>
        <v-col
          cols="4"
          class="d-flex justify-end pa-0"
        >
          <storage-change-nfs-export-policy
            v-if="rule.clients && rule.clients.length > 0"
            :selected-storage="selectedStorageItem"
            mode="edit"
            :server-fqdn="rule.clients[0]"
            :permission="getPermissionValue(rule.rwRules)"
          />
        </v-col>
      </v-row>
      <v-divider
        v-if="
          (selectedStorageItem.nfs_export_policy?.ontapExportPolicyRules
            ?.length || 0) >
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
    <div
      v-for="(acllist, idx) in selectedStorageItem.cifs_share_acl_list || []"
      :key="`cifs-acl-${idx}-${acllist.userOrGroup}`"
    >
      <v-row class="ma-1">
        <v-col cols="3">
          {{ acllist.userOrGroup }}
        </v-col>
        <v-col cols="3" />
        <v-col cols="3">
          {{ acllist.permission }}
        </v-col>
        <v-col
          cols="3"
          class="d-flex justify-end pa-0"
        >
          <storage-change-cifs-permissions
            :selected-storage="selectedStorageItem"
            :selected-a-d="acllist.userOrGroup"
            :selected-permission="acllist.permission"
          />
        </v-col>
      </v-row>
      <v-divider
        v-if="(selectedStorageItem.cifs_share_acl_list?.length || 0) > idx + 1"
      />
    </div>
  </common-card>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage";

import CommonCard from "@/components/common/CommonCard.vue";
import StorageChangeCifsPermissions from "@/components/Storage/StorageChangeCifsPermissions.vue";
import StorageChangeNfsExportPolicy from "@/components/Storage/StorageChangeNfsExportPolicy.vue";

defineProps<{
  selectedStorageItem: UnifiedStorageItem;
}>();

function getPermissionValue(rwRules: string | string[] | null | undefined) {
  const values = Array.isArray(rwRules)
    ? rwRules.map((v) => v.toLowerCase())
    : typeof rwRules === "string"
      ? [rwRules.toLowerCase()]
      : [];
  return values.includes("never") ? "ro" : "rw";
}

function getPermissionLabel(rwRules: string | string[] | null | undefined) {
  return getPermissionValue(rwRules) === "ro" ? "read-only" : "read-write";
}
</script>
