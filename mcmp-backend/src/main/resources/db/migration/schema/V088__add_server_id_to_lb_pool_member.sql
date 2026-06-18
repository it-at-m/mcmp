ALTER TABLE cmp.lb_pool_member
    ADD COLUMN server_id BIGINT REFERENCES cmp.server(id) ON DELETE SET NULL;
