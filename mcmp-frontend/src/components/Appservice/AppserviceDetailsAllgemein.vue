<template>
      <common-card title="Informationen">
          <v-row>
            <v-col cols="3">
              <h3>Name</h3>
            </v-col>
            <v-col cols="3">
              <h3>Nummer</h3>
            </v-col>
            <v-col cols="3">
              <h3>Eigentum von</h3>
            </v-col>
            <v-col cols="3">
              <h3>Delegierter</h3>
            </v-col>
          </v-row>
          <v-row>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(props.selectedAppservice?.name)
                }}
              </p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(props.selectedAppservice?.number)
                }}
              </p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(
                    props.selectedAppservice!.ownedByName
                  )
                }}
              </p>
            </v-col>
            <v-col
              cols="3"
              class="pt-0"
            >
              <p>
                {{
                  formatter.ifEmptyReturnDash(
                    props.selectedAppservice!.serviceOwnerDelegateName
                  )
                }}
              </p>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="6">
              <h3>CMDB</h3>
            </v-col>
            <v-col cols="6">
              <h3>Change-Gruppe</h3>
            </v-col>
          </v-row>
          <v-row>
            <v-col
              cols="6"
              class="pt-0"
            >
              <p>
                <a
                  v-if="props.selectedAppservice?.sysId"
                  :href="`https://it-services.muenchen.de/nav_to.do?uri=cmdb_ci_service_discovered.do?sys_id=${props.selectedAppservice.sysId}%26sysparm_view=EAM`"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ props.selectedAppservice?.name }}
                </a>
                <span v-else>-</span>
              </p>
            </v-col>
            <v-col
              cols="6"
              class="pt-0"
            >
              <p>
                <a
                  v-if="props.selectedAppservice?.sysId"
                  :href="`https://it-services.muenchen.de/now/sgw/record/sys_user_group/${props.selectedAppservice.changeGroupSysId}/`"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {{ props.selectedAppservice?.changeGroupName }}
                </a>
                <span v-else>-</span>
              </p>
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="3">
              <h3>Mikrosegmentiert</h3>
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
                    props.selectedAppservice?.cswEnforced
                  )
                }}
              </p>
            </v-col>
          </v-row>
      </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";

import CommonCard from "@/components/common/CommonCard.vue";
import { useFormatter } from "@/composables/formatter.ts";

const props = defineProps<{
  selectedAppservice: Appservice | null;
}>();

const formatter = useFormatter();
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
