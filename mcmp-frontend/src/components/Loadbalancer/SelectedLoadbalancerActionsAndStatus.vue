<template>
  <detail-page-header
    v-if="lb"
    :appservice-id="lb.appservices?.[0]?.id ?? null"
    :appservice-name="lb.appservices?.[0]?.name ?? null"
    :appservice-count="lb.appservices?.length ?? 0"
    :current-icon="mdiSitemap"
    :current-label="lb.name"
  >
    <template
      v-if="hasActions"
      #actions
    >
      <loadbalancer-delete-btn
        v-if="hasActions"
        :lb="lb"
      />
    </template>

    <template #statusChips>
      <appservice-assignment-status-chips
        :can-edit="lb.canEdit"
        :assigned-count="lb.appservices?.length ?? 0"
        entity-label="Loadbalancer"
      />
    </template>
  </detail-page-header>
</template>

<script setup lang="ts">
import type { LoadbalancerDetail } from "@/types/LoadbalancerDetail";

import { mdiSitemap } from "@mdi/js";
import { computed, onMounted, ref } from "vue";

import testenvService from "@/api/testenvService";
import AppserviceAssignmentStatusChips from "@/components/common/AppserviceAssignmentStatusChips.vue";
import DetailPageHeader from "@/components/common/DetailPageHeader.vue";
import LoadbalancerDeleteBtn from "@/components/Loadbalancer/LoadbalancerDeleteBtn.vue";

const props = defineProps<{
  lb: LoadbalancerDetail;
}>();

const testEnv = ref(false);
const loadingTestEnv = ref(false);

onMounted(() => {
  testenvService.getTestEnabled(loadingTestEnv).then((enabled) => {
    testEnv.value = enabled;
  });
});

const hasActions = computed(
  () => props.lb.canEdit && props.lb.appservices?.length === 1 && testEnv.value
);
</script>
