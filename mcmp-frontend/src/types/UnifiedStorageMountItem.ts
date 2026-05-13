import type { AppserviceNameAndSysId } from "@/types/Server.ts";

export interface UnifiedStorageMountItem {
  mountPoint: string;
  filesystem: string;
  options: string[];
  serverId: number;

  uuid: string;
  name: string;
  type: string;
  size: number;
  used: number;
  protocol: string;
  appservices: AppserviceNameAndSysId[];

  nfs_mount_path?: string;
  cifs_share_name?: string;
  cifs_mount_path?: string;
}
