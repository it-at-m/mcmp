import type PortGroup from "@/types/PortGroup";

export default interface Nic {
  serverId: number;
  portGroup: PortGroup | null;
  ips: {
    ip: string;
    ipType: string;
  }[];
  vnicKey: number;
  unitNumber: number | null;
  device: string;
  macAddress: string;
  network: string;
  connected: boolean;
  portGroupSummary: string;
  portGroupKey: string;
  distributedPortKey: string;
  addressType: string;
  cardType: string;
  toolsIpAddress: string;
  toolsNetworkName: string;
  toolsConnected: boolean;
}
