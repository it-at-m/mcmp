import type Backup from "@/types/Backup";
import type { Ref } from "vue";

import { defaultResponseHandler, getConfig } from "@/api/fetch-utils";
import { BACKUP_BASE, getApiBase } from "@/constants";

export default {
  getBackupsByServerId(
    loading: Ref<boolean>,
    serverId: number
  ): Promise<Backup[]> {
    loading.value = true;
    return fetch(`${getApiBase()}${BACKUP_BASE}/${serverId}`, getConfig())
      .then((response) => {
        defaultResponseHandler(response);
        return response.json();
      })
      .finally(() => {
        loading.value = false;
      });
  },
};
