import type { EnvironmentType } from "@/types/EnvironmentType.ts";
import type { ServerListExtended } from "@/types/ServerListExtended.ts";

export default class Appservice {
  constructor(
    public id: number,
    public name: string,
    public number: string,
    public sysId: string,
    public usedFor: string,
    public environment: EnvironmentType,
    public ownedByUsername: string,
    public ownedByName: string,
    public serviceOwnerDelegateUsername: string,
    public serviceOwnerDelegateName: string,
    public changeGroupId: number,
    public changeGroupName: string,
    public changeGroupSysId: string,
    public enableVcenterc: boolean,
    public cswEnforced: boolean,
    public servers: ServerListExtended[]
  ) {}
}
