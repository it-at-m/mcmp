import type { AwxConfig } from "@/types/AwxConfig";
import type { SnowConfig } from "@/types/SnowConfig";





export default class Action {
  [key: string]: any;
  constructor(
    public snowConfig: SnowConfig | null,
    public awxConfig: AwxConfig | null,
    public identifier: string,
    public title: string,
    public description: string,
    public comment: string,
    public errorTitle: string,
    public errorDescription: string,
    public executionTitle: string,
    public executionDescription: string,
    public successTitle: string,
    public successDescription: string,
    public enabled: boolean,
    public quickdiscovery: boolean,
    public serverInstallation: boolean,
    public changeRequired: boolean,
    public changeType: "normal" | "standard",
    public changeAction: string | null,
    public changeTemplate: string | null,
    public changeJustification: string | null,
    public changeImplementationPlan: string | null,
    public changeRiskImpactAnalysis: string | null,
    public changeBackoutPlan: string | null,
    public awxJobEnabled: boolean,
    public awxTemplateType: "template" | "workflow",
    public awxTemplateId: number,
    public awxInventoryId: number,
    public awxCredentials: string,
    public awxJobType: string,
    public awxLimit: string,
    public awxJobTags: string,
    public awxSkipTags: string,
    public awxExtraVars: string,
    public awxScmBranch: string,
    public awxVerbosity: number,
    public awxTimeout: number,
    public awxForks: number,
    public awxJobSliceCount: number,
    public awxExecutionEnvironment: number,
    public awxInstanceGroups: string,
    public awxLabels: string,
    public awxEstimatedRuntime: number,
    public isLowPriority: boolean,
    public createIncidents: boolean
  ) {}
}
