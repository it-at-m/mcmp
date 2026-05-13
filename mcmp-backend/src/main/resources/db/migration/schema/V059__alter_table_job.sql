SET client_encoding = 'UTF8';

CREATE TYPE db_type AS ENUM ('postgresql', 'mariadb', 'oracle', 'mssql', 'mysql', 'mongodb', 'adabas');

ALTER TABLE job ADD COLUMN target_database_type db_type;
