import type { BaseIntegrationConfig } from "./BaseIntegrationConfig";

export interface AwxConfig extends BaseIntegrationConfig {
  type: "awx";
  enabled: boolean;
  apiUsername: string;
  apiPassword?: string;
}
