SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ADD COLUMN "linux" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN "windows" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN "oracle" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN "non_oracle" BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cmp.server ADD COLUMN "patchnight_group" VARCHAR(1);
ALTER TABLE cmp.server ADD COLUMN "patchnight_time" VARCHAR(5);
ALTER TABLE cmp.server ADD COLUMN "server_infos_owner_mail" VARCHAR(100);
ALTER TABLE cmp.server ADD COLUMN "server_infos_ticket_no" VARCHAR(15);
ALTER TABLE cmp.server ADD COLUMN "tetration_agent_installed" BOOLEAN NOT NULL DEFAULT FALSE;





