<template>
  <common-card
    v-if="props.selectedServer.serverKind === 'HARDWARE'"
    title="Hardware Informationen"
  >
    <v-row>
      <v-col cols="3">
        <h3>Hersteller</h3>
      </v-col>
      <v-col cols="3">
        <h3>Modell</h3>
      </v-col>
      <v-col cols="3">
        <h3>Seriennummer</h3>
      </v-col>
      <v-col cols="3">
        <h3>Herstellungsdatum</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formatter.ifEmptyReturnDash(props.selectedServer.vendor) }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formatter.ifEmptyReturnDash(props.selectedServer.model) }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ formatter.ifEmptyReturnDash(props.selectedServer.uuid) }}</p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.ifEmptyReturnDash(
              formatter.formatToBerlinDate(props.selectedServer.mfgTime)
            )
          }}
        </p>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    v-if="props.selectedServer.serverKind === 'HARDWARE'"
    title="Administration"
  >
    <v-row>
      <v-col cols="3">
        <h3>
          <template
            v-if="props.selectedServer.cloud?.cloudType === 'UCS_MANAGER'"
          >
            UCSM<info-tooltip text="Cisco UCS Manager" />
          </template>
          <template
            v-else-if="props.selectedServer.cloud?.cloudType === 'UCS_CIMC'"
          >
            CIMC<info-tooltip text="Cisco Integrated Management Controller" />
          </template>
        </h3>
      </v-col>
      <v-col
        v-if="props.selectedServer.cloud?.cloudType === 'UCS_MANAGER'"
        cols="3"
      >
        <h3>Einbauort</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0 links"
      >
        <p v-if="!props.selectedServer.cloud?.fqdn">
          {{ formatter.ifEmptyReturnDash(props.selectedServer.cloud?.fqdn) }}
        </p>
        <div v-else>
          <a
            :href="'https://' + props.selectedServer.cloud?.fqdn"
            target="_blank"
            rel="noopener noreferrer"
            >{{ props.selectedServer.cloud?.fqdn }}</a
          >
          <info-tooltip text="Nur im Admin-Netz aufrufbar!" />
        </div>
      </v-col>
      <v-col
        v-if="props.selectedServer.cloud?.cloudType === 'UCS_MANAGER'"
        cols="3"
        class="pt-0"
      >
        <template v-if="props.selectedServer.serverType === 'CISCO_BLADE'">
          Chassis
          {{ formatter.ifEmptyReturnDash(props.selectedServer.ucsmChassisId) }}
          / Slot
          {{
            formatter.ifEmptyReturnDash(props.selectedServer.ucsmChassisSlotId)
          }}
        </template>
        <template
          v-else-if="props.selectedServer.serverType === 'CISCO_RACK_UNIT'"
        >
          RackUnit
          {{ formatter.ifEmptyReturnDash(props.selectedServer.ucsmServerId) }}
        </template>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    v-if="props.selectedServer.serverKind === 'VIRTUAL'"
    title="Host Informationen"
  >
    <v-row>
      <v-col cols="3">
        <h3>Rechenzentrum</h3>
      </v-col>
      <v-col cols="3">
        <h3>Datacenter</h3>
      </v-col>
      <v-col cols="3">
        <h3>Cluster</h3>
      </v-col>
      <v-col cols="3">
        <h3>Host</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            props.selectedServer.location == "A20"
              ? "Agnes-Pockels-Bogen 20, 80992 München"
              : props.selectedServer.location == "K30"
                ? "Klausnerstraße 30, 85609 Aschheim"
                : props.selectedServer.location == "F40"
                  ? "Friedenstraße 40, 81671 München"
                  : "-"
          }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ formatter.ifEmptyReturnDash(props.selectedServer.cloud?.name) }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ formatter.ifEmptyReturnDash(props.selectedServer.cluster) }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ formatter.ifEmptyReturnDash(props.selectedServer.host) }}
        </p>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    v-if="props.selectedServer.serverType === 'VM_VMWARE'"
    title="Technische Informationen"
    top-margin="0"
  >
    <v-row>
      <v-col cols="3">
        <h3>Kernel-Version</h3>
      </v-col>
      <v-col cols="3">
        <h3>VMX-Version</h3>
      </v-col>
      <v-col cols="3">
        <h3>VMware Tools</h3>
      </v-col>
      <v-col cols="3">
        <h3>VMware Tools Version</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.ifEmptyReturnDash(
              props.selectedServer.guestToolsKernelVersion
            )
          }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <v-row class="ml-0 mt-0">
          <p>{{ props.selectedServer.vmxVersion }}</p>
          <v-tooltip
            v-if="Number(props.selectedServer.vmxVersion) > VMX_VERSION_MINIMUM"
            text="Version aktuell"
          >
            <template #activator="{ props: tooltipProps }">
              <v-icon
                v-bind="tooltipProps"
                color="_green"
                >{{ mdiCheckCircle }}</v-icon
              >
            </template>
          </v-tooltip>
          <v-tooltip
            v-else
            text="Version veraltet"
          >
            <template #activator="{ props: tooltipProps }">
              <v-icon
                v-bind="tooltipProps"
                color="_red"
                >{{ mdiAlertCircle }}</v-icon
              >
            </template>
          </v-tooltip>
        </v-row>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p
          v-if="
            props.selectedServer.guestToolsRunningStatus === 'guestToolsRunning'
          "
        >
          Eingeschaltet
        </p>
        <p v-else>
          Ausgeschaltet <v-icon color="_red">{{ mdiAlertCircle }}</v-icon>
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        {{
          formatter.ifEmptyReturnDash(props.selectedServer.guestToolsVersion)
        }}
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="3">
        <h3>CPU-Topology</h3>
      </v-col>
      <v-col cols="3">
        <h3>CPU Hot Add</h3>
      </v-col>
      <v-col cols="3">
        <h3>Memory Hot Add</h3>
      </v-col>
      <v-col cols="3">
        <h3>Uptime</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ cpuTopology }}</p>
        <v-icon
          v-if="props.selectedServer.cpuTopology !== 'Assigned at power on'"
          color="_red"
          >{{ mdiAlertCircle }}</v-icon
        >
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatBooleanToGerman(
              props.selectedServer.cpuHotAddEnabled
            )
          }}
        </p>
        <v-icon
          v-if="!props.selectedServer.cpuHotAddEnabled"
          color="_red"
          >{{ mdiAlertCircle }}</v-icon
        >
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatBooleanToGerman(
              props.selectedServer.memoryHotAddEnabled
            )
          }}
        </p>
        <v-icon
          v-if="!props.selectedServer.memoryHotAddEnabled"
          color="_red"
          >{{ mdiAlertCircle }}</v-icon
        >
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ uptime }}</p>
      </v-col>
    </v-row>
  </common-card>
  <common-card
    v-if="props.selectedServer.serverType === 'VM_PROXMOX'"
    title="Technische Informationen"
    top-margin="0"
  >
    <v-row>
      <v-col cols="3">
        <h3>Machine Version</h3>
      </v-col>
      <v-col cols="3">
        <h3>qemu Guest Agent</h3>
      </v-col>
      <v-col cols="3">
        <h3>qemu Guest Agent Version</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <v-row class="ml-0 mt-0">
          <p>{{ props.selectedServer.vmxVersion }}</p>
          <v-tooltip
            v-if="Number(props.selectedServer.vmxVersion) > VMX_VERSION_MINIMUM"
            text="Version aktuell"
          >
            <template #activator="{ props: tooltipProps }">
              <v-icon
                v-bind="tooltipProps"
                color="_green"
                >{{ mdiCheckCircle }}</v-icon
              >
            </template>
          </v-tooltip>
          <v-tooltip
            v-else
            text="Version veraltet"
          >
            <template #activator="{ props: tooltipProps }">
              <v-icon
                v-bind="tooltipProps"
                color="_red"
                >{{ mdiAlertCircle }}</v-icon
              >
            </template>
          </v-tooltip>
        </v-row>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p
          v-if="
            props.selectedServer.guestToolsRunningStatus === 'guestToolsRunning'
          "
        >
          Eingeschaltet
        </p>
        <p v-else>
          Ausgeschaltet <v-icon color="_red">{{ mdiAlertCircle }}</v-icon>
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        {{
          formatter.ifEmptyReturnDash(props.selectedServer.guestToolsVersion)
        }}
      </v-col>
    </v-row>
    <v-row>>
      <v-col cols="3">
        <h3>CPU Hot Add</h3>
      </v-col>
      <v-col cols="3">
        <h3>Memory Hot Add</h3>
      </v-col>
      <v-col cols="3">
        <h3>Uptime</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatBooleanToGerman(
              props.selectedServer.cpuHotAddEnabled
            )
          }}
        </p>
        <v-icon
          v-if="!props.selectedServer.cpuHotAddEnabled"
          color="_red"
          >{{ mdiAlertCircle }}</v-icon
        >
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.formatBooleanToGerman(
              props.selectedServer.memoryHotAddEnabled
            )
          }}
        </p>
        <v-icon
          v-if="!props.selectedServer.memoryHotAddEnabled"
          color="_red"
          >{{ mdiAlertCircle }}</v-icon
        >
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>{{ uptime }}</p>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import { mdiAlertCircle, mdiCheckCircle } from "@mdi/js";
import { computed } from "vue";

