<template>
  <common-card
    v-show="selectedServer.cloud?.cloudType == 'VCENTER'"
    title="Virtuelle Festplatten"
  >
    <v-data-table
      :headers="headers"
      :items="disks"
      :items-per-page="-1"
      :loading="loading[0]"
      class="elevation-1"
      hide-default-footer
      disable-sort
    >
      <template #item.capacityInBytes="{ item }">
        {{ formatter.formatBtoGB(item.capacityInBytes) }} GB
      </template>
    </v-data-table>
  </common-card>
  <common-card
    title="Laufwerke"
    top-margin="0"
    is-expansion-panel
  >
    <template #toolbar-actions>
      <edit-mountpoint
        v-if="
          selectedServer.guestToolsFullName?.includes('Linux 10') &&
          selectedServer.managed &&
          selectedServer.canEdit &&
          selectedServer.cloud?.cloudType == 'VCENTER'
        "
        :mount-points="[]"
        :selected-server="props.selectedServer"
        :snapshot-on-server="snapshots.length > 0"
        :new-mountpoint="true"
        @save="editMountPoint"
      />
      <edit-mountpoint
        v-if="
          selectedServer.managed &&
          selectedServer.canEdit &&
          selectedServer.cloud?.cloudType == 'VCENTER'
        "
        :mount-points="props.mountPoints"
        :selected-server="props.selectedServer"
        :snapshot-on-server="snapshots.length > 0"
        :new-mountpoint="false"
        @save="editMountPoint"
      />
    </template>
    <v-data-table
      :headers="mountHeaders"
      :items="mountPoints"
      :items-per-page="-1"
      :sort-by="[{ key: 'diskPath', order: 'asc' }]"
      :loading="loading[1]"
      class="elevation-1"
      hide-default-footer
    >
      <template #item.capacityInBytes="{ item }">
        {{ formatter.formatBytesSmart(item.capacityInBytes) }}
      </template>
      <template #item.freeSpaceInBytes="{ item }">
        {{ formatter.formatBytesSmart(item.freeSpaceInBytes) }}
      </template>
      <template #item.actions="{ item }">
        <linear-progress-with-colors
          :value="
            ((item.capacityInBytes - item.freeSpaceInBytes) /
              item.capacityInBytes) *
            100
          "
          :show-percentage="true"
        />
      </template>
      <template #item.editable="{ item }">
        <v-tooltip
          v-if="!item.editable"
          text="Die Größe für dieses Laufwerk kann nicht angepasst werden!"
          location="top"
        >
          <template #activator="{ props }">
            <v-icon
              v-bind="props"
              :icon="mdiLock"
              size="small"
            />
          </template>
        </v-tooltip>
      </template>
      <template #no-data>
        <v-row>
          <v-col> Keine Laufwerke gefunden. </v-col>
        </v-row>
      </template>
    </v-data-table>
  </common-card>
  <common-card
    title="Netzlaufwerke"
    top-margin="0"
    is-expansion-panel
  >
    <v-data-table
      :headers="shareMountHeaders"
      :items="shareMountPoints"
      :items-per-page="-1"
      :sort-by="[{ key: 'diskPath', order: 'asc' }]"
      :loading="loading[1]"
      class="elevation-1 links"
      hide-default-footer
    >
      <template #item.mountPoint="{ item }">
        <router-link
          v-if="item.uuid && item.type"
          :to="`/storage/${item.type}/${item.uuid}`"
        >
          {{ item.mountPoint }}
        </router-link>
        <template v-else>
          {{ item.mountPoint }}
        </template>
      </template>
      <template #item.size="{ item }">
        {{ formatter.formatBytesSmart(item.size) }}
      </template>
      <template #item.free="{ item }">
        {{ formatter.formatBytesSmart(item.size - item.used) }}
      </template>
      <template #item.used="{ item }">
        <linear-progress-with-colors
          :value="(item.used / item.size) * 100"
          :show-percentage="true"
        />
      </template>
      <template #no-data>
        <v-row>
          <v-col> Keine Netzlaufwerke gefunden. </v-col>
        </v-row>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Disk from "@/types/Disk";
import type MountPoint from "@/types/MountPoint";
import type Snapshot from "@/types/Snapshot.ts";
import type { UnifiedStorageMountItem } from "@/types/UnifiedStorageMountItem.ts";

import { mdiLock } from "@mdi/js";
import { ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import LinearProgressWithColors from "@/components/common/LinearProgressWithColors.vue";
import { useFormatter } from "@/composables/formatter";
import Server from "@/types/Server";
import EditMountpoint from "./EditMountpoint.vue";

const jobLoading = ref(true);

const props = defineProps<{
  selectedServer: Server;
  disks: Disk[];
  mountPoints: MountPoint[];
  shareMountPoints: UnifiedStorageMountItem[];
  loading: boolean[];
  snapshots: Snapshot[];
}>();

const headers = [
  { title: "Device", key: "device", width: 50, align: "start" },
  { title: "Größe", key: "capacityInBytes", width: "64%", align: "center" },
];

const mountHeaders = [
  {
    title: "Pfad",
    key: "diskPath",
    width: 140,
    align: "start",
    maxWidth: 140,
    minWidth: 140,
    sortable: true,
  },
  {
    title: "Größe",
    key: "capacityInBytes",
    width: 20,
    align: "center",
    maxWidth: 20,
    minWidth: 20,
    sortable: false,
  },
  {
    title: "frei",
    key: "freeSpaceInBytes",
    width: 20,
    align: "center",
    maxWidth: 20,
    minWidth: 20,
    sortable: false,
  },
  {
    title: "% belegt",
    key: "actions",
    sortable: false,
    width: 40,
    align: "center",
    maxWidth: 40,
    minWidth: 40,
  },
  {
    title: "",
    key: "editable",
    sortable: false,
    width: "1%",
    align: "center",
  },
];

const shareMountHeaders = [
  {
    title: "Pfad",
    key: "mountPoint",
    width: 140,
    align: "start",
    maxWidth: 140,
    minWidth: 140,
    sortable: true,
  },
  {
    title: "Größe",
    key: "size",
    width: 20,
    align: "center",
    maxWidth: 20,
    minWidth: 20,
    sortable: false,
  },
  {
    title: "frei",
    key: "free",
    width: 20,
    align: "center",
    maxWidth: 20,
    minWidth: 20,
    sortable: false,
  },
  {
    title: "% belegt",
    key: "used",
    sortable: false,
    width: 40,
    align: "center",
    maxWidth: 40,
    minWidth: 40,
  },
];

const formatter = useFormatter();

function editMountPoint(
  mountPoint: MountPoint,
  newCapacityGB: number,
  newVolumeGroup: string
) {
  if (
    props.selectedServer?.guestConfigFullName?.toLowerCase().includes("linux")
  ) {
    jobService.startJob(
      jobLoading,
      "LINUX_MOUNTPOINT_CHANGE",
      props.selectedServer.id,
      {
        mountPoint: mountPoint.diskPath,
        newSize: newCapacityGB,
        volumeGroup: newVolumeGroup,
      }
    );
  }
  if (
    props.selectedServer?.guestConfigFullName?.toLowerCase().includes("windows")
  ) {
    jobService.startJob(
      jobLoading,
      "WINDOWS_PARTITION_CHANGE",
      props.selectedServer.id,
      {
        partition: mountPoint.diskPath,
        newSize: newCapacityGB,
      }
    );
  }
}
</script>
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  /* noinspection CssUnresolvedCustomProperty */
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
