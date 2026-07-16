<template>
  <v-menu
    v-model="isOpen"
    location="right"
    max-height="500"
  >
    <template #activator="{ props }">
      <v-btn
        v-bind="props"
        flat
      >
        ServiceNow Tickets
        <v-icon end>{{ isOpen ? mdiChevronUp : mdiChevronDown }}</v-icon>
      </v-btn>
    </template>
    <v-list rounded="lg">
      <v-list-item>
        <strong
          >Diese Tickets werden bald<br />
          in die MCMP integriert</strong
        >
      </v-list-item>
      <v-list-item
        v-for="([category, tickets], idx) in sortedSnowTickets"
        :key="category"
      >
        <v-menu
          v-model="isOpenList[idx]"
          location="right top"
          max-height="400"
        >
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              flat
            >
              {{ category }}
              <v-icon end>{{
                isOpenList[idx] ? mdiChevronUp : mdiChevronDown
              }}</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item
              v-for="ticket in [...tickets].sort((a, b) =>
                a.Name.localeCompare(b.Name)
              )"
              :key="ticket.Name"
            >
              <v-btn
                :href="ticket.href"
                target="_blank"
                rel="noopener"
                flat
                :append-icon="mdiOpenInNew"
              >
                {{ ticket.Name }}
              </v-btn>
            </v-list-item>
          </v-list>
        </v-menu>
      </v-list-item>
    </v-list>
  </v-menu>
</template>
<script setup lang="ts">
import { mdiChevronDown, mdiChevronUp, mdiOpenInNew } from "@mdi/js";
import { computed, ref } from "vue";

const isOpen = ref(false);

const sortedSnowTickets = computed(() =>
  Object.entries(SnowTicketsOldMap).sort((a, b) => a[0].localeCompare(b[0]))
);

const SnowTicketsOldMap = {
  linux: [
    {
      Name: "Reinstallation Linuxserver",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=ae31946adbd51090fcba8384059619c3",
    },
    {
      Name: "Beantragen eines Paketshop Repository",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=44fa7b08db1dd410fcba8384059619fe",
    },
    {
      Name: "Linuxserver gemeinsamen Share anlegen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=9829ec041bd99410588efddacd4bcb59",
    },
    {
      Name: "Ansible & AWX - Support beantragen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=b19dfedc1ba91150948e657f7b4bcb31",
    },
    {
      Name: "Sharefreigabe",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=18e8c7ca1b6a5c100207db169b4bcb9d",
    },
    {
      Name: "Linux VM NFS-Speicher anpassen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=73fd83e11bde1094588efddacd4bcb92",
    },
  ],
  windows: [
    {
      Name: "Windows Server User-Berechtigung",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=516c42461b32ec10e52dfddacd4bcb0f",
    },
    {
      Name: "NFS Share anlegen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=b5f038001b5d9410588efddacd4bcb8a",
    },
    {
      Name: "Sharefreigabe",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=18e8c7ca1b6a5c100207db169b4bcb9d",
    },
  ],
  Datenbanken: [
    {
      Name: "Oracle DBs Daten übertragen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=afa7d1b6db06d8d0fcba8384059619f2",
    },
    {
      Name: "Daten übertragen für DBs",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=d44f697d1b1378d0d95c744c8b4bcbf6",
    },
    {
      Name: "Datenbank Auftrag einreichen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=1484976ac3236250d130f1fb05013122",
    },
  ],
  Storage: [
    {
      Name: "S3 Bucket anlegen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=109ecd231b4b3894d95c744c8b4bcb9b",
    },
    {
      Name: "S3 Bucket löschen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=443011e31b4b3894d95c744c8b4bcb06",
    },
    {
      Name: "S3 Tenant anpassen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=72e0996b1b4b3894d95c744c8b4bcb61",
    },
  ],
  Backup: [
    {
      Name: "Wiederherstellung Server",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=c2945c271b77a810e52dfddacd4bcb5c",
    },
    {
      Name: "Wiederherstellung Datenbank",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=a24b24041b32e150948e657f7b4bcb5f",
    },
  ],
  Netzwerk: [
    {
      Name: "Portfreischaltung",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=85fcd2951b8dad94948e657f7b4bcbb9",
    },
    {
      Name: "CNAME anlegen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=dbd8badfdb43c450fcba838405961974",
    },
    {
      Name: "CNAME ändern",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=3d952757db0bc450fcba8384059619e1",
    },
    {
      Name: "CNAME löschen",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=98124bd7dbc3c450fcba8384059619b9",
    },
  ],
  Monitoring: [
    {
      Name: "Applikation ans Logmanagement anbinden",
      href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=35d2512e1b7cf5d4e52dfddacd4bcbac",
    },
  ],
};
const isOpenList = ref<boolean[]>(
  Object.keys(SnowTicketsOldMap).map(() => false)
);
</script>
