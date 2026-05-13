import type { BaseIntegrationConfig } from "./BaseIntegrationConfig";

export interface BaasConfig extends BaseIntegrationConfig {
  type: "baas";
  enabled: boolean;
}
