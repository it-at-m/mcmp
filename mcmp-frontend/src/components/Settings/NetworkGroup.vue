<template>
  <common-card title="Netzwerk/Netzwerkgruppen">
    <template #toolbar-actions>
      <v-btn
        icon
        :loading="loading"
        aria-label="Netzwerkgruppen aktualisieren"
        @click="refreshNetworkGroups"
      >
        <v-icon>{{ mdiReload }}</v-icon>
      </v-btn>
    </template>
    <v-chip
      v-if="!isNetworkAdmin"
      color="btn_red"
      class="mb-4"
    >
      Read Only! Nur Netzwerk-Admins können Netzwerkgruppen verwalten
    </v-chip>
    <v-data-table
      :headers="headers"
      :items="networkGroups"
      class="elevation-1"
      :items-per-page="-1"
      item-value="id"
      no-data-text="Keine Netzwerkgruppen gefunden"
      :sort-by="sortBy"
      show-expand
      :expanded="expanded"
    >
      <template #item="{ item }">
        <tr
          @drop="handleDrop(item.id!, $event)"
          @dragover.prevent
        >
          <td>{{ item.name }}</td>
          <td>{{ item.environment || "-" }}</td>
          <td>
            <template
              v-if="
                item.application ||
                item.database ||
                item.storage ||
                item.restrict
              "
            >
              <v-chip
                v-if="item.application"
                size="small"
                class="mr-1 mb-1"
                color="primary"
              >
                Application
              </v-chip>
              <v-chip
                v-if="item.database"
                size="small"
                class="mr-1 mb-1"
                color="primary"
              >
                Database
              </v-chip>
              <v-chip
                v-if="item.storage"
                size="small"
                class="mr-1 mb-1"
                color="primary"
              >
                Storage
              </v-chip>
              <v-chip
                v-if="item.restrict"
                size="small"
                class="mr-1 mb-1"
                color="error"
              >
                Restrict
              </v-chip>
            </template>
            <span
              v-else
              class="text-grey"
              >Kein Typ zugewiesen</span
            >
          </td>
          <td class="appservices-column">
            <v-col v-if="item.restrict">
              <div class="appservices-container">
                <template v-if="item.appservices.length > 0">
                  <v-chip
                    v-for="app in item.appservices"
                    :key="app.id"
                    size="small"
                    color="primary"
                    variant="outlined"
                    class="mb-1 mr-1"
                  >
                    {{ app.name }}
                  </v-chip>
                </template>
                <span
                  v-else
                  class="text-grey mr-2"
                >
                  Keine Applikationsservices zugewiesen
                </span>
                <network-group-appservice-assignment
                  v-if="isNetworkAdmin"
                  :networkgroup="item"
                  class="ml-1"
                  @save="assingAppservicesToNetworkgroup"
                />
              </div>
            </v-col>
          </td>
          <td>
            <v-btn
              text
              :aria-label="
                expanded.includes(item.id!)
                  ? `Netzwerke für ${item.name} einklappen`
                  : `Netzwerke für ${item.name} ausklappen`
              "
              @click.stop="toggleExpand(item.id!)"
            >
              <v-icon>
                {{
                  expanded.includes(item.id!) ? mdiChevronUp : mdiChevronDown
                }}
              </v-icon>
            </v-btn>
          </td>
        </tr>
      </template>
      <template #expanded-row="{ item }">
        <td
          :colspan="headers.length + 1"
          class="expanded-row-td"
        >
          <v-row
            class="expanded-row-content"
            dense
            @drop="handleDrop(item.id!, $event)"
            @dragover.prevent
          >
            <v-col
              v-for="network in networksForGroup(item.id!)"
              :key="network.id"
              cols="12"
              md="4"
            >
              <v-card
                class="mt-4 mb-0"
                elevation="2"
              >
                <v-card-title
                  style="
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                  "
                >
                  <span>{{ network.name }}</span>
                  <v-btn
                    text
                    :aria-label="
                      expandedNetworks.includes(network.id?.toString() ?? '')
                        ? `Details für Netzwerk ${network.name} einklappen`
                        : `Details für Netzwerk ${network.name} ausklappen`
                    "
                    @click.stop="toggleNetworkExpand(network.id)"
                  >
                    <v-icon>
                      {{
                        expandedNetworks.includes(network.id?.toString() ?? "")
                          ? mdiChevronUp
                          : mdiChevronDown
                      }}
                    </v-icon>
                  </v-btn>
                </v-card-title>
                <v-card-text>
                  <div class="network-fields-grid">
                    <template
                      v-for="field in networkFields"
                      :key="field.key"
                    >
                      <div class="network-field-label">
                        <strong>{{ field.label }}:</strong>
                      </div>
                      <div class="network-field-value">
                        {{ field.getValue(network) }}
                      </div>
                    </template>

                    <!-- Erweiterte Felder ohne Trennstrich -->
                    <v-expand-transition>
                      <div
                        v-if="
                          expandedNetworks.includes(
                            network.id?.toString() ?? ''
                          )
                        "
                        class="expanded-fields"
                      >
                        <div class="network-field-label">
                          <strong>IP-Adresse:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.ipAddress }}
                        </div>

                        <div class="network-field-label">
                          <strong>Netmask:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.netmask }}
                        </div>

                        <div class="network-field-label">
                          <strong>Gateway:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.gateway }}
                        </div>

                        <div class="network-field-label">
                          <strong>Broadcast:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.broadcast }}
                        </div>

                        <div class="network-field-label">
                          <strong>DNS Primary:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.dnsPrimary }}
                        </div>

                        <div class="network-field-label">
                          <strong>DNS Secondary:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.dnsSecondary }}
                        </div>
                        <div class="network-field-label">
                          <strong>Typ:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.networktyp }}
                        </div>
                        <div class="network-field-label">
                          <strong>MCMP-Typ:</strong>
                        </div>
                        <div class="network-field-value">
                          {{ network.mcmpNetworkTyp }}
                        </div>
                      </div>
                    </v-expand-transition>
                  </div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </td>
      </template>
    </v-data-table>
  </common-card>
