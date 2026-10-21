export type DatabaseEngine =
  "Oracle" | "MSSQL" | "MariaDB" | "MySQL" | "PostgreSQL";

export type DatabaseDeploymentType = "SINGLE" | "RAC" | "CLUSTER";

export interface DatabaseNode {
  hostname: string;
  fqdn: string;
  role: string;
  ipAddress: string;
  state: string;
  cpuCores?: number;
  memoryGb?: number;
}

export interface DatabaseExtension {
  name: string;
  version: string;
}

export interface DatabaseLoginPrivilege {
  user: string;
  host: string;
  privilege: string;
}

export interface DatabaseDetailField {
  label: string;
  value: string;
}

export interface DatabaseDetail {
  id: number;
  name: string;
  engine: DatabaseEngine;
  deploymentType: DatabaseDeploymentType;
  /** z. B. "SINGLE" / "RAC" wie im CMDB-Feld database_type */
  databaseType?: string;
  databaseName: string;
  pdbName?: string;
  instanceName?: string;
  clusterName?: string;
  /** SCAN-/Listener-/VIP-Name bei Clustern */
  scanName?: string;
  fqdn: string;
  port: number;
  version: string;
  edition?: string;
  characterSet: string;
  collation?: string;
  startupTime: string;
  sizeDbGb?: number;
  sizeSgaGb?: number;
  sizePgaGb?: number;
  sizeLogGb?: number;
  sizeTempDbGb?: number;
  maxConnections?: number;
  activeConnections?: number;
  activeLogins?: number;
  options?: string[];
  serviceNames?: string[];
  extensions?: DatabaseExtension[];
  loginPrivileges?: DatabaseLoginPrivilege[];
  nodes: DatabaseNode[];
  archiveLogMode?: string;
  patchLevel?: string;
  lastBackup?: string;
  dataDirectory?: string;
  replicationMode?: string;
  replicationLagSeconds?: number;
  environment?: string;
  cmdbCiName?: string;
  owner?: string;
  supportGroup?: string;
  location?: string;
  backupClass?: string;
  slaClass?: string;
}

export interface DatabaseListItem {
  id: number;
  name: string;
  engine: DatabaseEngine;
  deploymentType: DatabaseDeploymentType;
  fqdn: string;
}
