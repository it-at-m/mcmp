<template>
  <CommonDialog
    v-model="dialog"
    :loading="loading"
    title="Server"
    max-width="1200"
    @dialog-cancel="close"
    submitActivated
    :checkForEnabledActions="[
      'LINUX_RHEL9_SERVER',
      'LINUX_RHEL10_SERVER',
      'WINDOWS_SERVER_2025',
      'WINDOWS_SERVER_2022',
    ]"
  >
    <template #activator="{ props }">
      <v-btn
        flat
        v-bind="props"
        @click="
          reset();
          registerOpenDialog;
        "
        >Server
      </v-btn>
    </template>

    <v-stepper
      v-model="step"
      :items="pages"
      rounded="lg"
    >
      <template v-slot:item.1>
        <div style="min-height: 500px">
          <GeneralSettings :instlServerDetails="instlServerDetails" />
        </div>
      </template>

      <template v-slot:item.2>
        <div style="min-height: 500px">
          <ExtraSettings
            v-if="
              instlServerDetails.categoryType === categoryType.DB ||
              instlServerDetails.categoryType === categoryType.Mixed
            "
            :instlServerDetails="instlServerDetails"
          />
          <ServerName
            v-else
            :instlServerDetails="instlServerDetails"
          />
        </div>
      </template>

      <template v-slot:item.3>
        <div style="min-height: 500px">
          <ServerName
            v-if="
              instlServerDetails.categoryType === categoryType.DB ||
              instlServerDetails.categoryType === categoryType.Mixed
            "
            :instlServerDetails="instlServerDetails"
          />
          <HardwareSettings
            v-else
            :instlServerDetails="instlServerDetails"
          />
        </div>
      </template>

      <template v-slot:item.4>
        <div style="min-height: 500px">
          <HardwareSettings
            v-if="
              instlServerDetails.categoryType === categoryType.DB ||
              instlServerDetails.categoryType === categoryType.Mixed
            "
            :instlServerDetails="instlServerDetails"
          />
          <DeleteSchedule
            v-else-if="
              !(
                instlServerDetails.categoryType === categoryType.DB ||
                instlServerDetails.categoryType === categoryType.Mixed
              ) && instlServerDetails.schedule
            "
            :instlServerDetails="instlServerDetails"
          />
          <InstallSummary
            v-else
            :instlServerDetails="instlServerDetails"
          />
        </div>
      </template>

      <template v-slot:item.5>
        <div style="min-height: 500px">
          <DeleteSchedule
            v-if="
              (instlServerDetails.categoryType === categoryType.DB ||
                instlServerDetails.categoryType === categoryType.Mixed) &&
              instlServerDetails.schedule
            "
            :instlServerDetails="instlServerDetails"
          />
          <InstallSummary
            v-else
            :instlServerDetails="instlServerDetails"
          />
        </div>
      </template>

      <template v-slot:item.6>
        <div style="min-height: 500px">
          <InstallSummary
            v-if="instlServerDetails.schedule"
            :instlServerDetails="instlServerDetails"
          />
        </div>
      </template>

      <template v-slot:actions="{ next, prev }">
        <div class="d-flex justify-space-between w-100 mt-4 mb-4 px-4">
          <v-btn
            :prepend-icon="mdiArrowLeft"
            color="cancel"
            variant="outlined"
            rounded="xl"
            class="action-btn cancel-btn"
            @click="prev"
            :disabled="step == 1"
            >Zurück
          </v-btn>
          <v-btn
            :append-icon="mdiArrowRight"
            color="do"
            variant="flat"
            size="large"
            rounded="xl"
            class="action-btn confirm-btn"
            :disabled="!validationRules.allowNext(step, instlServerDetails)"
            @click="step === pages.length ? order() : step++"
          >
            {{ step === pages.length ? "Bestellen" : "Weiter" }}
          </v-btn>
        </div>
      </template>
    </v-stepper>
  </CommonDialog>
</template>

<script setup lang="ts">
import type { ServerCategoryType } from "@/types/ServerTypes.ts";

import { mdiArrowLeft, mdiArrowRight } from "@mdi/js";
import { computed, inject, ref } from "vue";
import { shades } from "vuetify/util/colors";

import jobService from "@/api/jobService";
import CommonDialog from "@/components/common/CommonDialog.vue";
import DeleteSchedule from "@/components/install/DeleteSchedule.vue";
import ExtraSettings from "@/components/install/ExtraSettings.vue";
import GeneralSettings from "@/components/install/GeneralSettings.vue";
import HardwareSettings from "@/components/install/HardwareSettings.vue";
import InstallSummary from "@/components/install/InstallSummary.vue";
import ServerName from "@/components/install/ServerName.vue";
import { useRules } from "@/composables/rules";
import { useUserStore } from "@/stores/user.ts";
import installServerDetails, {
  categoryType,
  OperatingSystem,
  OsType,
  OsVersion,
} from "@/types/installServerDetails";
import NewServername from "@/types/NewServername.ts";

const validationRules = useRules();

