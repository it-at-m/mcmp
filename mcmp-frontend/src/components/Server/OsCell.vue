<template>
  <v-tooltip
    :text="props.osFullName || ''"
    :disabled="!props.osFullName"
  >
    <template #activator="{ props: tooltipProps }">
      <v-sheet
        width="30"
        height="30"
        class="os-icon"
      >
        <img
          v-if="icon"
          :src="icon"
          :class="['os-icon', sizeClass]"
          :alt="`Betriebssystem: ${props.osFullName || ''}`"
          v-bind="tooltipProps"
          @error="handleImageError"
        />
      </v-sheet>
    </template>
  </v-tooltip>
</template>

<script setup lang="ts">
import { computed } from "vue";

import almalinuxIcon from "@/assets/almalinux.png";
import centosIcon from "@/assets/centos.ico";
import debianIcon from "@/assets/debian.svg";
import linuxIcon from "@/assets/linux.svg";
import otherIcon from "@/assets/other.svg";

const windowsIcon =
  "https://monitoring.muenchen.de/lhmmon/check_mk/themes/facelift/images/icon_windows_msi.svg";
const rhelIcon =
  "https://monitoring.muenchen.de/lhmmon/check_mk/themes/facelift/images/icon_linux_rpm.svg";
const ubuntuIcon =
  "https://monitoring.muenchen.de/lhmmon/check_mk/themes/facelift/images/icon_linux_deb.svg";
const vmwareIcon =
  "https://monitoring.muenchen.de/lhmmon/check_mk/themes/facelift/images/icon_vsphere.svg";

const props = defineProps<{
  osFullName: string;
  size?: "small" | "normal";
}>();

const handleImageError = (e: Event) => {
  const target = e.target as HTMLImageElement;
  if (target.src !== otherIcon) {
    target.src = otherIcon;
  }
};

const osMap = [
  { match: /windows/i, icon: windowsIcon },
  { match: /red ?hat/i, icon: rhelIcon },
  { match: /rhel/i, icon: rhelIcon },
  { match: /ubuntu/i, icon: ubuntuIcon },
  { match: /debian/i, icon: debianIcon },
  { match: /centos/i, icon: centosIcon },
  { match: /suse/i, icon: linuxIcon },
  { match: /sles/i, icon: linuxIcon },
  { match: /oracle/i, icon: linuxIcon },
  { match: /fedora/i, icon: linuxIcon },
  { match: /almalinux/i, icon: almalinuxIcon },
  { match: /vmware/i, icon: vmwareIcon },
  { match: /linux/i, icon: linuxIcon },
];

const match = computed(() => {
  if (!props.osFullName) return undefined;
  return osMap.find((m) => m.match.test(props.osFullName));
});
const icon = computed(() => match.value?.icon || otherIcon);
const sizeClass = computed(() => {
  if (props.osFullName?.includes("Windows")) return "os-icon-x-small";
  if (props.size === "small") return "os-icon-small";
  return "";
});
</script>

<style scoped>
.os-icon {
  justify-content: center;
  align-items: center;
  display: flex;
  background-color: transparent;
}

.os-icon-small {
  width: 25px !important;
  height: 25px !important;
}

.os-icon-x-small {
  width: 22px !important;
  height: 22px !important;
}
</style>
