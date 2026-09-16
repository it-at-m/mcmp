<template>
  <v-form v-model="isValid">
    <v-row>
      <v-col>
        <v-text-field
          v-model="order.cpuLimit"
          label="CPU Limit"
          rounded
          variant="outlined"
          class="mt-2"
          disabled
          readonly
        />
      </v-col>
      <v-col>
        <v-text-field
          v-model="order.memoryLimit"
          label="Memory Limit*"
          placeholder="z.B. 1Gi"
          rounded
          class="mt-2"
          variant="outlined"
          :rules="[
            rules.notEmptyRule('Ein Memory Limit muss angegeben werden'),
            rules.regexRule(/^[0-9]+Gi$/, 'Ungültiges Format, z.B. 4Gi'),
            memoryMaxRule,
          ]"
          @update:model-value="memoryTouched = true"
        />
      </v-col>
    </v-row>
    <v-row>
      <v-col>
        <v-text-field
          v-model.number="order.podLimit"
          label="Pod Limit*"
          type="number"
          placeholder="z.B. 8"
          rounded
          variant="outlined"
          :rules="[
            rules.rangeRule(
              2,
              128,
              'Das Pod Limit muss zwischen 2 und 128 liegen'
            ),
          ]"
        />
      </v-col>
      <v-col>
        <v-select
          v-model="order.pvLimit"
          label="Persistent Volume Limit*"
          :items="[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]"
          rounded
          variant="outlined"
          :menu-props="{ closeOnContentClick: true }"
          :rules="[
            rules.rangeRule(
              0,
              9,
              'Das Persistent Volume Limit muss zwischen 0 und 9 liegen'
            ),
          ]"
        />
      </v-col>
    </v-row>
  </v-form>
</template>

<script setup lang="ts">
import type OpenshiftNamespaceOrder from "@/types/OpenshiftNamespaceOrder.ts";

import { ref, watch } from "vue";

import { useRules } from "@/composables/rules.ts";

const order = defineModel<OpenshiftNamespaceOrder>({ required: true });
const rules = useRules();
const isValid = ref(false);
const emit = defineEmits(["validation-change"]);
const memoryTouched = ref(false);

watch(isValid, (newVal) => {
  emit("validation-change", !!newVal);
});

order.value.cpuLimit = "100m";

function memoryMaxRule(value: string | null | undefined) {
  if (!value || !value.endsWith("Gi")) return true;
  const gibibytes = Number(value.slice(0, -2));
  return (
    (gibibytes >= 1 && gibibytes <= 32) ||
    "Das Memory Limit muss zwischen 1Gi und 32Gi liegen"
  );
}

// Standard-Memory-Limit abhängig vom gewählten Ingress, solange der Nutzer
// das Feld noch nicht manuell angepasst hat
watch(
  () => order.value.ingress,
  (ingress) => {
    if (memoryTouched.value) return;
    order.value.memoryLimit = ingress === "web2tier" ? "2Gi" : "1Gi";
  },
  { immediate: true }
);
</script>
