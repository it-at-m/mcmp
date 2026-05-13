// @ts-expect-error: "TS2307 cannot find module" is a false positive here
import "vuetify/styles";

import type { VueI18nAdapterParams } from "vuetify/locale/adapters/vue-i18n";

import { useI18n } from "vue-i18n";
import { createVuetify } from "vuetify";
import { aliases, mdi } from "vuetify/iconsets/mdi-svg";
import { VDateInput } from "vuetify/labs/components";
import { createVueI18nAdapter } from "vuetify/locale/adapters/vue-i18n";

import i18n from "@/plugins/i18n";

export default createVuetify({
  components: {
    VDateInput,
  },
  icons: {
    defaultSet: "mdi",
    aliases,
    sets: {
      mdi,
    },
  },
  locale: {
    adapter: createVueI18nAdapter({ i18n, useI18n } as VueI18nAdapterParams),
  },
  theme: {
    defaultTheme: "system",
    themes: {
      light: {
        colors: {
          bg_icon: "#FFFFFF",
          bg_light: "hsl(0, 0%, 100%)",
          bg: "hsl(0, 0%, 95%)",
          bg_dark: "hsl(0, 0%, 90%)",
          text: "#000000",
          select: "hsl(0, 0%, 90%)",
          primary: "#333333",
          secondary: "#FFCC00",
          cancel: "#333333",
          accent: "#3D74B6",
          success: "#0E810E",
          error: "#E41A0C",
          do: "#7ba4d9",
          btn_green: "#0E810E",
          btn_red: "#E41A0C",
          notice_red: "#E41A0C",
          //-----------------------
          light_red: "#F86F63",
          _red: "#E41A0C",
          light_green: "#13AE13",
          _green: "#0E810E",
          _blue: "#3D74B6",
          light_blue: "#7ba4d9",
          link: "#1976d2",
          link_inverted: "#90caf9",
        },
      },
      dark: {
        colors: {
          bg_icon: "#FFFFFF",
          bg_light: "hsl(0, 0%, 15%)",
          bg: "hsl(0, 0%, 10%)",
          bg_dark: "hsl(0, 0%, 5%)",
          text: "#FFFFFF",
          select: "hsl(0, 0%, 25%)",
          primary: "#FFFFFF",
          secondary: "#FFCC00",
          cancel: "#FFFFFF",
          accent: "#7ba4d9",
          success: "#0E810E",
          error: "#E41A0C",
          do: "#7ba4d9",
          btn_green: "#0E810E",
          btn_red: "#E41A0C",
          notice_red: "#E41A0C",
          //-----------------------
          light_red: "#F86F63",
          _red: "#E41A0C",
          light_green: "#13AE13",
          _green: "#0E810E",
          _blue: "#3D74B6",
          light_blue: "#7ba4d9",
          link: "#90caf9",
          link_inverted: "#1976d2",
        },
      },
    },
  },
});
