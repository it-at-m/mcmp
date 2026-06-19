<template>
  <v-container>
    <v-chart
      v-if="
        (selectedStorageItem.type == 'NFS' ||
          selectedStorageItem.type == 'CIFS') &&
        !selectedStorageItem.isWorm
      "
      style="width: 100%; height: clamp(250px, 40vh, 400px)"
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
      style="width: 100%; height: clamp(250px, 40vh, 400px)"
      :option="qtreeChartOption"
      autoresize
    />
    <v-chart
      v-else-if="selectedStorageItem.type == 'S3'"
      style="width: 100%; height: clamp(250px, 40vh, 400px)"
      :option="s3ChartOption"
      autoresize
    />
  </v-container>
</template>
<script setup lang="ts">
import type { UnifiedStorageItem } from "@/types/Storage.ts";

import { PieChart } from "echarts/charts";
import {
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
  PieChart,
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

  return {
    tooltip: {
      trigger: "item",
      formatter: (params: any) => {
        return `${params.marker}${params.name} ${formatter.formatBytesSmart(params.value)} (${params.percent}%)`;
      },
    },
    legend: {
      orient: "horizontal",
      bottom: 0,
      left: "center",
      textStyle: { color: textColor },
      selectedMode: false,
    },
    series: [
      {
        name: "Ressourcen",
        type: "pie",
        radius: "65%",
        center: ["50%", "45%"],
        label: {
          color: textColor,
        },
        data: data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: "rgba(0, 0, 0, 0.5)",
          },
        },
      },
    ],
  };
}

const nfsCifsChartOption = computed(() => {
  const logicalUsedByAfs = clampToPositive(
    props.selectedStorageItem.spaceLogicalUsedByAfs
  );

  if (snapshotReservePercent.value === 0) {
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

  const snapshotUsed = clampToPositive(
    props.selectedStorageItem.spaceSnapshotUsed
  );
  const reserveSize = snapshotReserveBytes.value;
  const snapshotOverflowIntoAfs = Math.max(snapshotUsed - reserveSize, 0);

  return getChartOption([
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
      value: logicalUsedByAfs,
      name: "Aktives Dateisystem: regulär belegt",
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