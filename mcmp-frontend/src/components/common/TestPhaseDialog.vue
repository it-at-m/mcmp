
<template>
  <v-dialog
      v-model="dialog"
      max-width="700"
      persistent
  >
    <v-card>
      <v-card-title class="d-flex align-center">
        <v-icon
            :icon="mdiInformationOutline"
            color="error"
            size="large"
            class="mr-2"
        />
        Testphase - Wichtiger Hinweis
      </v-card-title>

      <v-card-text class="pt-4">
        <v-alert
            type="error"
            variant="tonal"
            class="mb-4"
        >
          <div class="text-body-1">
            <strong>Diese Anwendung befindet sich aktuell in der Testphase!</strong>
          </div>
        </v-alert>

        <div class="text-body-1">
          <p class="mb-3">
            Bitte beachtet, dass sich die MCMP derzeit in der Entwicklungs- und Testphase befindet. Während dieser Phase kann es zu Änderungen, fehlenden Funktionen oder unerwarteten Fehlern kommen.
          </p>

          <p class="mb-3">
            <strong>Was funktioniert bereits?</strong>
          </p>

          <ul class="mb-5 ml-6">
            <li>Alle VMs stehen lesend zur Verfügung.</li>
            <li>Bearbeitungen sind aktuell nur an C/K-Systemen möglich, sofern entsprechende Berechtigungen für den Application Service vorhanden sind.</li>
            <li>Fehlender Application Service? Bitte über ServiceNow beantragen.</li>
          </ul>
           <p class="mb-3">
            <strong>Verfügbare Funktionalitäten bisher: </strong>
          </p>
        <ul class="mb-5 ml-6">
            <li>Starten, Stoppen und Neustarten von VMs</li>
            <li>Erstellen und Einspielen von Snapshots</li>
            <li>72h-Zugriff mit admin/root-Rechten</li>
            <li>Windows Wartungsmodus setzen und vorzeitig beenden</li>
            <li>Checkmk Downtime setzen und Service Discovery</li>
            <li>Anpassung von CPU-/RAM-Ressourcen</li>
            <li>Verlinkungen zur CMDB und checkmk sowie Infos zur Patchnight</li>
          </ul>
          <p class="mb-3">
            Wenden Sie sich gerne an das CMP-Team rund um Thomas Meier, Aaron Adler, Max Sedlmeier und Reiko Streng – per Mail oder über Webex.
          </p>
          <p class="mb-0">
            Vielen Dank für eure Unterstützung während der Testphase!
          </p>
        </div>
      </v-card-text>

      <v-card-actions class="px-6 pb-4">
        <v-spacer />
        <v-btn
            color="primary"
            variant="elevated"
            @click="acceptDialog"
        >
          Zur Kenntnis genommen
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { mdiInformationOutline } from "@mdi/js";
import { onMounted, ref } from "vue";

const dialog = ref(false);

const DIALOG_KEY = "testphase-dialog-acknowledged";

onMounted(() => {
  const acknowledged = sessionStorage.getItem(DIALOG_KEY);
  if (!acknowledged) {
    dialog.value = true;
  }
});

function acceptDialog(): void {
  dialog.value = false;
  sessionStorage.setItem(DIALOG_KEY, "true");
}
</script>