import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
import { useFormatter } from "@/composables/formatter.js";
import Server from "@/types/Server";

const uptime = computed(() => {
  const bootTime = props.selectedServer.bootTime;
  if (!bootTime) return "-";
  const bootDate = new Date(bootTime);
  const now = new Date();
  let diff = Math.floor((now.getTime() - bootDate.getTime()) / 1000);

  const days = Math.floor(diff / (3600 * 24));
  diff -= days * 3600 * 24;
  const hours = Math.floor(diff / 3600);
  diff -= hours * 3600;
  const minutes = Math.floor(diff / 60);

  let result = "";
  if (days > 0) result += `${days}d `;
  if (hours > 0 || days > 0) result += `${hours}h `;
  result += `${minutes}min`;
  return result.trim();
});

const formatter = useFormatter();

const VMX_VERSION_MINIMUM = 20;

const props = defineProps<{
  selectedServer: Server;
}>();

const cpuTopology = computed(() => {
  if (props.selectedServer.cpuTopology) {
    return props.selectedServer.cpuTopology === "Assigned at power on"
      ? "Zugewiesen beim Einschalten"
      : props.selectedServer.cpuTopology;
  } else {
    return "Keine CPU-Topology";
  }
});
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.links a,
.links a:visited,
.links a:hover,
.links a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}
</style>