const instlServerDetails = ref<installServerDetails>(
  createDefaultInstallServerDetails()
);
const userStore = useUserStore();

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

const dialog = ref(false);
const loading = ref(false);

const step = ref(1);
const pages = computed(() => [
  "Allgemein",
  ...(instlServerDetails.value.categoryType === categoryType.DB ||
  instlServerDetails.value.categoryType === categoryType.Mixed
    ? ["Extra Einstellungen"]
    : []),
  "Server Name",
  "Hardware",
  ...(instlServerDetails.value.schedule ? ["Abbau"] : []),
  "Zusammenfassung",
]);

function close() {
  dialog.value = false;
  reset();
  unregisterOpenDialog?.();
}

function reset() {
  step.value = 1;
  instlServerDetails.value = createDefaultInstallServerDetails();
}

function createDefaultInstallServerDetails(): installServerDetails {
  return new installServerDetails(
    null,
    null,
    null,
    null,
    null,
    {
      mariaPostgresMysqlOracle: {
        db_type: "",
        db_version: "",
        customer_db_name: "",
        customer_db_user: "",
        customer_db_schema: "",
        customer_db_charset: "",
        postgis: [],
        conn_dima_admin: false,
        conn_cap: false,
        conn_app_server: false,
        customer_app_server: [],
        oracle_datasize: 15,
      },
      mssql: {
        mssql_serversort: "SQL_Latin1_General_CP1_CI_AS",
      },
    },
    "",
    false,
    new NewServername("", "", "", "", 1),
    "",
    8,
    2,
    {
      [OsType.Windows]: {
        [categoryType.Standard]: [
          {
            drive_number: 0,
            label: "C:\\ Hauptpartition",
            size: 100,
            min_size: 100,
            max_size: 500,
          },
        ],
        [categoryType.DB]: [
          {
            drive_number: 1,
            label: "E:\\ SQL_DATA",
            size: 10,
            min_size: 10,
            max_size: 500,
          },
          {
            drive_number: 2,
            label: "F:\\ SQL_LOG",
            size: 5,
            min_size: 5,
            max_size: 500,
          },
          {
            drive_number: 3,
            label: "G:\\ Temp_data",
            size: 10,
            min_size: 10,
            max_size: 500,
          },
          {
            drive_number: 4,
            label: "H:\\ Temp_log",
            size: 20,
            min_size: 20,
            max_size: 500,
          },
        ],
      },
      [OsType.Linux]: {
        [categoryType.Standard]: [
          {
            drive_number: 0,
            label: "Hauptpartition",
            size: 100,
            min_size: 100,
            max_size: 500,
          },
        ],
      },
    },
    null
  );
}

