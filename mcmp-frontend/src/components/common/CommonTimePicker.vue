<template>
  <h4>{{ lableText }}zeitpunkt:</h4>
  <v-text-field
    v-model="formatedEndDate"
    label="Datum"
    :aria-label="lableText + 'datum'"
    readonly
  />
  <div class="justify-center d-flex">
    <v-date-picker
      :model-value="rawDate"
      :min="minDate"
      :aria-label="lableText + 'datum auswählen'"
      hide-header
      hide-actions
      :rules="dateRules"
      @update:model-value="updateDate"
    />
  </div>
  <br />
  <v-text-field
    v-model="time"
    label="Uhrzeit"
    type="time"
    :aria-label="lableText + 'uhrzeit eintragen'"
    :rules="timeRules"
    @update:model-value="writeInDate"
  >
    <template
      v-if="withButtons"
      #append-inner
    >
      <v-btn
        :icon="mdiChevronUp"
        size="small"
        variant="text"
        @click="increase"
      />
      <v-btn
        :icon="mdiChevronDown"
        size="small"
        variant="text"
        @click="decrease"
      />
    </template>
  </v-text-field>
</template>

<script setup lang="ts">
import { mdiChevronDown, mdiChevronUp } from "@mdi/js";
import { computed, ref, watch } from "vue";

const props = defineProps<{
  lableText: string;
  timeRules?: any[];
  dateRules?: any[];
  rawDateIn: Date;
  round?: boolean;
  withButtons?: boolean;
}>();

const emit = defineEmits(["update:rawDateIn"]);

const rawDate = ref<Date>(props.rawDateIn);

const time = ref<string>(formatTime(rawDate.value));
const formatedEndDate = computed(() => formatDate(rawDate.value));

watch(
  () => props.rawDateIn,
  (newDate) => {
    rawDate.value = new Date(newDate);
    const newTime = formatTime(rawDate.value);
    if (time.value !== newTime) {
      time.value = newTime;
    }
  },
  { immediate: true }
);

const now = new Date();
const minDate = now.toISOString().split("T")[0];

function writeInDate(newTime: string) {
  if (props.round) {
    time.value = roundTimeToHalfHour(newTime);
  }
  writeTimeInDate(time.value, rawDate.value);
}

function updateDate(date: Date) {
  const hours = rawDate.value.getHours();
  const minutes = rawDate.value.getMinutes();
  rawDate.value = new Date(date);
  rawDate.value.setHours(hours);
  rawDate.value.setMinutes(minutes);
  emit("update:rawDateIn", rawDate.value);
}

function roundTimeToHalfHour(time: string) {
  if (!time) return "";

  // Parse time with default values to prevent undefined
  let [hours = 0, minutes = 0] = time.split(":").map(Number);

  // Wenn Minuten < 30 → auf 30 setzen
  if (minutes < 30 && minutes > 0) {
    minutes = 30;
  } else if (minutes > 30) {
    // Wenn Minuten >= 30 → auf 00 und +1 Stunde
    minutes = 0;
    hours += 1;

    // Optional: 24-Stunden-Überlauf behandeln
    if (hours >= 24) {
      hours = 0;
    }
  }

  // Zeit wieder als String formatieren (z. B. "09:00")
  const h = String(hours).padStart(2, "0");
  const m = String(minutes).padStart(2, "0");
  return `${h}:${m}`;
}

function increase() {
  if (!time.value) return "";
  // Parse time with default values to prevent undefined
  let [hours = 0, minutes = 0] = time.value.split(":").map(Number);

  minutes += 1;

  // Zeit wieder als String formatieren (z. B. "09:00")
  const h = String(hours).padStart(2, "0");
  const m = String(minutes).padStart(2, "0");
  writeInDate(`${h}:${m}`);
}

function decrease() {
  if (!time.value) return;
  // Parse time with default values to prevent undefined
  let [hours = 0, minutes = 0] = time.value.split(":").map(Number);

  // Wenn Minuten < 30 → auf 30 setzen
  if (minutes <= 30 && minutes > 0) {
    minutes = 0;
  } else {
    // Wenn Minuten >= 30 → auf 00 und +1 Stunde
    minutes = 30;
    hours -= 1;

    // Optional: 24-Stunden-Überlauf behandeln
    if (hours < 0 && minutes <= 30) {
      hours = 23;
    }
  }

  // Zeit wieder als String formatieren (z. B. "09:00")
  const h = String(hours).padStart(2, "0");
  const m = String(minutes).padStart(2, "0");
  writeInDate(`${h}:${m}`);
}

function stringTimeParser(time: string): [number, number] {
  // Parse time with default values and return as tuple
  const [h = 0, m = 0] = time.split(":").map(Number);
  return [h, m];
}

function writeTimeInDate(time: string, date: Date) {
  if (time != "" && time != null) {
    const [h, m] = stringTimeParser(time);
    date.setHours(h);
    date.setMinutes(m);
    updateDate(date);
  }
}

function formatDate(date: Date) {
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = date.getFullYear();
  return `${day}.${month}.${year}`;
}

function formatTime(date: Date) {
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${hours}:${minutes}`;
}
</script>
