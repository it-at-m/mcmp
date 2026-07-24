<template>
  <common-card
    title="Informationen"
    class="links"
  >
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
          {{ formatter.ifEmptyReturnDash(props.selectedAppservice?.name) }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{ formatter.ifEmptyReturnDash(props.selectedAppservice?.number) }}
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          {{
            formatter.ifEmptyReturnDash(props.selectedAppservice!.ownedByName)
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
      <v-col cols="3">
        <h3>
          Anwendungsservice<info-tooltip>
            <div class="pa-1">
              <strong
                >Logische Geschäftsanwendung (Anwendungsservice-Sicht)</strong
              >
              <p class="text-caption mt-2 mb-1">
                Repräsentiert die Gruppierung aller CIs, die für einen Dienst
                zusammenarbeiten.
              </p>
              <ul
                class="mt-2 text-body-2"
                style="padding-left: 1.2rem"
              >
                <li>
                  <strong>Zweck:</strong> Verknüpft die IT-Infrastruktur
                  (Server, DBs) mit einem konkreten Business-Nutzen.
                </li>
                <li>
                  <strong>Impact:</strong> Hilft bei der Analyse, welche Dienste
                  bei einem Serverausfall betroffen sind.
                </li>
                <li>
                  <strong>Eigentumsverantwortung:</strong> Hier sind
                  Verantwortlichkeiten (Owner), Support-Gruppen und SLAs
                  hinterlegt.
                </li>
              </ul>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>
          Anwendungsservice Map<info-tooltip>
            <div class="pa-1">
              <strong>Service-Abhängigkeiten (Top-Down Map)</strong>
              <p class="text-caption mt-2 mb-1">
                Visualisiert die hierarchische Struktur und Beziehungen des
                Dienstes.
              </p>
              <ul
                class="mt-2 text-body-2"
                style="padding-left: 1.2rem"
              >
                <li>
                  <strong>Visualisierung:</strong> Grafische Darstellung aller
                  Komponenten (Server, DBs, LB), die den Service bilden.
                </li>
                <li>
                  <strong>Beziehungen:</strong> Zeigt direkt, welche
                  Infrastruktur-CIs miteinander kommunizieren oder voneinander
                  abhängen.
                </li>
                <li>
                  <strong>Fehlersuche:</strong> Ideal zur Identifikation von
                  "Single Points of Failure" innerhalb der Service-Architektur.
                </li>
              </ul>
            </div>
          </info-tooltip>
        </h3>
      </v-col>
      <v-col cols="3">
        <h3>Change-Gruppe</h3>
      </v-col>
    </v-row>
    <v-row>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          <a
            v-if="props.selectedAppservice?.sysId"
            :href="`https://it-services.muenchen.de/now/workspace/dpm/list/params/list-id/5b35b707eb7501108684f8bdb552283d/sub/service-details/cmdb_ci_service_auto/${props.selectedAppservice.sysId}`"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ props.selectedAppservice?.name }}
          </a>
          <span v-else>-</span>
        </p>
      </v-col>
      <v-col
        cols="3"
        class="pt-0"
      >
        <p>
          <a
            v-if="props.selectedAppservice?.sysId"
            :href="`https://it-services.muenchen.de/now/sgw/record/cmdb_ci_service/${props.selectedAppservice.sysId}/sub/unifiedmap/params/root-node/${props.selectedAppservice.sysId}/`"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ props.selectedAppservice.name }}
          </a>
          <span v-else>-</span>
        </p>
      </v-col>
      <v-col
        cols="3"
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
        <div class="d-flex align-center">
          <span class="me-2">
            {{
              formatter.formatBooleanToGerman(
                props.selectedAppservice?.cswEnforced
              )
            }}
          </span>
          <v-icon
            v-if="props.selectedAppservice?.cswEnforced"
            :icon="mdiCheckCircle"
            color="_green"
          />
          <v-tooltip
            v-else-if="props.selectedAppservice?.cswEnforced === false"
            text="Information und Aktivierung"
            location="top"
          >
            <template #activator="{ props: tooltipProps }">
              <a
                href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=e80a43b73b2d3e90adb4352a85e45a72"
                target="_blank"
                rel="noopener noreferrer"
                v-bind="tooltipProps"
                class="d-inline-flex align-center"
              >
                <v-icon
                  :icon="mdiAlertCircle"
                  color="grey"
                />
              </a>
            </template>
          </v-tooltip>
        </div>
      </v-col>
    </v-row>
  </common-card>
</template>

<script setup lang="ts">
import type Appservice from "@/types/Appservice.ts";

import { mdiAlertCircle, mdiCheckCircle } from "@mdi/js";

import CommonCard from "@/components/common/CommonCard.vue";
import InfoTooltip from "@/components/common/InfoTooltip.vue";
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
