export interface mariaPostgesMysqlOracleParams {
  db_type: string;
  db_version: string;
  customer_db_name: string;
  customer_db_user: string;
  customer_db_schema: string;

  customer_db_charset: string;
  postgis: string[];
  conn_dima_admin: boolean;
  conn_cap: boolean;
  conn_app_server: boolean;
  customer_app_server: string[];
  oracle_datasize: number;
}

export interface mssqlParams {
  mssql_serversort: string;
}

export interface dbParams {
  mariaPostgresMysqlOracle: mariaPostgesMysqlOracleParams;
  mssql: mssqlParams;
}
