<template>
  <CommonCard title="Preise verwalten">
    <template #toolbar-actions>
      <PriceAddEdit
        title="Preis hinzufügen"
        :icon="mdiPlus"
        @save="saveAction"
      />
    </template>
    <v-data-table
      :headers="headers"
      :items="prices"
      class="elevation-1"
      :items-per-page="-1"
      item-value="id"
      no-data-text="Keine Preise gefunden"
      :sort-by="sortBy"
    >
      <template v-slot:item.edit="{ item }">
        <PriceAddEdit
          title="Preis bearbeiten"
          :icon="mdiPencil"
          :price="item"
          @save="updateAction"
        />
        <v-btn
          icon
          @click="askDeletePermission(item)"
          :aria-label="`Preis ${item.name} löschen`"
        >
          <v-icon>{{ mdiDelete }}</v-icon>
        </v-btn>
      </template>
    </v-data-table>
  </CommonCard>
  <v-dialog
    v-model="deleteDialog"
    max-width="500px"
  >
    <v-card>
      <v-toolbar>
        <v-toolbar-title class="text-h5">Preis löschen</v-toolbar-title>
        <v-btn
          :icon="mdiClose"
          @click="deleteDialog = false"
        />
      </v-toolbar>
      <v-card-text>Wollen Sie den Preis wirklich löschen?</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="cancel"
          @click="deleteDialog = false"
          >Abbrechen</v-btn
        >
        <v-btn
          variant="flat"
          color="do"
          @click="deleteAction(toEdit)"
          >Löschen</v-btn
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import type Price from "@/types/Price";
import type { DataTableHeader } from "vuetify/framework";

import { mdiClose, mdiDelete, mdiPencil, mdiPlus } from "@mdi/js";
import { onMounted, ref } from "vue";

import priceService from "@/api/priceService.ts";
import CommonCard from "@/components/common/CommonCard.vue";
import PriceAddEdit from "@/components/Settings/priceAddEdit.vue";

const deleteDialog = ref(false);
const toEdit = ref();
const sortBy = ref([{ key: "name", order: "asc" as "asc" | "desc" }]);
const loading = ref(true);
const prices = ref<Price[]>();

onMounted(() => {
  getPrices();
});

const headers = ref<DataTableHeader[]>([
  { title: "Name", key: "name" },
  { title: "Preis", key: "pricePerUnit" },
  { title: "Währung", key: "currency" },
  { title: "Beschreibung", key: "description" },
  {
    title: "Bearbeiten/Löschen",
    key: "edit",
    sortable: false,
    align: "end",
  },
]);

function getPrices() {
  priceService.getPrices(loading).then((response) => {
    prices.value = response;
  });
}

function saveAction(price: Price) {
  priceService.createPrice(loading, price).then(() => {
    getPrices();
  });
}

function updateAction(price: Price) {
  priceService.updatePrice(loading, price).then(() => {
    getPrices();
  });
}

function deleteAction(price: Price) {
  priceService.deletePrice(loading, price.name).then(() => {
    getPrices();
    deleteDialog.value = false;
  });
}

function askDeletePermission(price: Price) {
  toEdit.value = price;
  deleteDialog.value = true;
}
</script>
