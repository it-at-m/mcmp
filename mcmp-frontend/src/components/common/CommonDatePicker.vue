<template>
  <v-menu
    v-model="menu"
    :close-on-content-click="false"
  >
    <template #activator="{ props }">
      <v-text-field
        v-bind="props"
        :model-value="inputValue"
        :label="label"
        :prepend-inner-icon="mdiCalendar"
        :variant="variant"
        :density="density"
        :clearable="clearable"
        :disabled="disabled"
        :class="textFieldClass"
        placeholder="TT.MM.JJJJ"
        hide-details
        @click:clear="handleClear"
        @update:model-value="handleManualInput"
      ></v-text-field>
    </template>
    <v-date-picker
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="handleDateChange"
    ></v-date-picker>
  </v-menu>
</template>

<script setup lang="ts">
import { mdiCalendar } from "@mdi/js";
import { ref, watch } from "vue";

type VariantType =
  | "outlined"
  | "filled"
  | "plain"
  | "solo"
  | "solo-filled"
  | "solo-inverted"
  | "underlined";
type DensityType = "default" | "comfortable" | "compact";

interface Props {
  modelValue: string | Date | null;
  label: string;
  variant?: VariantType;
  density?: DensityType;
  clearable?: boolean;
  disabled?: boolean;
  textFieldClass?: string;
}

const props = withDefaults(defineProps<Props>(), {
  variant: "filled",
  density: "comfortable",
  clearable: true,
  disabled: false,
  textFieldClass: "mb-4",
});

const emit =
  defineEmits<(e: "update:modelValue", value: string | null) => void>();

const menu = ref(false);

// Separate input value for the text field display
const inputValue = ref<string>("");

// Sync inputValue when modelValue changes externally (e.g. from date picker)
watch(
  () => props.modelValue,
  (newVal) => {
    if (!newVal) {
      inputValue.value = "";
      return;
    }
    const date = new Date(newVal);
    if (!isNaN(date.getTime())) {
      inputValue.value = date.toLocaleDateString("de-DE", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      });
    }
  },
  { immediate: true }
);

function handleDateChange(newDate: string | null) {
  emit("update:modelValue", newDate);
  menu.value = false;
}

function handleManualInput(value: string | null) {
  if (!value) {
    inputValue.value = "";
    emit("update:modelValue", null);
    return;
  }

  // Auto-insert dots after day and month
  let formatted = value.replace(/[^\d.]/g, ""); // nur Ziffern und Punkte
  const digits = formatted.replace(/\./g, "");

  if (digits.length <= 2) {
    formatted = digits;
  } else if (digits.length <= 4) {
    formatted = digits.slice(0, 2) + "." + digits.slice(2);
  } else {
    formatted =
      digits.slice(0, 2) + "." + digits.slice(2, 4) + "." + digits.slice(4, 8);
  }

  inputValue.value = formatted;

  // Only parse when full date is entered (DD.MM.YYYY = 10 chars)
  if (formatted.length === 10) {
    const parts = formatted.split(".");
    if (parts.length === 3) {
      const day = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const year = parseInt(parts[2], 10);
      const date = new Date(year, month, day);
      if (
        !isNaN(date.getTime()) &&
        date.getDate() === day &&
        date.getMonth() === month &&
        date.getFullYear() === year
      ) {
        // Build ISO date string directly to avoid timezone offset issues
        const isoDate = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
        emit("update:modelValue", isoDate);
        return;
      }
    }
  }
  // Incomplete or invalid date: reset modelValue so watchers trigger
  emit("update:modelValue", null);
}

function handleClear() {
  inputValue.value = "";
  emit("update:modelValue", null);
}
</script>