</template>

<script setup lang="ts">
import type Network from "@/types/Network.ts";
import type NetworkGroup from "@/types/NetworkGroup.ts";
import type { DataTableHeader } from "vuetify/framework";

import { mdiChevronDown, mdiChevronUp, mdiReload } from "@mdi/js";
import { computed, onMounted, ref } from "vue";

import networkService from "@/api/networkService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import NetworkGroupAppserviceAssignment from "@/components/Settings/NetworkGroupAppserviceAssignment.vue";
import { useUserStore } from "@/stores/user.ts";

const sortBy = ref([{ key: "name", order: "asc" as "asc" | "desc" }]);
const loading = ref(true);
const networkGroups = ref<NetworkGroup[]>();
const networks = ref<Network[]>();
const userStore = useUserStore();
const isNetworkAdmin = computed(() =>
  userStore.getUser?.authorities.includes("ROLE_NETWORK")
);
const networkFields = [
  { key: "infoblox", label: "Infoblox", getValue: (n: Network) => n.infoblox },
  { key: "cidr", label: "CIDR", getValue: (n: Network) => n.cidr },
  {
    key: "environment",
    label: "Klassifizierung",
    getValue: (n: Network) => n.environment,
  },
  { key: "vlan", label: "VLAN", getValue: (n: Network) => n.vlan },
  { key: "comment", label: "Comment", getValue: (n: Network) => n.comment },
  { key: "referat", label: "Referat", getValue: (n: Network) => n.referat },
];

const expanded = ref<number[]>([]);

function toggleExpand(id: number) {
  const idx = expanded.value.indexOf(id);
  if (idx === -1) expanded.value.push(id);
  else expanded.value.splice(idx, 1);
}

const expandedNetworks = ref<string[]>([]);

function toggleNetworkExpand(id: number | undefined) {
  if (id == null) return;
  const idStr = id.toString();
  const idx = expandedNetworks.value.indexOf(idStr);
  if (idx === -1) expandedNetworks.value.push(idStr);
  else expandedNetworks.value.splice(idx, 1);
}

const draggedNetwork = ref<Network | null>(null);
const confirmDialog = ref(false);
const dropSource = ref<NetworkGroup | null>(null);
const dropTarget = ref<NetworkGroup | null>(null);
const pendingNetwork = ref<Network | null>(null);

onMounted(() => {
  getNetworkGroups();
  getNetworks();
});

const headers = ref<DataTableHeader[]>([
  { title: "Name", key: "name", width: "1%" },
  { title: "Klassifizierung", key: "environment", width: "1%" },
  { title: "Typ", key: "type", width: "1%" },
  { title: "Applikationsservices", key: "appservices" }, // keine width Angabe
  {
    title: "Erweitern",
    sortable: false,
    width: "1%",
  },
]);

function networksForGroup(groupId: number) {
  return networks.value?.filter((n) => n.networkGroupId === groupId) ?? [];
}

function handleDrop(groupId: number | null, event: Event) {
  event.preventDefault();
  if (draggedNetwork.value) {
    const sourceGroup =
      networkGroups.value?.find(
        (g) => g.id === draggedNetwork.value!.networkGroupId
      ) ?? null;
    const targetGroup =
      networkGroups.value?.find((g) => g.id === groupId) ?? null;

    // Keine Aktion, wenn Quelle und Ziel gleich sind
    if ((sourceGroup?.id ?? null) === (targetGroup?.id ?? null)) {
      draggedNetwork.value = null;
      return;
    }

    dropSource.value = sourceGroup;
    dropTarget.value = targetGroup;
    pendingNetwork.value = draggedNetwork.value;
    confirmDialog.value = true;
  }
}

function assingAppservicesToNetworkgroup(
  networkGroup: NetworkGroup,
  appservices: number[]
) {
  networkService
    .assignAppservicesToGroup(loading, networkGroup.id!, appservices)
    .then(() => {
      getNetworkGroups();
    });
}

function getNetworkGroups() {
  networkService.getNetworkGroups(loading).then((data) => {
    networkGroups.value = data;
  });
}

function getNetworks() {
  networkService.getNetworks(loading).then((data) => {
    networks.value = data;
    networks.value.sort((a, b) => a.name.localeCompare(b.name));
  });
}

function refreshNetworkGroups() {
  getNetworkGroups();
  getNetworks();
}
</script>

<style>
.expanded-row-content {
  width: 100%;
  box-sizing: border-box;
  padding: 0 16px 16px 16px;
}

.expanded-row-td {
  padding: 0 !important;
  border: none;
  background: transparent;
}

.network-fields-grid {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 4px 16px;
  align-items: start;
}

.network-field-label {
  justify-self: end;
  text-align: right;
}

.network-field-value {
  word-break: break-word;
}

.expanded-fields {
  display: contents;
  grid-column: 1 / -1;
}

/* Neue Styles für die Appservices-Spalte */
.appservices-column {
  max-width: 300px;
  width: 300px;
}

.appservices-container {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  overflow: hidden;
}
</style>
