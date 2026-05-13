<template>
  <common-card title="Job Statistics">
    <v-row class="mb-4">
      <v-col
        cols="12"
        md="2"
      >
        <v-text-field
          v-model="startDate"
          label="Startdatum"
          type="date"
          required
        ></v-text-field>
      </v-col>
      <v-col
        cols="12"
        md="2"
      >
        <v-text-field
          v-model="endDate"
          label="Enddatum"
          type="date"
          required
        ></v-text-field>
      </v-col>
      <v-col
        cols="12"
        md="2"
        class="d-flex align-center"
      >
        <v-btn
          @click="loadStatistics"
          :loading="loading"
          color="primary"
          class="mr-2"
        >
          Lade Statistik
        </v-btn>
        <v-btn
          @click="exportToCsv"
          :disabled="sortedStatistics.length === 0"
          color="secondary"
          variant="outlined"
        >
          Export CSV
        </v-btn>
      </v-col>
    </v-row>
    <v-data-table
      :headers="headers"
      :items="sortedStatistics"
      class="elevation-1"
      :loading="loading"
      density="compact"
      no-data-text="Keine Daten gefunden"
      :items-per-page="-1"
      :row-props="
        (item) => (item.item.action === 'SUMME' ? { class: 'sum-row' } : {})
      "
      :hide-default-footer="true"
    >
      <template #headers>
        <tr>
          <th
            v-for="header in headers"
            :key="header.key"
            class="v-data-table__th"
            style="cursor: pointer; user-select: none; text-align: center"
            @click="toggleSort(header.key as string)"
          >
            {{ header.title }}
            <span v-if="currentSortBy.key === header.key">
              {{ currentSortBy.order === "asc" ? "↑" : "↓" }}
            </span>
          </th>
        </tr>
      </template>
      <template #item="{ item, columns }">
        <tr :class="item.action === 'SUMME' ? 'sum-row' : ''">
          <td
            v-for="col in columns"
            :key="col.key"
            :class="[
              col.key === 'totalJobs' ? 'total-jobs-cell' : '',
              col.key === 'changeRequired' ? 'text-center' : '',
            ]"
            :style="{
              textAlign:
                col.key === 'action'
                  ? 'left'
                  : col.key === 'changeRequired'
                    ? 'center'
                    : 'right',
            }"
          >
            <template v-if="col.key === 'changeRequired'">
              <v-icon
                :icon="
                  item.changeRequired
                    ? mdiCheckboxMarked
                    : mdiCheckboxBlankOutline
                "
                :color="item.changeRequired ? 'success' : 'default'"
              />
            </template>
            <template v-else>
              {{ (item as any)[col.key as string] }}
            </template>
          </td>
        </tr>
      </template>
    </v-data-table>
    <div
      v-if="chartData.length > 0"
      class="mt-6"
    >
      <div class="text-subtitle-1 mb-0 font-weight-medium">
        Anzahl Jobs je Aktion
      </div>
      <v-chart
        :option="chartOption"
        :style="{ height: chartHeight, width: '100%' }"
        autoresize
      />
    </div>
    <div
      v-if="durationChartData.length > 0"
      class="mt-6"
    >
      <div class="text-subtitle-1 mb-0 font-weight-medium">
        AWX-Laufzeit AVG (10%-90%) je Aktion in Sek.
      </div>
      <v-chart
        :option="durationChartOption"
        :style="{ height: durationChartHeight, width: '100%' }"
        autoresize
      />
    </div>
  </common-card>
</template>

<script setup lang="ts">
import type JobStatistics from "@/types/JobStatistics";
import type { DataTableHeader } from "vuetify/framework";

import { mdiCheckboxBlankOutline, mdiCheckboxMarked } from "@mdi/js";
import { BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent } from "echarts/components";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { computed, onMounted, ref } from "vue";
import VChart from "vue-echarts";
import { useTheme } from "vuetify";

import jobService from "@/api/jobService";
import CommonCard from "@/components/common/CommonCard.vue";

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent]);

const theme = useTheme();
const loading = ref(false);
const statistics = ref<JobStatistics[]>([]);
const startDate = ref("2025-12-01");
const endDate = ref("2026-03-03");

const currentSortBy = ref<{ key: string; order: "asc" | "desc" }>({
  key: "action",
  order: "asc",
});

