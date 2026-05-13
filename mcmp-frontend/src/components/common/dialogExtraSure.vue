<template>
  <CommonDialog
    :model-value="dialog"
    :title="title"
    :icon="icon"
    max-width="600"
    @dialogConfirm="onDo"
    @dialog-cancel="onCancel"
    show-actions
    :submitActivated="checked"
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
  </CommonDialog>
</template>

<script setup lang="ts">
import { mdiAlertCircle } from "@mdi/js";
import { ref } from "vue";
import CommonDialog from "@/components/common/CommonDialog.vue";
import CommonWarning from "@/components/common/CommonAlert.vue";

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
