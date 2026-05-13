SET client_encoding = 'UTF8';

ALTER TABLE cmp.server ALTER COLUMN "patchnight_exitstring" TYPE TEXT;

CREATE INDEX IF NOT EXISTS idx_server_patchnight_exitcode_nonzero
    ON cmp.server (patchnight_exitcode)
    WHERE patchnight_exitcode <> 0;
