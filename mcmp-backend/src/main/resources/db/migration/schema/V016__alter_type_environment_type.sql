ALTER TABLE cmp.network_group ALTER COLUMN "environment" DROP NOT NULL;
ALTER TABLE cmp.network ALTER COLUMN "environment" DROP NOT NULL;

UPDATE cmp.network_group SET "environment" = NULL;
UPDATE cmp.network SET "environment" = NULL;

CREATE TYPE cmp.environment_type_new AS ENUM ('C', 'D', 'K', 'P', 'S', 'TL');

ALTER TABLE cmp.appservice
    ALTER COLUMN "environment" TYPE cmp.environment_type_new
        USING (
        CASE "environment"::text
            WHEN 'c' THEN 'C'::cmp.environment_type_new
            WHEN 'd' THEN 'D'::cmp.environment_type_new
            WHEN 'k' THEN 'K'::cmp.environment_type_new
            WHEN 'p' THEN 'P'::cmp.environment_type_new
            WHEN 's' THEN 'S'::cmp.environment_type_new
            WHEN 'tl' THEN 'TL'::cmp.environment_type_new
            END
        );

ALTER TABLE cmp.network_group
    ALTER COLUMN "environment" TYPE cmp.environment_type_new
        USING (
        CASE "environment"::text
            WHEN 'c' THEN 'C'::cmp.environment_type_new
            WHEN 'd' THEN 'D'::cmp.environment_type_new
            WHEN 'k' THEN 'K'::cmp.environment_type_new
            WHEN 'p' THEN 'P'::cmp.environment_type_new
            WHEN 's' THEN 'S'::cmp.environment_type_new
            WHEN 'tl' THEN 'TL'::cmp.environment_type_new
            END
        );

ALTER TABLE cmp.network
    ALTER COLUMN "environment" TYPE cmp.environment_type_new
        USING (
        CASE "environment"::text
            WHEN 'c' THEN 'C'::cmp.environment_type_new
            WHEN 'd' THEN 'D'::cmp.environment_type_new
            WHEN 'k' THEN 'K'::cmp.environment_type_new
            WHEN 'p' THEN 'P'::cmp.environment_type_new
            WHEN 's' THEN 'S'::cmp.environment_type_new
            WHEN 'tl' THEN 'TL'::cmp.environment_type_new
            END
        );
DROP TYPE cmp.environment_type;

CREATE TYPE cmp.environment_type AS ENUM ('C', 'D', 'K', 'P', 'S', 'TL');

ALTER TABLE cmp.appservice
    ALTER COLUMN "environment" TYPE cmp.environment_type
        USING (
        CASE "environment"::text
            WHEN 'C' THEN 'C'::cmp.environment_type
            WHEN 'D' THEN 'D'::cmp.environment_type
            WHEN 'K' THEN 'K'::cmp.environment_type
            WHEN 'P' THEN 'P'::cmp.environment_type
            WHEN 'S' THEN 'S'::cmp.environment_type
            WHEN 'TL' THEN 'TL'::cmp.environment_type
            END
        );

ALTER TABLE cmp.network_group
    ALTER COLUMN "environment" TYPE cmp.environment_type
        USING (
        CASE "environment"::text
            WHEN 'C' THEN 'C'::cmp.environment_type
            WHEN 'D' THEN 'D'::cmp.environment_type
            WHEN 'K' THEN 'K'::cmp.environment_type
            WHEN 'P' THEN 'P'::cmp.environment_type
            WHEN 'S' THEN 'S'::cmp.environment_type
            WHEN 'TL' THEN 'TL'::cmp.environment_type
            END
        );

ALTER TABLE cmp.network
    ALTER COLUMN "environment" TYPE cmp.environment_type
        USING (
        CASE "environment"::text
            WHEN 'C' THEN 'C'::cmp.environment_type
            WHEN 'D' THEN 'D'::cmp.environment_type
            WHEN 'K' THEN 'K'::cmp.environment_type
            WHEN 'P' THEN 'P'::cmp.environment_type
            WHEN 'S' THEN 'S'::cmp.environment_type
            WHEN 'TL' THEN 'TL'::cmp.environment_type
            END
        );
DROP TYPE cmp.environment_type_new;

ALTER TABLE cmp.config_infoblox ADD CONSTRAINT unique_config_infoblox_api_endpoint UNIQUE (api_endpoint);

ALTER TABLE cmp.config_snow ADD CONSTRAINT unique_config_snow_api_endpoint UNIQUE (api_endpoint);

ALTER TABLE cmp.config_awx ADD CONSTRAINT unique_config_awx_api_endpoint UNIQUE (api_endpoint);