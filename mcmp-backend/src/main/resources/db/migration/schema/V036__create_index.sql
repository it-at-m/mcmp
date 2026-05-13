SET client_encoding = 'UTF8';

CREATE INDEX idx_ip_assignment_ip_id ON cmp.ip_assignment(ip_id);
CREATE INDEX idx_ip_assignment_nic_id ON cmp.ip_assignment(nic_id);
