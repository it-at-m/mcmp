import type { BaseIntegrationConfig } from "./BaseIntegrationConfig";

export interface Cloud extends BaseIntegrationConfig {
  name: string;
  fqdn: string;
  serverGui: string;
  cloudType: string;
  enabled: boolean;
  locked: boolean;
  apiUsername: string;
  apiPassword?: string;
  configInfobloxId: number;
  configBaasId: number;
  type: "cloud";
}
