export enum SystemMode {
  NORMAL = "NORMAL",
  INFO = "INFO",
  FRONTEND_READ_ONLY = "FRONTEND_READ_ONLY",
  READ_ONLY = "READ_ONLY",
  LOCKED = "LOCKED",
}

export interface SystemStatus {
  systemMode: SystemMode;
  maintenanceMessage: string;
  maintenanceMessageMarkdown: string;
}
