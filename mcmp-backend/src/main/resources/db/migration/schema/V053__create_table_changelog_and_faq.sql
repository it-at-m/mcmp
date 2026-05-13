SET client_encoding = 'UTF8';

CREATE TABLE cmp.changelog (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "app_version" VARCHAR(100),
    "content_markdown" TEXT,
    "content_html" TEXT,
    "user_id" bigint,
    CONSTRAINT fk_changelog_user FOREIGN KEY ("user_id") REFERENCES cmp.user("id") ON DELETE SET NULL
);
ALTER TABLE cmp.changelog OWNER TO cmp;

CREATE TABLE cmp.faq_category (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "version" bigint NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
    "name" VARCHAR(100) NOT NULL UNIQUE,
    "description" TEXT,
    "sort_order" INTEGER NOT NULL DEFAULT 0
);
ALTER TABLE cmp.faq_category OWNER TO cmp;

CREATE TABLE cmp.faq (
     "id" BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     "version" bigint NOT NULL DEFAULT 0,
     "created_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
     "updated_at" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP(3),
     "category_id" BIGINT NOT NULL,
     "question" TEXT,
     "answer_markdown" TEXT,
     "answer_html" TEXT,
     "sort_order" INTEGER NOT NULL DEFAULT 0,
     "is_published" BOOLEAN NOT NULL DEFAULT FALSE,
     "user_id" BIGINT,
     CONSTRAINT fk_faq_category FOREIGN KEY ("category_id") REFERENCES cmp.faq_category("id") ON DELETE CASCADE,
     CONSTRAINT fk_faq_user FOREIGN KEY ("user_id") REFERENCES cmp.user("id") ON DELETE SET NULL
);
ALTER TABLE cmp.faq OWNER TO cmp;

CREATE INDEX idx_faq_category_sort ON cmp.faq_category (sort_order);
CREATE INDEX idx_faq_published_category_sort ON cmp.faq (is_published, category_id, sort_order);
