export enum SystemMode {
  NORMAL = "NORMAL",
  READ_ONLY = "READ_ONLY",
  LOCKED = "LOCKED",
}

export interface SystemStatus {
  systemMode: SystemMode;
  maintenanceMessage: string;
}