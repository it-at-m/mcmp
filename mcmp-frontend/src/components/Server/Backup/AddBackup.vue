<template>
  <CommonDialog
    v-model="dialog"
    title="Oracle Backup erstellen"
    :icon="mdiPencil"
    max-width="600"
    show-actions
    submitActivated
    @dialog-cancel="close"
    @dialog-confirm="save"
    showChangeWarning
    :checkForEnabledActions="['DB_ORACLE_CREATE_BACKUP']"
  >
    <template #activator="{ props }">
      <v-list-item-title
        v-bind="props"
        style="cursor: pointer"
        >Oracle Backup erstellen
      </v-list-item-title>
    </template>
    <v-form
      ref="oracleRef"
      v-if="props.type == 'Oracle'"
    >
      <CommonAlert
        color="info"
        v-if="selectedBackupType == 'inkrementelle_Sicherung'"
      >
        <h4>Info:</h4>
        Bei der inkrementellen Sicherung (online Archivelog-Sicherung mit
        Restorepoint - empfohlener Backuptyp) werden nur die Daten gesichert,
        die sich seit der letzten Sicherung geändert haben. Dies spart
        Speicherplatz und Zeit im Vergleich zu einer vollen Sicherung, dauert
        bei der Wiederherstellung jedoch etwas länger, da sie auf der letzten
        Vollsicherung aufsetzt und danach kommende inkrementelle Sicherungen
        nachzieht.
      </CommonAlert>
      <CommonAlert
        color="info"
        v-if="selectedBackupType == 'volle_Sicherung'"
      >
        <h4>Info:</h4>
        Bei der vollen Sicherung werden alle Daten der Datenbank gesichert. Dies
        bietet die schnellste Wiederherstellungsmöglichkeit, benötigt jedoch
        mehr Speicherplatz und (insbesondere bei sehr großen Datenbanken) mehr
        Zeit beim Sichern im Vergleich zu einer inkrementellen Sicherung
        (Durchsatz 20-35GB/min abhängig von Auslastung
        Infrastruktur/VM/Datenbank).
      </CommonAlert>
      <CommonAlert
        color="notice_red"
        v-if="selectedBackupType == 'offline_Sicherung'"
      >
        <h4>Hinweis:</h4>
        Bei der offline Sicherung wird die Oracle Datenbank heruntergefahren und
        alle Daten der Datenbank gesichert. Nach der Erstellung des Backups wird
        die Datenbank wieder hochgefahren. Dadurch kommt es zu einer (ggf.
        längeren) Downtime der Datenbank und somit der Anwendung, bis die
        Datenbanksicherung abgeschlossen ist.<br />
        Wenn die Archivierung einer Datenbank deaktiviert ist
        ("No-Archive-Mode"), ist dies die einzige konsistente Sicherungsmethode.
      </CommonAlert>
      <br />
      <v-select
        label="Backup Typ"
        v-model="selectedBackupType"
        :items="oracleBackupType"
        item-title="key"
        item-value="value"
        :menu-props="{ persistent: true, closeOnContentClick: true }"
      />
    </v-form>
  </CommonDialog>
</template>
<script setup lang="ts">
import type Server from "@/types/Server.ts";

import { mdiPencil } from "@mdi/js";
import { inject, ref } from "vue";

import jobService from "@/api/jobService.ts";
import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonDialog from "@/components/common/CommonDialog.vue";

const props = defineProps<{
  type: string;
  server: Server;
}>();
const emit = defineEmits<{
  (e: "save", value: boolean): void;
}>();
const dialog = ref(false);
const oracleRef = ref<HTMLFormElement>();
const loading = ref<boolean>(false);
const selectedBackupType = ref<string>("inkrementelle_Sicherung");
const oracleBackupType = [
  { key: "Inkrementelle Sicherung", value: "inkrementelle_Sicherung" },
  { key: "Volle Sicherung", value: "volle_Sicherung" },
  //{ key: "Offline Sicherung", value: "offline_Sicherung" },
];

const registerOpenDialog = inject<() => void>("registerOpenDialog");
const unregisterOpenDialog = inject<() => void>("unregisterOpenDialog");

function open() {
  dialog.value = true;
  registerOpenDialog?.();
}

function resetForm() {
  selectedBackupType.value = "inkrementelle_Sicherung";
}

function close() {
  unregisterOpenDialog?.();
  resetForm();
  dialog.value = false;
}

function save() {
  oracleRef.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid) {
      jobService
        .startJob(loading, "DB_ORACLE_CREATE_BACKUP", props.server.id, {
          flag: selectedBackupType.value,
        })
        .then(() => {
          dialog.value = false;
          unregisterOpenDialog?.();
          emit("save", true);
          close();
        });
    }
  });
}
</script>
