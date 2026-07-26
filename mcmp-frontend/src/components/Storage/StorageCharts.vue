<template>
  <v-container class="pa-0">
    <v-chart
      v-if="
        (selectedStorageItem.type == 'NFS' ||
          selectedStorageItem.type == 'CIFS') &&
        !selectedStorageItem.isWorm
      "
      style="width: 100%; height: clamp(80px, 12vh, 110px)"
      :option="nfsCifsChartOption"
      autoresize
    />
    <v-chart
      v-else-if="
        selectedStorageItem.type == 'QTREE' ||
        ((selectedStorageItem.type == 'NFS' ||
          selectedStorageItem.type == 'CIFS') &&
          selectedStorageItem.isWorm)
      "
      style="width: 100%; height: clamp(80px, 12vh, 110px)"
      :option="qtreeChartOption"
      autoresize
    />
    <v-chart
      v-else-if="selectedStorageItem.type == 'S3'"
      style="width: 100%; height: clamp(80px, 12vh, 110px)"
      :option="s3ChartOption"
      autoresize
    />
  </v-container>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage.ts";

import { BarChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from "echarts/components";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { computed } from "vue";
import VChart from "vue-echarts";
import { useTheme } from "vuetify/framework";

import { useFormatter } from "@/composables/formatter.ts";

use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  BarChart,
  CanvasRenderer,
]);

const props = defineProps<{
  selectedStorageItem: UnifiedStorageItem;
  previewSizeGb?: number;
  previewSnapshotReservePercent?: number;
}>();
const theme = useTheme();
const formatter = useFormatter();

const bytesPerGb = 1024 * 1024 * 1024;

interface TooltipFormatterParam {
  marker: string;
  seriesName: string;
  value: number;
}

function clampToPositive(value?: number): number {
  return Math.max(value ?? 0, 0);
}

const previewSizeBytes = computed(() => {
  if (props.previewSizeGb !== undefined) {
    return Math.max(props.previewSizeGb * bytesPerGb, 0);
  }

  return clampToPositive(props.selectedStorageItem.size);
});

const snapshotReservePercent = computed(() => {
  return (
    props.previewSnapshotReservePercent ??
    props.selectedStorageItem.spaceSnapshotReservePercent ??
    0
  );
});

const snapshotReserveBytes = computed(() => {
  return Math.max(
    previewSizeBytes.value * (snapshotReservePercent.value / 100),
    0
  );
});

function getChartOption(data: { value: number; name: string }[]) {
  const isDark = theme.global.current.value.dark;
  const textColor = isDark ? "#ffffff" : "#000000";
  const total = data.reduce((sum, d) => sum + d.value, 0);

  return {
    grid: {
      left: 0,
      right: 16,
      top: 2,
      bottom: 28,
    },
    xAxis: {
      type: "value",
      min: 0,
      max: total > 0 ? total : 1,
      show: false,
    },
    yAxis: {
      type: "category",
      data: ["Ressourcen"],
      show: false,
    },
    tooltip: {
      trigger: "item",
      formatter: (params: TooltipFormatterParam) => {
        const percent =
          total > 0 ? ((params.value / total) * 100).toFixed(1) : "0";
        return `${params.marker}${params.seriesName} ${formatter.formatBytesSmart(params.value)} (${percent}%)`;
      },
    },
    legend: {
      orient: "horizontal",
      bottom: 0,
      left: "center",
      textStyle: { color: textColor },
      selectedMode: false,
      formatter: (name: string) => {
        const item = data.find((d) => d.name === name);
        return item
          ? `${name}: ${formatter.formatBytesSmart(item.value)}`
          : name;
      },
    },
    series: data.map((d) => ({
      name: d.name,
      type: "bar",
      stack: "total",
      barWidth: 32,
      data: [d.value],
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: "rgba(0, 0, 0, 0.5)",
        },
      },
    })),
  };
}

const nfsCifsChartOption = computed(() => {
  const logicalUsedByAfs = clampToPositive(
    props.selectedStorageItem.spaceLogicalUsedByAfs
  );

  const snapshotUsed = clampToPositive(
    props.selectedStorageItem.spaceSnapshotUsed
  );

  if (snapshotReservePercent.value === 0 && snapshotUsed === 0) {
    return getChartOption([
      {
        value: logicalUsedByAfs,
        name: "Aktives Dateisystem: regulär belegt",
      },
      {
        value: Math.max(previewSizeBytes.value - logicalUsedByAfs, 0),
        name: "Aktives Dateisystem: frei",
      },
    ]);
  }

  if (snapshotReservePercent.value === 0) {
    return getChartOption([
      {
        value: logicalUsedByAfs,
        name: "Aktives Dateisystem: regulär belegt",
      },
      {
        value: Math.max(
          previewSizeBytes.value - logicalUsedByAfs - snapshotUsed,
          0
        ),
        name: "Aktives Dateisystem: frei",
      },
      {
        value: snapshotUsed,
        name: "Aktives Dateisystem: belegt durch Snapshots",
      },
    ]);
  }

  const reserveSize = snapshotReserveBytes.value;
  const snapshotOverflowIntoAfs = Math.max(snapshotUsed - reserveSize, 0);

  return getChartOption([
    {
      value: logicalUsedByAfs,
      name: "Aktives Dateisystem: regulär belegt",
    },
    {
      value: Math.max(
        previewSizeBytes.value -
          reserveSize -
          logicalUsedByAfs -
          snapshotOverflowIntoAfs,
        0
      ),
      name: "Aktives Dateisystem: frei",
    },
    {
      value: Math.max(reserveSize - snapshotUsed, 0),
      name: "Snapshotreservierung: frei",
    },
    {
      value: Math.min(snapshotUsed, reserveSize),
      name: "Snapshotreservierung: belegt",
    },
    {
      value: snapshotOverflowIntoAfs,
      name: "Aktives Dateisystem: belegt durch Snapshots",
    },
  ]);
});

const qtreeChartOption = computed(() => {
  const used = clampToPositive(props.selectedStorageItem.used);
  return getChartOption([
    {
      value: used,
      name: "Belegt",
    },
    {
      value: Math.max(previewSizeBytes.value - used, 0),
      name: "Frei",
    },
  ]);
});

const s3ChartOption = computed(() => {
  const used = clampToPositive(props.selectedStorageItem.used);
  return getChartOption([
    {
      value: used,
      name: "Belegt",
    },
    {
      value: Math.max(previewSizeBytes.value - used, 0),
      name: "Frei",
    },
  ]);
});
</script>
