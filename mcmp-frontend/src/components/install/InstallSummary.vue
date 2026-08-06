<template>
  <v-container>
    <common-alert is-snow-change />
    <v-checkbox
      v-if="
        userStore.getUser?.authorities?.includes('ROLE_LINUX') &&
        instlServerDetails.osType == OsType.Linux
      "
      v-model="instlServerDetails.isLinuxCustom"
      label="Custom Linux Server (nur nach absprache mit Andy/Sebi verwenden!)"
    ></v-checkbox>
    <div v-if="!instlServerDetails.isLinuxCustom">
      <br />
      <h2>Allgemein</h2>
      <v-divider />
      <br />
      <v-row>
        <v-col><strong>Anwendungsservice:</strong></v-col>
        <v-col>{{ instlServerDetails.appservice?.name }}</v-col>
      </v-row>
      <v-row>
        <v-col><strong>Vorausichtlicher Servername:</strong></v-col>
        <v-col>{{ instlServerDetails.expectedServerName }}</v-col>
      </v-row>
      <v-row>
        <v-col><strong>Betriebssystem:</strong></v-col>
        <v-col>{{ instlServerDetails.osVersion }}</v-col>
      </v-row>
      <v-row>
        <v-col><strong>Server Typ:</strong></v-col>
        <v-col>{{ instlServerDetails.categoryType }}</v-col>
      </v-row>
      <v-row>
        <v-col><strong>Server Kategorie:</strong></v-col>
        <v-col>{{ instlServerDetails.category?.label }}</v-col>
      </v-row>
      <v-row
        v-if="
          (instlServerDetails.categoryType == categoryType.DB ||
            instlServerDetails.categoryType == categoryType.Mixed) &&
          !instlServerDetails.category?.label.match(/PostgreSQL/)
        "
      >
        <v-col
          ><strong>Begründung für nicht Postgres Bestellung:</strong></v-col
        >
        <v-col>{{
          instlServerDetails.nonPostgresReason || "Keine Angabe"
        }}</v-col>
      </v-row>

      <div
        v-if="
          instlServerDetails.categoryType == categoryType.DB ||
          instlServerDetails.categoryType == categoryType.Mixed
        "
      >
        <br />
        <h2>Datenbank</h2>
        <v-divider />
        <br />
        <v-row>
          <v-col><strong>Datenbank Kategorie:</strong></v-col>
          <v-col v-if="instlServerDetails.categoryType == categoryType.DB">{{
            instlServerDetails.category?.label
          }}</v-col>
          <v-col
            v-else-if="instlServerDetails.categoryType == categoryType.Mixed"
            >{{ instlServerDetails.category?.label.split(/\+/)[1] }}</v-col
          >
        </v-row>
        <div
          v-for="(value, key) in instlServerDetails.dbParams
            ?.mariaPostgresMysqlOracle"
        >
          <v-row>
            <v-col
              ><strong>{{ key }}:</strong></v-col
            >
            <v-col>{{ value }}</v-col>
          </v-row>
        </div>
      </div>

      <br />

      <h2>Hardware</h2>
      <v-divider />
      <br />
      <v-row>
        <v-col><strong>CPU:</strong></v-col>
        <v-col>{{ instlServerDetails.cpu }}</v-col>
      </v-row>
      <v-row>
        <v-col><strong>Arbeitsspeicher:</strong></v-col>
        <v-col>{{ instlServerDetails.memory }} GB</v-col>
      </v-row>
      <v-row v-if="instlServerDetails.osType == OsType.Windows">
        <v-col><strong>Festplattenspeicher:</strong></v-col>
        <v-col>
          <div
            v-for="diskConf in instlServerDetails.disk[
              instlServerDetails.osType
            ]?.[instlServerDetails.categoryType!]"
          >
            {{ diskConf.label }}: {{ diskConf.size }} GB
          </div>
          <br
            v-if="
              instlServerDetails.disk[instlServerDetails.osType]?.[
                instlServerDetails.categoryType!
              ]?.length > 1
            "
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col><strong>Netzwerkgruppe:</strong></v-col>
        <v-col>{{ instlServerDetails.networkGroup?.name }}</v-col>
      </v-row>
    </div>
    <div v-else-if="!loading">
      <p>Entfernen des Häkchens resettet das JSON!</p>
      <br />
      <h2>Linux Custom Konfiguration</h2>
      <v-divider />
      <br />
      <v-textarea
        v-model="instlServerDetails.linuxCustomExtraVars"
        label="JSON Konfiguration bearbeiten"
        variant="outlined"
        auto-grow
        rows="15"
        style="font-family: monospace"
        :error-messages="jsonError"
      ></v-textarea>
    </div>
    <div v-if="instlServerDetails.schedule">
      <br />
      <v-divider />
      <br />
      <v-row>
        <v-col><strong>Abbau des Servers am:</strong></v-col>
        <v-col>{{
          formatter.formatToGermanLocalTime(
            instlServerDetails.removeScheduleTime.toISOString()
          )
        }}</v-col>
      </v-row>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";

