SET client_encoding = 'UTF8';

ALTER TABLE server ADD COLUMN greenit_power_off_change_rejected_date TIMESTAMPTZ;
COMMENT ON COLUMN server.greenit_power_off_change_rejected_date IS 'Last date when a green-it power off change was rejected for this server (stored as TIMESTAMPTZ in Europe/Berlin timezone)';

ALTER TABLE server ADD COLUMN greenit_rightsizing_change_rejected_date TIMESTAMPTZ;
COMMENT ON COLUMN server.greenit_rightsizing_change_rejected_date IS 'Last date when a green-it rightsizing change was rejected for this server (stored as TIMESTAMPTZ in Europe/Berlin timezone)';

ALTER TABLE green_it_rightsizing ADD COLUMN status TEXT;

ALTER TABLE green_it_power_off ADD COLUMN status TEXT;