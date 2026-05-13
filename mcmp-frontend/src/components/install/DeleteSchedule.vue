<template>
  <v-form ref="form">
    <CommonAlert isSnowChange />
    <br />
    <CommonTimePicker
      lableText="Abbau"
      :timeRules="[
        validationRules.notEmptyRule('Abbauzeitpunkt darf nicht leer sein.'),
        validationRules.isNotPastTime(
          nowPlusOne,
          rawDate,
          'Abbauzeitpunkt muss 1h nach Aufbau Zeitpunkt liegen.'
        ),
        validationRules.isNotAfterTime(
          new Date(),
          rawDate,
          14,
          'Abbauzeitpunkt darf nicht mehr als 2 Wochen in der Zukunft liegen.'
        ),
      ]"
      v-model:rawDateIn="rawDate"
    />
  </v-form>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";

import CommonAlert from "@/components/common/CommonAlert.vue";
import CommonTimePicker from "@/components/common/CommonTimePicker.vue";
import { useRules } from "@/composables/rules";
import installServerDetails from "@/types/installServerDetails";

const props = defineProps<{
  instlServerDetails: installServerDetails;
}>();
const validationRules = useRules();
const form = ref<HTMLFormElement>();
const validated = ref(false);
const rawDate = ref<Date>(new Date());
const nowPlusOne = computed(() => {
  let date = new Date();
  date.setHours(date.getHours() + 1);
  return date;
});

watch([rawDate], async () => {
  props.instlServerDetails.removeScheduleTime = rawDate.value;
  form.value?.validate().then((validation: { valid: boolean }) => {
    validated.value = validation.valid;
  });
});
</script>
