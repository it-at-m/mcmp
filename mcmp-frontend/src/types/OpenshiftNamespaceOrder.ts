import type AppserviceList from "@/types/AppserviceList.ts";

export type NodeSelectorType = "worker" | "stargate" | "holyplace";
export type IngressType = "web2tier" | "eai" | "sysadm" | "swvt" | "monitor";
export type LoggingType = "no" | "yes" | "jsonparsing";

export default class OpenshiftNamespaceOrder {
  constructor(
    // Allgemeines
    public appservice: AppserviceList | null,

    // Namespace
    public namespaceName: string,
    public description: string,
    public nodeSelector: NodeSelectorType | null,

    // Netzwerk
    public ingress: IngressType | null,

    // Hardware
    public cpuLimit: string,
    public memoryLimit: string,
    public pvLimit: number,
    public podLimit: number,

    // Optionales
    public logging: LoggingType,
    public quayOrga: string
  ) {}
}
