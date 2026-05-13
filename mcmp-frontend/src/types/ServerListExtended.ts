export interface ServerListExtended {
  id: number;
  name: string;
  powerState: string;
  os?: string;
  appserviceNames?: string;
  numCpu?: number;
  memoryMb?: number;
  vdisksCapacityInBytes?: number;
  serverKind?: string;
  serverType?: string;
  managed?: boolean;
  canEdit?: boolean;
}
