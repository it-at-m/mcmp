<template>
  <common-dialog
    :model-value="dialog"
    :title="title"
    :icon="icon"
    max-width="600"
    show-actions
    :submit-activated="checked"
    @dialog-confirm="onDo"
    @dialog-cancel="onCancel"
  >
    <strong>{{ text }}</strong>
    <v-spacer />
    <v-form
      ref="form"
      @submit.prevent="onDo"
    >
      <v-checkbox
        v-model="checked"
        :label="checkboxText"
        :rules="[mustBeChecked]"
        required
      />
    </v-form>
  </common-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";

import CommonDialog from "@/components/common/CommonDialog.vue";

const props = defineProps<{
  title: string;
  text: string;
  checkboxText: string;
  icon: string;
}>();

const emits = defineEmits<{
  (e: "do"): void;
  (e: "cancel"): void;
}>();

const dialog = ref(true);
const checked = ref(false);
const form = ref<HTMLFormElement>();

const mustBeChecked = (v: boolean) => v || "";

function onCancel() {
  checked.value = false;
  dialog.value = false;
  emits("cancel");
}

function onDo() {
  form.value?.validate().then((validation: { valid: boolean }) => {
    if (validation.valid && checked.value) {
      emits("do");
      dialog.value = false;
      checked.value = false;
    }
  });
}
</script>
