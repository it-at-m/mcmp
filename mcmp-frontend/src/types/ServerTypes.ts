import { OsVersion } from "@/types/installServerDetails.ts";

export interface ServerCategoryType {
  label: string;
  osVersion: OsVersion[];
  kenner: string;
  allowedPrefixes: string[];
  isApp?: boolean;
  isDb?: boolean;
  allowedDBVersions?: Partial<Record<OsVersion, string[]>>;
}

export const categorys: {
  linux: Record<string, ServerCategoryType>;
  windows: Record<string, ServerCategoryType>;
} = {
  linux: {
    apache: {
      label: "Apache",
      osVersion: [OsVersion.RHEL10, OsVersion.RHEL9],
      kenner: "",
      allowedPrefixes: [],
      isApp: true,
    },
    php: {
      label: "Apache/PHP",
      osVersion: [OsVersion.RHEL10, OsVersion.RHEL9],
      kenner: "",
      allowedPrefixes: [],
      isApp: true,
    },
    tomcat: {
      label: "Apache/Tomcat",
      osVersion: [OsVersion.RHEL10, OsVersion.RHEL9],
      kenner: "",
      allowedPrefixes: [],
      isApp: true,
    },
    java: {
      label: "Java",
      osVersion: [OsVersion.RHEL10, OsVersion.RHEL9],
      kenner: "",
      allowedPrefixes: [],
      isApp: true,
    },
    maria: {
      label: "MariaDB",
      osVersion: [OsVersion.RHEL10],
      allowedDBVersions: { [OsVersion.RHEL10]: ["11.4"] },
      kenner: "da",
      allowedPrefixes: ["cn-"],
      isDb: true,
    },
    mysql: {
      label: "MySQL",
      osVersion: [OsVersion.RHEL10],
      allowedDBVersions: { [OsVersion.RHEL10]: ["8.4"] },
      kenner: "dy",
      allowedPrefixes: [],
      isDb: true,
    },
    oracle: {
      label: "OracleDB",
      osVersion: [OsVersion.RHEL9],
      allowedDBVersions: { [OsVersion.RHEL9]: ["19c"] },
      kenner: "db",
      allowedPrefixes: [],
      isDb: true,
    },
    postgres: {
      label: "PostgreSQL",
      osVersion: [OsVersion.RHEL10],
      allowedDBVersions: {
        [OsVersion.RHEL10]: ["17", "18"],
      },
      kenner: "dp",
      allowedPrefixes: ["cn-"],
      isDb: true,
    },
  },
  windows: {
    mssql: {
      label: "MSSQL",
      osVersion: [OsVersion.Windows2025, OsVersion.Windows2022],
      kenner: "ds",
      allowedPrefixes: ["cn-"],
      isDb: true,
    },
  },
};

export interface ServerTypeMixed {
  osVersion: OsVersion[];
  label: string;
  kenner: string;
  appCategorys: ServerCategoryType[];
  dbCategory?: ServerCategoryType;
  allowedPrefixes: string[];
  allowedDBVersions?: Partial<Record<OsVersion, string[]>>;
}

export const serverTypeMixes: ServerTypeMixed[] = [
  /*
  {
    osVersion: [OsVersion.RHEL10, OsVersion.RHEL9],
    label: "Apache/PHP + MariaDB",
    kenner: "",
    appCategorys: [categorys.linux.apache, categorys.linux.php],
    dbCategory: categorys.linux.maria,
    allowedPrefixes: [],
    allowedDBVersions: {[OsVersion.RHEL10]: ["11.4"]},
  },
  {
    osVersion: [OsVersion.RHEL10],
    label: "Apache/PHP + MySQL",
    kenner: "",
    appCategorys: [categorys.linux.apache, categorys.linux.php],
    dbCategory: categorys.linux.mysql,
    allowedPrefixes: [],
  },
  {
    osVersion: [OsVersion.RHEL10, OsVersion.RHEL9],
    label: "Apache/PHP + PostgreSQL",
    kenner: "",
    appCategorys: [categorys.linux.apache, categorys.linux.php],
    dbCategory: categorys.linux.postgres,
    allowedPrefixes: [],
    allowedDBVersions: {
        [OsVersion.RHEL10]: ["16", "17", "18"],
        [OsVersion.RHEL9]: ["16", "17"],
      },
  },
  */
];
