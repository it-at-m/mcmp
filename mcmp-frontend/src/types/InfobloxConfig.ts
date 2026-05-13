import type { BaseIntegrationConfig } from "./BaseIntegrationConfig";

export interface InfobloxConfig extends BaseIntegrationConfig {
  type: "infoblox";
  apiUsername: string;
  apiPassword?: string;
}
