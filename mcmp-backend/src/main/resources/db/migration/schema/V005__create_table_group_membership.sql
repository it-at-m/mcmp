SET client_encoding = 'UTF8';

create TABLE cmp.group_membersship (
    "user_id" BIGINT NOT NULL,
    "group_id" BIGINT NOT NULL,
    CONSTRAINT pk_group_membersship PRIMARY KEY ("user_id", "group_id"),
    CONSTRAINT fk_user_id FOREIGN KEY ("user_id") REFERENCES cmp.user("id") ON delete CASCADE,
    CONSTRAINT fk_group_id FOREIGN KEY ("group_id") REFERENCES cmp.group("id") ON delete CASCADE
);
alter table cmp.group_membersship OWNER TO cmp;
