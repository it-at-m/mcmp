import { EnvironmentType } from "@/types/EnvironmentType";

export default class Network {
  constructor(
    public id: number | undefined,
    public broadcast: string,
    public cidr: string,
    public comment: string,
    public dnsPrimary: string,
    public dnsSecondary: string,
    public environment: EnvironmentType,
    public infobloxId: number | undefined,
    public ipAddress: string,
    public name: string,
    public netmask: string,
    public networkGroupId: number,
    public networktyp: string,
    public referat: string,
    public vlan: string,
    public gateway: string,
    public mcmpStatus: boolean,
    public mcmpNetworkTyp: string,
    public mcmpNetworkGroup: string,
    public infoblox: string
  ) {}
}
