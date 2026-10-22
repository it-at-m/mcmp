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

export interface DatabaseTablespace {
  name: string;
  sizeGb?: number;
  usedGb?: number;
  freeGb?: number;
  usedPercent?: number;
  autoextend?: boolean;
  maxSizeGb?: number;
  status?: string;
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
  /** Volle Zeilenbreite statt 1/4-Spalte – z. B. für lange Connection-Strings */
  fullWidth?: boolean;
  /** Wert in Monospace-/Code-Optik darstellen */
  monospace?: boolean;
  /** Kopier-Button neben dem Wert anzeigen */
  copyable?: boolean;
  /** Kleine Erläuterung unter dem Wert */
  hint?: string;
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
  tablespaces?: DatabaseTablespace[];
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
  sessions?: DatabaseSessionSummary;
  waitEvents?: DatabaseWaitEvent[];
  topStatements?: DatabaseTopStatement[];
  cache?: DatabaseCacheStats;
  replication?: DatabaseReplicationStatus;
  archive?: DatabaseArchiveStatus;
  dataFiles?: DatabaseDataFile[];
  objectHealth?: DatabaseObjectHealth;
  tableStats?: DatabaseTableStat[];
  accounts?: DatabaseAccountInfo[];
  parameters?: DatabaseParameterInfo[];
  backupHistory?: DatabaseBackupInfo[];
  security?: DatabaseSecurityInfo;
  schedulerJobs?: DatabaseSchedulerJob[];
  errors?: DatabaseErrorSummary;
}

export interface DatabaseListItem {
  id: number;
  name: string;
  engine: DatabaseEngine;
  deploymentType: DatabaseDeploymentType;
  fqdn: string;
}

export interface DatabaseDetailField {
  label: string;
  value: string;
}

export interface DatabaseSessionSummary {
  total: number;
  active: number;
  idle: number;
  idleInTransaction?: number;
  maxConnections?: number;
  maxUsedConnections?: number;
  utilizationPercent?: number;
  blockedSessions: number;
}

export interface DatabaseWaitEvent {
  waitClass?: string;
  event: string;
  count?: number;
  timeMs?: number;
  averageMs?: number;
}

export interface DatabaseTopStatement {
  sqlId?: string;
  text: string;
  executions?: number;
  averageElapsedMs?: number;
  totalElapsedMs?: number;
  rowsProcessed?: number;
  bufferGets?: number;
  planCacheHitPercent?: number;
}

export interface DatabaseCacheStats {
  bufferCacheHitPercent?: number;
  sharedPoolHitPercent?: number;
  planCacheHitPercent?: number;
  dictionaryCacheHitPercent?: number;
  tempFiles?: number;
  tempBytes?: number;
}

export interface DatabaseReplicationNode {
  name: string;
  role: string;
  state: string;
  syncMode?: string;
  lagSeconds?: number;
  lagBytes?: number;
  lastCommitAt?: string;
}

export interface DatabaseReplicationStatus {
  mode?: string;
  role?: string;
  healthy?: boolean;
  maxLagSeconds?: number;
  nodes: DatabaseReplicationNode[];
}

export interface DatabaseArchiveStatus {
  logMode?: string;
  enabled: boolean;
  destination?: string;
  retention?: string;
  archivedCount?: number;
  lastArchivedAt?: string;
  failedCount?: number;
  lastFailedAt?: string;
  gapDetected?: boolean;
  areaUsedPercent?: number;
}

export interface DatabaseDataFile {
  name: string;
  path?: string;
  type?: string;
  sizeMb?: number;
  usedMb?: number;
  maxSizeMb?: number;
  autoextend?: boolean;
  status?: string;
  volumeFreeGb?: number;
}

export interface DatabaseObjectHealth {
  tables?: number;
  indexes?: number;
  views?: number;
  invalidObjects?: number;
  unusableIndexes?: number;
  unusedIndexes?: number;
  tablesWithoutPrimaryKey?: number;
}

export interface DatabaseTableStat {
  schema: string;
  table: string;
  rows?: number;
  sizeMb?: number;
  freeMb?: number;
  deadRows?: number;
  deadRowPercent?: number;
  lastAnalyzed?: string;
  lastVacuum?: string;
  sequentialScans?: number;
  indexScans?: number;
}

export interface DatabaseAccountInfo {
  user: string;
  host?: string;
  accountStatus?: string;
  isLocked?: boolean;
  passwordExpired?: boolean;
  passwordExpiresAt?: string;
  canLogin?: boolean;
  roles?: string[];
  lastLogin?: string;
  connectionLimit?: number;
}

export interface DatabaseParameterInfo {
  name: string;
  value: string;
  isDefault?: boolean;
  modifiable?: boolean;
  pendingRestart?: boolean;
}

export interface DatabaseBackupInfo {
  type: string;
  startedAt: string;
  finishedAt?: string;
  sizeGb?: number;
  status: string;
  destination?: string;
  message?: string;
}

export interface DatabaseSecurityInfo {
  tdeEnabled?: boolean;
  tlsEnabled?: boolean;
  tlsVersion?: string;
  tlsCipher?: string;
  authMode?: string;
  auditEnabled?: boolean;
  passwordPolicy?: string;
}

export interface DatabaseSchedulerJob {
  name: string;
  schema?: string;
  enabled: boolean;
  state?: string;
  lastStartAt?: string;
  nextRunAt?: string;
  failureCount?: number;
}

export interface DatabaseErrorSummary {
  lastErrorAt?: string;
  lastErrorMessage?: string;
  errorsLast24h?: number;
}
