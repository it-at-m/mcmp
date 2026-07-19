<template>
  <common-dialog
    v-model="dialog"
    title="Oracle Backup erstellen"
    :icon="mdiPencil"
    max-width="600"
    show-actions
    submit-activated
    show-change-warning
    :check-for-enabled-actions="['DB_ORACLE_CREATE_BACKUP']"
    @dialog-cancel="close"
    @dialog-confirm="save"
  >
    <template #activator="{ props }">
      <v-list-item-title
        v-bind="props"
        style="cursor: pointer"
        >Oracle Backup erstellen
      </v-list-item-title>
    </template>
    <v-form
      v-if="props.type == 'Oracle'"
      ref="oracleRef"
    >
      <common-alert
        v-if="selectedBackupType == 'inkrementelle_Sicherung'"
        color="info"
      >
        <h4>Info:</h4>
        Bei der inkrementellen Sicherung (online Archivelog-Sicherung mit
        Restorepoint - empfohlener Backuptyp) werden nur die Daten gesichert,
        die sich seit der letzten Sicherung geändert haben. Dies spart
        Speicherplatz und Zeit im Vergleich zu einer vollen Sicherung, dauert
        bei der Wiederherstellung jedoch etwas länger, da sie auf der letzten
        Vollsicherung aufsetzt und danach kommende inkrementelle Sicherungen
        nachzieht.
      </common-alert>
      <common-alert
        v-if="selectedBackupType == 'volle_Sicherung'"
        color="info"
      >
        <h4>Info:</h4>
        Bei der vollen Sicherung werden alle Daten der Datenbank gesichert. Dies
        bietet die schnellste Wiederherstellungsmöglichkeit, benötigt jedoch
        mehr Speicherplatz und (insbesondere bei sehr großen Datenbanken) mehr
        Zeit beim Sichern im Vergleich zu einer inkrementellen Sicherung
        (Durchsatz 20-35GB/min abhängig von Auslastung
        Infrastruktur/VM/Datenbank).
      </common-alert>
      <common-alert
        v-if="selectedBackupType == 'offline_Sicherung'"
        color="notice_red"
      >
        <h4>Hinweis:</h4>
        Bei der offline Sicherung wird die Oracle Datenbank heruntergefahren und
        alle Daten der Datenbank gesichert. Nach der Erstellung des Backups wird
        die Datenbank wieder hochgefahren. Dadurch kommt es zu einer (ggf.
        längeren) Downtime der Datenbank und somit der Anwendung, bis die
        Datenbanksicherung abgeschlossen ist.<br />
        Wenn die Archivierung einer Datenbank deaktiviert ist
        ("No-Archive-Mode"), ist dies die einzige konsistente Sicherungsmethode.
      </common-alert>
      <br />
      <v-select
        v-model="selectedBackupType"
        label="Backup Typ"
        :items="oracleBackupType"
        item-title="key"
        item-value="value"
        :menu-props="{ persistent: true, closeOnContentClick: true }"
      />
    </v-form>
  </common-dialog>
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
const emit = defineEmits<(e: "save", value: boolean) => void>();
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
