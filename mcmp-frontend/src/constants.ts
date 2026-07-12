interface RuntimeConfig {
  apiBaseUrl: string;
  ad2ImageUrl: string;
}

let runtimeConfig: RuntimeConfig | null = null;

export function setRuntimeConfig(config: RuntimeConfig) {
  runtimeConfig = config;
}

export function getApiBase(): string {
    return runtimeConfig?.apiBaseUrl || "/api";
}

const API = "/api/backend-service";
export const SERVER_BASE = `${API}/server`;
export const DISK_BASE = `${API}/disk`;
export const MOUNTPOINT_BASE = `${API}/mount-point`;
export const SNAPSHOT_BASE = `${API}/snapshot`;
export const NIC_BASE = `${API}/nic`;
export const BACKUP_BASE = `${API}/backup`;
export const INFOBLOX_CONFIG_BASE = `${API}/infobloxConfig`;
export const INFOBLOX_FQDN_BASE = `${API}/infobloxFQDN`;
export const AWX_BASE = `${API}/awxConfig`;
export const SNOW_BASE = `${API}/snowConfig`;
export const BAAS_BASE = `${API}/baasConfig`;
export const ACTION_BASE = `${API}/action`;
export const USER_BASE = `${API}/user`;
export const JOB_BASE = `${API}/job`;
export const JOB_NODE_BASE = `${API}/job/node`;
export const NETWORK_BASE = `${API}/network`;
export const PRICE_BASE = `${API}/price`;
export const APPSERVICE_BASE = `${API}/appservice`;
export const CLOUD_BASE = `${API}/cloud`;
export const APP_VERSION_BASE = `${API}/version`;
export const CHANGELOG_BASE = `${API}/changelogs`;
export const FAQ_CATEGORY_BASE = `${API}/faq-categories`;
export const FAQ_BASE = `${API}/faqs`;
export const STORAGE_BASE = `${API}/storage`;
export const LOADBALANCER_BASE = `${API}/loadbalancer`;
export const TESTENV_BASE = `${API}/testenv`;
export const APP_CONFIG_BASE = `${API}/app-config`;

export const AD2IMAGE_URL = import.meta.env.VITE_AD2IMAGE_URL;

export const SNACKBAR_DEFAULT_TIMEOUT = 5000;

export const APPSERVICE_EXPLAIN_URL= "https://go.muenchen.de/sp/KB0023236";

export const enum STATUS_INDICATORS {
  SUCCESS = "success",
  INFO = "info",
  WARNING = "warning",
  ERROR = "error",
}