const sortedStatistics = computed(() => {
  const summe = statistics.value.find((item) => item.action === "SUMME");
  const others = statistics.value.filter((item) => item.action !== "SUMME");

  const { key, order } = currentSortBy.value;
  const desc = order === "desc";

  others.sort((a, b) => {
    const aVal = (a as any)[key] ?? "";
    const bVal = (b as any)[key] ?? "";
    const aStr = typeof aVal === "string" ? aVal.toLowerCase() : aVal;
    const bStr = typeof bVal === "string" ? bVal.toLowerCase() : bVal;
    if (aStr < bStr) return desc ? 1 : -1;
    if (aStr > bStr) return desc ? -1 : 1;
    return 0;
  });

  if (summe) others.push(summe);
  return others;
});

function toggleSort(key: string) {
  if (currentSortBy.value.key === key) {
    currentSortBy.value = {
      key,
      order: currentSortBy.value.order === "asc" ? "desc" : "asc",
    };
  } else {
    currentSortBy.value = { key, order: "asc" };
  }
}

const headers = ref<DataTableHeader[]>([
  { title: "Aktion", key: "action", align: "left", sortable: true },
  {
    title: "Change erforderlich",
    key: "changeRequired",
    align: "center",
    sortable: true,
  },
  {
    title: "Change Status Rejected",
    key: "changeStatusRejected",
    align: "end",
    sortable: true,
  },
  {
    title: "Change Status Canceled",
    key: "changeStatusCanceled",
    align: "end",
    sortable: true,
  },
  {
    title: "Change Status Skipped",
    key: "changeStatusSkipped",
    align: "end",
    sortable: true,
  },
  {
    title: "Change Status Approved",
    key: "changeStatusApproved",
    align: "end",
    sortable: true,
  },
  {
    title: "Change Status Failed",
    key: "changeStatusFailed",
    align: "end",
    sortable: true,
  },
  {
    title: "Anzahl Jobs",
    key: "totalJobs",
    align: "end",
    sortable: true,
    cellClass: "total-jobs-cell",
  },
  {
    title: "AWX Status Failed",
    key: "awxStatusFailed",
    align: "end",
    sortable: true,
  },
  {
    title: "AWX Status Successful",
    key: "awxStatusSuccessful",
    align: "end",
    sortable: true,
  },
  {
    title: "AWX-Laufzeit MIN in Sek.",
    key: "awxDurationMin",
    align: "end",
    sortable: true,
  },
  {
    title: "AWX-Laufzeit MAX in Sek.",
    key: "awxDurationMax",
    align: "end",
    sortable: true,
  },
  {
    title: "AWX-Laufzeit AVG in Sek.",
    key: "awxDurationMittelwert",
    align: "end",
    sortable: true,
  },
  {
    title: "AWX-Laufzeit AVG (10%-90%) in Sek. ",
    key: "awxDurationTrimmedAvg",
    align: "end",
    sortable: true,
  },
]);

function loadStatistics() {
  if (!startDate.value || !endDate.value) {
    return;
  }
  jobService
    .getJobStatistics(loading, startDate.value, endDate.value)
    .then((response) => {
      statistics.value = response;
    })
    .catch(() => {
      statistics.value = [];
    });
}

onMounted(() => {
  loadStatistics();
});

