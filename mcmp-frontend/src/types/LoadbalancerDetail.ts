export interface LbMonitor {
  type: string;
  interval: number | null;
  port: string | null;
  method: string | null;
  path: string | null;
  host: string | null;
  version: string | null;
  expect: string | null;
}

export interface LbPoolRef {
  isDefault: boolean | null;
  hosts: string[] | null;
  paths: string[] | null;
}

export interface LoadbalancerMember {
  ip: string;
  port: number;
  serverId: number | null;
  serverName: string | null;
  monitorCondition: string | null;
  monitors: LbMonitor[] | null;
}

export interface LoadbalancerPool {
  name: string;
  lbMethod: string;
  monitorCondition: string | null;
  monitors: LbMonitor[] | null;
  poolRef: LbPoolRef | null;
  members: LoadbalancerMember[];
}

export interface LbIrule {
  name: string;
  content: string | null;
}

export interface LbAppserviceRef {
  id: number;
  name: string;
}

export interface LbVirtualServerCi {
  snowName: string;
  snowSysId: string;
  snowSysClass: string;
}

export interface LoadbalancerDetail {
  id: number;
  name: string;
  listen: string;
  forward: string;
  port: number;
  persistence: string;
  wafEnabled: boolean;
  wafStatus: string | null;
  redirect80: boolean;
  addresses: string[];
  domains: string[];
  appservices: LbAppserviceRef[];
  tenantRepositoryUrl: string | null;
  pools: LoadbalancerPool[];
  irules: LbIrule[];
  snowCis: LbVirtualServerCi[];
  canEdit: boolean;
}
