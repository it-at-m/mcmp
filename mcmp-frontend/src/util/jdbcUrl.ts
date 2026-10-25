import type { DatabaseDetail, DatabaseEngine } from "@/types/Database";

const DEFAULT_PORTS: Record<DatabaseEngine, number> = {
  Oracle: 1521,
  MSSQL: 1433,
  PostgreSQL: 5432,
  MySQL: 3306,
  MariaDB: 3306,
};

export function buildJdbcUrl(db: DatabaseDetail): string | null {
  // Bei RAC/Clustern geht die Verbindung über den SCAN-Listener
  const host = db.scanName || db.fqdn;
  if (!host) return null;
  const port = db.port || DEFAULT_PORTS[db.engine];

  switch (db.engine) {
    case "Oracle": {
      const service = db.pdbName ?? db.serviceNames?.[0] ?? db.databaseName;
      return `jdbc:oracle:thin:@//${host}:${port}/${service}`;
    }
    case "MSSQL":
      return `jdbc:sqlserver://${host}:${port};databaseName=${db.databaseName};encrypt=true;trustServerCertificate=false`;
    case "PostgreSQL":
      return `jdbc:postgresql://${host}:${port}/${db.databaseName}?sslmode=require`;
    case "MySQL":
      return `jdbc:mysql://${host}:${port}/${db.databaseName}?useSSL=true&characterEncoding=UTF-8`;
    case "MariaDB":
      return `jdbc:mariadb://${host}:${port}/${db.databaseName}?useSsl=true`;
    default:
      return null;
  }
}
