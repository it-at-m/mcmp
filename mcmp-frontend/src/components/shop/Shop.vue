<template>
  <v-menu
    v-model="isOpen"
    :location="railMode ? 'end' : 'bottom'"
  >
    <template #activator="{ props }">
      <v-list-item
        v-if="railMode"
        v-bind="props"
        :prepend-icon="mdiCartArrowDown"
      >
        <v-tooltip
          activator="parent"
          location="right"
        >
          Neu
        </v-tooltip>
      </v-list-item>
      <v-btn
        v-else
        v-bind="props"
        :prepend-icon="mdiCartArrowDown"
        size="large"
        rounded
        class="mr-2"
      >
        Neu
        <v-icon end>{{ isOpen ? mdiChevronUp : mdiChevronDown }}</v-icon>
      </v-btn>
    </template>
    <v-list rounded="lg">
      <v-list-item>
        <install-dialog />
      </v-list-item>
      <v-list-item>
        <ansible-user />
      </v-list-item>
       <v-list-item>
         <v-menu
           v-model="isLoadbalancerOpen"
           location="right"
         >
           <template #activator="{ props }">
             <v-btn
               v-bind="props"
               flat
             >
               Loadbalancer
               <v-icon end>{{
                 isLoadbalancerOpen ? mdiChevronUp : mdiChevronDown
               }}</v-icon>
             </v-btn>
           </template>
           <v-list>
             <v-list-item>
               <loadbalancer-order />
             </v-list-item>
             <v-list-item>
               <v-btn
                 href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=d27584b32b2b36d05779ff1ece91bfb9"
                  target="_blank"
                  rel="noopener"
                 flat
                 :append-icon="mdiOpenInNew"
                  >Ändern</v-btn>
              </v-list-item>
             <v-list-item>
                <v-btn
                  href="https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=4f8a347b2b2f76d05779ff1ece91bf1b"
                    target="_blank"
                    rel="noopener"
                  flat
                  :append-icon="mdiOpenInNew"
                    >Löschen</v-btn>
             </v-list-item>
            </v-list>
          </v-menu>
      </v-list-item>
      <v-list-item>
        <v-menu
          v-model="isAnwendOpen"
          location="right"
        >
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              flat
            >
              Anwendungsservice
              <v-icon end>{{
                isAnwendOpen ? mdiChevronUp : mdiChevronDown
              }}</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item
              v-for="ticket in anwendungsserviceSnowTickets.sort((a, b) =>
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
      <v-list-item>
        <snow-tickets-old />
      </v-list-item>
      <v-list-item>
              <v-btn
                href="https://it-services.muenchen.de/now/platform-analytics-workspace/dashboards/params/edit/false/sys-id/ed9ef3f7d2959f75a70f2bf6768e3d31"
                target="_blank"
                rel="noopener"
                flat
                :append-icon="mdiOpenInNew"
              >
                LCM Dashboard
              </v-btn>
            </v-list-item>
    </v-list>
  </v-menu>
</template>
<script setup lang="ts">
import {
  mdiCartArrowDown,
  mdiChevronDown,
  mdiChevronUp,
  mdiOpenInNew,
} from "@mdi/js";
import { ref } from "vue";

import InstallDialog from "@/components/install/InstallDialog.vue";
import LoadbalancerOrder from "@/components/Loadbalancer/LoadbalancerOrder.vue";
import AnsibleUser from "@/components/shop/AnsibleUser.vue";
import SnowTicketsOld from "@/components/shop/SnowTicketsOld.vue";

defineProps<{
  railMode?: boolean;
}>();

const isOpen = ref(false);
const isAnwendOpen = ref(false);
const isLoadbalancerOpen = ref(false);

const anwendungsserviceSnowTickets = [
  {
    Name: "Erstellen",
    href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=b3b788ca1b2ca550e52dfddacd4bcbb4",
  },
  {
    Name: "Bearbeiten",
    href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=fc165be41b49e990e52dfddacd4bcb1b",
  },
  {
    Name: "Stilllegen",
    href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=a58ee9d71b4f16104b4ffd509b4bcbbb",
  },
  {
    Name: "Zugriff bearbeiten",
    href: "https://it-services.muenchen.de/sp?id=sc_cat_item&sys_id=58f6edb71b77ec10e52dfddacd4bcb6c",
  },
];
</script>
