import type { AppserviceNameAndSysId } from "@/types/Server.ts";

export interface UnifiedStorageItem {
  uuid: string;
  name: string;
  type: string;
  size: number;
  used: number;
  protocol: string;
  appservices: AppserviceNameAndSysId[];

  // NFS Specifics
  nfs_mount_path?: string;
  nfs_export_policy?: {
    exportPolicyId: number;
    name: string;
    ontapExportPolicyRules: {
      policyId: number;
      clients: string[];
      rwRules: string;
    }[];
  };
  nfs_security_style?: string;

  // CIFS Specifics
  cifs_share_name?: string;
  cifs_mount_path?: string;
  cifs_share_acl_list?: {
    shareAclId: number;
    userOrGroup: string;
    permission: string;
  }[];

  // S3 Specifics
  s3_endpoint?: string;
  s3_bucket_name?: string;
  s3_object_count?: number;

  // Common/Volume Specifics
  mirrorEnabled?: boolean;
  diskClass?: string;

  isFlexClone?: boolean;
  parentVolumeName?: string;
  parentVolumeUUID?: string;
  parentVolumeType?: string;
  parentSnapshotName?: string;

  isWorm?: boolean;
  minRetention?: string;
  maxRetention?: string;
  defaultRetention?: string;
  autocommitPeriod?: string;
  appendMode?: boolean;

  spaceAvailablePercent?: number;
  spaceAfsTotal?: number;
  spaceLogicalUsed?: number;
  spaceLogicalAvailable?: number;
  spaceLogicalUsedPercent?: number;
  spaceLogicalUsedByAfs?: number;
  spaceSnapshotReservePercent?: number;
  spaceSnapshotReserveSize?: number;
  spaceSnapshotUsed?: number;
}
