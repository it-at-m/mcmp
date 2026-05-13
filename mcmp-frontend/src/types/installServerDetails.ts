import type AppserviceList from "@/types/AppserviceList.ts";
import type { dbParams } from "@/types/installDBParams.ts";
import type {
  ServerCategoryType,
  ServerTypeMixed,
} from "@/types/ServerTypes.ts";

import type NetworkGroup from "./NetworkGroup";

import NewServername from "@/types/NewServername.ts";

export enum OsType {
  Linux = "Linux",
  Windows = "Windows",
}

export enum OsVersion {
  RHEL9 = "RHEL 9",
  RHEL10 = "RHEL 10",
  Windows2025 = "Windows Server 2025",
  Windows2022 = "Windows Server 2022",
}

export type Os = {
  Linux: string[];
  Windows: string[];
};

export const OperatingSystem: Os = {
  Linux: [OsVersion.RHEL9, OsVersion.RHEL10],
  Windows: [OsVersion.Windows2025, OsVersion.Windows2022],
};

export enum categoryType {
  Standard = "Standard",
  App = "App",
  DB = "DB",
  Mixed = "Mixed",
}

export type DiskEntry = {
  drive_number: number;
  label: string;
  size: number;
  min_size: number;
  max_size: number;
};

export default class installServerDetails {
  constructor(
    // general
    public appservice: AppserviceList | null,
    public isVCenterC: boolean,
    public osType: OsType | null,
    public osVersion: OsVersion | null,
    public categoryType: categoryType | null,
    public category: ServerCategoryType | ServerTypeMixed | null,

    // extra settings (db)
    public dbParams: dbParams | null,
    public nonPostgresReason: string | null,
    public middlewareUser: boolean,

    // Server name
    public serverName: NewServername | null,
    public expectedServerName: string,

    // hardware settings
    public memory: number,
    public cpu: number,
    public disk: { [key in OsType]?: { [key in categoryType]?: DiskEntry[] } },
    public networkGroup: NetworkGroup | null,

    // linux custom
    public isLinuxCustom: boolean = false,
    public linuxCustomExtraVars: string = "",

    // remove schedule
    public schedule: boolean = false,
    public removeScheduleTime: Date = new Date()
  ) {}

  isDatabase(): boolean {
    return this.categoryType === categoryType.DB;
  }
}
