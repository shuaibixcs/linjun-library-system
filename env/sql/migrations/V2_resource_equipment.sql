-- Only for a database initialized before equipment_status was introduced.
-- Fresh installations use schema.sql and do not need this migration.
ALTER TABLE resource ADD COLUMN equipment_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
UPDATE resource SET equipment_status='DAMAGED' WHERE kind='SEAT' AND status='MAINTENANCE';
