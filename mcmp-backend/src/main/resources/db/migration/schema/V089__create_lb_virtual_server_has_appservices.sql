CREATE TABLE cmp.lb_virtual_server_has_appservices (
    lb_virtual_server_id BIGINT NOT NULL REFERENCES cmp.lb_virtual_server(id) ON DELETE CASCADE,
    appservice_id        BIGINT NOT NULL REFERENCES cmp.appservice(id) ON DELETE CASCADE,
    PRIMARY KEY (lb_virtual_server_id, appservice_id)
);
ALTER TABLE cmp.lb_virtual_server_has_appservices OWNER TO cmp;
