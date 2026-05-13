SET client_encoding = 'UTF8';

CREATE TABLE cmp.price (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" BIGINT DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "name" VARCHAR(100) NOT NULL UNIQUE,
    "price_per_unit" DECIMAL(10, 2) NOT NULL,
    "currency" VARCHAR(10) NOT NULL,
    "description" TEXT
);
ALTER TABLE cmp.price OWNER TO cmp;