function order() {
  // custom linux
  let schedule = {};
  if (instlServerDetails.value.schedule) {
    schedule = {
      ...schedule,
      removeScheduleTime: instlServerDetails.value.removeScheduleTime,
    };
  }

  if (
    instlServerDetails.value.isLinuxCustom &&
    userStore.getUser?.authorities?.includes("ROLE_LINUX")
  ) {
    let json = JSON.parse(instlServerDetails.value.linuxCustomExtraVars);

    Object.assign(json, { linux_custom: true });

    if (
      instlServerDetails.value.osType == OsType.Linux &&
      instlServerDetails.value.osVersion == OsVersion.RHEL10
    ) {
      jobService.startJob(loading, "LINUX_RHEL10_SERVER", -1, {
        ...json,
        ...schedule,
      });
      close();
      return;
    }
    if (
      instlServerDetails.value.osType == OsType.Linux &&
      instlServerDetails.value.osVersion == OsVersion.RHEL9
    ) {
      jobService.startJob(loading, "LINUX_RHEL9_SERVER", -1, {
        ...json,
        ...schedule,
      });
      close();
      return;
    }
  }

  if (
    instlServerDetails.value.networkGroup == null ||
    instlServerDetails.value.appservice == null
  ) {
    alert(
      "Bitte wählen Sie eine Netzwerkgruppe und einen Applikationsservice aus."
    );
    return;
  }

  let serverType = instlServerDetails.value.serverName!.serverType;
  if (
    instlServerDetails.value.category &&
    "kenner" in instlServerDetails.value.category &&
    !("appCategorys" in instlServerDetails.value.category)
  ) {
    const category = instlServerDetails.value.category as ServerCategoryType;
    if (category.kenner) {
      serverType = serverType + category.kenner;
    }
  }

  // set Os prefix only if no kenner is set
  if (serverType.length == 0) {
    if (OperatingSystem.Linux.includes(instlServerDetails.value.osVersion!)) {
      serverType = serverType + "lx";
    } else if (
      OperatingSystem.Windows.includes(instlServerDetails.value.osVersion!)
    ) {
      serverType = serverType + "wi";
    } else {
      console.error("OsVersion is not in linux or windows.");
      return;
    }
  }
  instlServerDetails.value.serverName!.prefix = instlServerDetails.value.serverName!.prefix || "";
  instlServerDetails.value.serverName!.serverType = serverType;

  // 1. RHEL 10
  if (
    instlServerDetails.value.osType == OsType.Linux &&
    instlServerDetails.value.osVersion == OsVersion.RHEL10
  ) {
    jobService.startJob(loading, "LINUX_RHEL10_SERVER", -1, {
      fqdn: instlServerDetails.value.serverName,
      categoryType: instlServerDetails.value.categoryType,
      serverType: instlServerDetails.value.category,
      ram: instlServerDetails.value.memory,
      cpu: instlServerDetails.value.cpu,
      network_group_id: instlServerDetails.value.networkGroup.id,
      application_service_id: instlServerDetails.value.appservice.id,
      db_params:
        instlServerDetails.value.categoryType == categoryType.DB ||
        instlServerDetails.value.categoryType == categoryType.Mixed
          ? instlServerDetails.value.dbParams
          : null,
      non_postgres_reason: instlServerDetails.value.nonPostgresReason,
      middleware_user: instlServerDetails.value.middlewareUser,
      ...schedule,
    });
  }
  // 2. RHEL 9
  else if (
    instlServerDetails.value.osType == OsType.Linux &&
    instlServerDetails.value.osVersion == OsVersion.RHEL9
  ) {
    jobService.startJob(loading, "LINUX_RHEL9_SERVER", -1, {
      fqdn: instlServerDetails.value.serverName,
      categoryType: instlServerDetails.value.categoryType,
      serverType: instlServerDetails.value.category,
      ram: instlServerDetails.value.memory,
      cpu: instlServerDetails.value.cpu,
      network_group_id: instlServerDetails.value.networkGroup.id,
      application_service_id: instlServerDetails.value.appservice.id,
      db_params:
        instlServerDetails.value.categoryType == categoryType.DB ||
        instlServerDetails.value.categoryType == categoryType.Mixed
          ? instlServerDetails.value.dbParams
          : null,
      non_postgres_reason: instlServerDetails.value.nonPostgresReason,
      ...schedule,
    });
  }
  // 3. Windows Server 2022
  else if (
    instlServerDetails.value.osType == OsType.Windows &&
    instlServerDetails.value.osVersion == OsVersion.Windows2022
  ) {
    jobService.startJob(loading, "WINDOWS_SERVER_2022", -1, {
      fqdn: instlServerDetails.value.serverName,
      categoryType: instlServerDetails.value.categoryType,
      serverType: instlServerDetails.value.category,
      ram: instlServerDetails.value.memory,
      cpu: instlServerDetails.value.cpu,
      disks: instlServerDetails.value.disk[OsType.Windows][instlServerDetails.value.categoryType!],
      network_group_id: instlServerDetails.value.networkGroup.id,
      application_service_id: instlServerDetails.value.appservice.id,
      osVersion: instlServerDetails.value.osVersion,
      non_postgres_reason: instlServerDetails.value.nonPostgresReason,
      db_params:
        instlServerDetails.value.categoryType == categoryType.DB ||
        instlServerDetails.value.categoryType == categoryType.Mixed
          ? instlServerDetails.value.dbParams
          : null,
      ...schedule,
    });
  }
  // 4. Windows Server 2025
  else if (
    instlServerDetails.value.osType == OsType.Windows &&
    instlServerDetails.value.osVersion == OsVersion.Windows2025
  ) {
    jobService.startJob(loading, "WINDOWS_SERVER_2025", -1, {
      fqdn: instlServerDetails.value.serverName,
      categoryType: instlServerDetails.value.categoryType,
      serverType: instlServerDetails.value.category,
      ram: instlServerDetails.value.memory,
      cpu: instlServerDetails.value.cpu,
      disks: instlServerDetails.value.disk[OsType.Windows][instlServerDetails.value.categoryType!],
      network_group_id: instlServerDetails.value.networkGroup.id,
      application_service_id: instlServerDetails.value.appservice.id,
      osVersion: instlServerDetails.value.osVersion,
      non_postgres_reason: instlServerDetails.value.nonPostgresReason,
      db_params:
        instlServerDetails.value.categoryType == categoryType.DB ||
        instlServerDetails.value.categoryType == categoryType.Mixed
          ? instlServerDetails.value.dbParams
          : null,
      ...schedule,
    });
  }
  else {
    alert(
      "Für die ausgewählte Betriebssystemversion ist derzeit keine Bestellung möglich."
    );
    return;
  }
  close();
}
</script>

<style scoped>
.action-btn {
  min-width: 120px;
  height: 44px;
  border-radius: 12px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: 0.5px;
  transition: all 0.3s ease;
}

.cancel-btn {
  border: 2px solid #90a4ae;
  color: rgb(var(--v-theme-cancel));
}

.cancel-btn:hover {
  background: rgb(var(--v-theme-bg_light));
  border-color: #90a4ae;
  transform: translateY(-1px);
}

.confirm-btn {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.3);
  color: white !important;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #2196f3 0%, #1976d2 100%);
}

:deep(.v-stepper-item) {
  cursor: default;
}
</style>