import appserviceService from "@/api/appserviceService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";
import { useFormatter } from "@/composables/formatter.js";
import { useUserStore } from "@/stores/user.ts";
import Appservice from "@/types/Appservice.ts";
import installServerDetails, {
  categoryType,
  OsType,
} from "@/types/installServerDetails.ts";

const userStore = useUserStore();
const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
const formatter = useFormatter();
const jsonError = ref("");
const loading = ref(false);

watch(
  () => props.instlServerDetails.isLinuxCustom,
  (isChecked) => {
    if (isChecked) {
      const copy = JSON.parse(JSON.stringify(props.instlServerDetails));

      delete copy.disk;
      delete copy.dbParams["mssql"];
      delete copy.serverName;
      delete copy.isLinuxCustom;
      delete copy.linuxCustomExtraVars;

      if (copy.categoryType != categoryType.DB) {
        delete copy.dbParams;
        delete copy.nonPostgresReason;
      } else {
        // db params in das hauptobjekt verschieben
        Object.assign(copy, copy.dbParams?.mariaPostgresMysqlOracle);
        delete copy.dbParams;
      }
      if (copy.categoryType == categoryType.App) {
        if (copy.category.label == "Apache") {
          Object.assign(copy, { webserver_install: true });
        } else if (copy.category.label == "Apache/PHP") {
          Object.assign(copy, { webserver_install: true, php_install: true });
        } else if (copy.category.label == "Java") {
          Object.assign(copy, { java_install: true });
        } else if (copy.category.label == "Apache/Tomcat") {
          Object.assign(copy, {
            webserver_install: true,
            tomcat_install: true,
            java_install: true,
          });
        }
      }
      delete copy.category;

      Object.assign(copy, { networkgroup: copy.networkGroup.name });
      delete copy.networkGroup;
      Object.assign(copy, { fqdn: copy.expectedServerName });
      delete copy.expectedServerName;
      Object.assign(copy, { os_name: "rhel" });
      // get only number from copy.osVersion
      Object.assign(copy, { os_version: copy.osVersion.match(/\d+/)?.[0] });
      delete copy.osVersion;
      delete copy.osType;
      Object.assign(copy, { requester_username: userStore.getUser?.username });
      Object.assign(copy, { memory_mb: copy.memory * 1024 });
      delete copy.memory;
      delete copy.categoryType;
      appserviceService
        .getAppservice(loading, copy.appservice?.id)
        .then((response: Appservice) => {
          if (response) {
            Object.assign(copy, {
              application_service_number: response.number,
            });
            Object.assign(copy, { application_service: response.name });
            Object.assign(copy, { is_microsegmented: response.cswEnforced });
            Object.assign(copy, {
              application_service_environment: response.environment,
            });
            Object.assign(copy, { appservice_id: response.id });
            delete copy.appservice;
          }
        })
        .finally(() => {
          // String initial setzen
          props.instlServerDetails.linuxCustomExtraVars = JSON.stringify(
            copy,
            null,
            2
          );
          jsonError.value = "";
        });
    } else {
      // Reset wenn deaktiviert
      props.instlServerDetails.linuxCustomExtraVars = "";
      jsonError.value = "";
    }
  }
);

watch(
  () => props.instlServerDetails.linuxCustomExtraVars,
  (newVal) => {
    if (!props.instlServerDetails.isLinuxCustom) return;

    if (!newVal.trim()) {
      jsonError.value = "";
      return;
    }

    try {
      JSON.parse(newVal);
      jsonError.value = "";
    } catch (e) {
      jsonError.value = e instanceof Error ? e.message : String(e);
    }
  }
);
</script>
