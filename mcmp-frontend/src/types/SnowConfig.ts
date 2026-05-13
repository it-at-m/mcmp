import type { BaseIntegrationConfig } from "./BaseIntegrationConfig";

export interface SnowConfig extends BaseIntegrationConfig {
  type: "snow";
  proxy: string;
  useProxy: boolean;
  enabled: boolean;
  apiClientAuthUrl: string;
  apiClientId: string;
  apiClientSecret?: string;
}
