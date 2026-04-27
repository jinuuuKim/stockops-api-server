-- V17: Fix environment monitoring schema issues
-- 1. Add updated_at to sensor_readings (required by BaseEntity)
-- 2. Make value_kind nullable (Sensimul can send null valueKind)
-- 3. Fix controller_type to use uppercase enum values
-- 4. Add updated_at to controller_commands (required by BaseEntity)

-- 1. Add updated_at column to sensor_readings
ALTER TABLE sensor_readings ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 2. Make value_kind nullable for Sensimul compatibility
ALTER TABLE sensor_readings ALTER COLUMN value_kind DROP NOT NULL;

-- 3. Fix controller_type enum values (change from lowercase to uppercase)
ALTER TABLE environment_controllers DROP CONSTRAINT IF EXISTS environment_controllers_controller_type_check;
ALTER TABLE environment_controllers ADD CONSTRAINT environment_controllers_controller_type_check
  CHECK (controller_type IN ('COOLING', 'HEATING', 'HUMIDIFYING', 'DEHUMIDIFYING', 'VENTILATION', 'AIR_PURIFIER'));

UPDATE environment_controllers SET controller_type = 'AIR_PURIFIER' WHERE controller_type = 'air_purifier';
UPDATE environment_controllers SET controller_type = 'COOLING' WHERE controller_type = 'cooling';
UPDATE environment_controllers SET controller_type = 'HEATING' WHERE controller_type = 'heating';
UPDATE environment_controllers SET controller_type = 'HUMIDIFYING' WHERE controller_type = 'humidifying';
UPDATE environment_controllers SET controller_type = 'DEHUMIDIFYING' WHERE controller_type = 'dehumidifying';
UPDATE environment_controllers SET controller_type = 'VENTILATION' WHERE controller_type = 'ventilation';

-- 4. Add updated_at column to controller_commands
ALTER TABLE controller_commands ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