function exportToCsv() {
  const columnOrder = headers.value.map((h) => h.key as string);
  const columnTitles = headers.value.map((h) => h.title);

  const rows = sortedStatistics.value.map((item) =>
    columnOrder.map((key) => {
      const val = (item as any)[key];
      if (typeof val === "boolean") return val ? "Ja" : "Nein";
      if (val === null || val === undefined) return "";
      return String(val);
    })
  );

  const csvContent = [columnTitles, ...rows]
    .map((row) => row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(";"))
    .join("\n");

  const blob = new Blob(["\uFEFF" + csvContent], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `job-statistics_${startDate.value}_${endDate.value}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

const chartData = computed(() =>
  [...statistics.value]
    .filter((item) => item.action !== "SUMME")
    .sort((a, b) =>
      b.action.toLowerCase().localeCompare(a.action.toLowerCase())
    )
);

const chartHeight = computed(
  () => `${Math.max(chartData.value.length * 36 + 60, 200)}px`
);

const chartOption = computed(() => {
  const isDark = theme.global.current.value.dark;
  const textColor = isDark ? "#ffffff" : "#000000";

  const actions = chartData.value.map((d) => d.action);

  const seriesConfig = [
    { name: "Rejected", key: "changeStatusRejected", color: "#D32F2F" },
    { name: "Canceled", key: "changeStatusCanceled", color: "#F57C00" },
    { name: "Skipped", key: "changeStatusSkipped", color: "#FBC02D" },
    { name: "Approved", key: "changeStatusApproved", color: "#388E3C" },
    { name: "Failed", key: "changeStatusFailed", color: "#7B1FA2" },
  ];

  return {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any[]) => {
        const total = chartData.value.find((d) => d.action === params[0]?.name)?.totalJobs ?? 0;
        const lines = params.map(
          (p: any) => `<span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:${p.color};"></span>${p.seriesName}: <b>${p.value}</b>`
        );
        lines.push(`<hr style="margin:4px 0"/><b>Anzahl Jobs: ${total}</b>`);
        return `<div>${params[0]?.name}<br/>${lines.join("<br/>")}</div>`;
      },
    },
    legend: {
      data: seriesConfig.map((s) => s.name),
      textStyle: { color: textColor },
    },
    grid: {
      top: "1%",
      left: "3%",
      right: "8%",
      bottom: "5%",
      containLabel: true,
    },
    xAxis: {
      type: "value",
      name: "Anzahl Jobs",
      nameTextStyle: { color: textColor },
      axisLabel: { color: textColor },
    },
    yAxis: {
      type: "category",
      data: actions,
      axisLabel: { interval: 0, color: textColor },
    },
    series: seriesConfig.map((s) => ({
      name: s.name,
      type: "bar",
      stack: "changeStatus",
      data: chartData.value.map((d) => (d as any)[s.key] ?? 0),
      label: {
        show: false,
      },
      itemStyle: {
        color: s.color,
      },
    })),
  };
});

const durationChartData = computed(() =>
  [...statistics.value]
    .filter(
      (item) => item.action !== "SUMME" && item.awxDurationTrimmedAvg != null
    )
    .map((item) => ({
      name: item.action,
      value: item.awxDurationTrimmedAvg ?? 0,
    }))
    .sort((a, b) => b.name.toLowerCase().localeCompare(a.name.toLowerCase()))
);

const durationChartHeight = computed(
  () => `${Math.max(durationChartData.value.length * 36 + 60, 200)}px`
);

const durationChartOption = computed(() => {
  const isDark = theme.global.current.value.dark;
  const textColor = isDark ? "#ffffff" : "#000000";

  return {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any[]) => {
        const p = params[0];
        return `${p.name}: ${p.value} Sek.`;
      },
    },
    grid: {
      top: "1%",
      left: "3%",
      right: "8%",
      bottom: "1%",
      containLabel: true,
    },
    xAxis: {
      type: "value",
      name: "Sekunden",
      nameTextStyle: { color: textColor },
      axisLabel: { color: textColor },
    },
    yAxis: {
      type: "category",
      data: durationChartData.value.map((d) => d.name),
      axisLabel: { interval: 0, color: textColor },
    },
    series: [
      {
        name: "AWX-Laufzeit AVG (10%-90%)",
        type: "bar",
        data: durationChartData.value.map((d) => d.value),
        label: {
          show: true,
          position: "right",
          color: textColor,
          textBorderWidth: 0,
        },
        itemStyle: {
          color: "#388E3C",
        },
      },
    ],
  };
});
</script>

<style scoped>
:deep(.sum-row td) {
  background-color: #f5f5f5 !important;
  color: rgba(0, 0, 0, 0.87) !important;
}

.v-theme--dark :deep(.sum-row td) {
  background-color: #424242 !important;
  color: rgba(255, 255, 255, 0.87) !important;
}

:deep(.total-jobs-cell) {
  background-color: #f5f5f5 !important;
  color: rgba(0, 0, 0, 0.87) !important;
}

.v-theme--dark :deep(.total-jobs-cell) {
  background-color: #424242 !important;
  color: rgba(255, 255, 255, 0.87) !important;
}
</style>
