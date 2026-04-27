-- V18: Add mqtt_topic to environment_controllers
ALTER TABLE environment_controllers ADD COLUMN IF NOT EXISTS mqtt_topic VARCHAR(500);

UPDATE environment_controllers SET mqtt_topic = 'sensimul/sites/849dc38d-cb7a-42a5-9d4e-adaf1e5bc4cc/controllers/' || external_controller_id WHERE external_controller_id = 'ctrl-849dc38d';
UPDATE environment_controllers SET mqtt_topic = 'sensimul/sites/1eaf59ad-958a-493e-b6b7-fc86c7d5eda1/controllers/' || external_controller_id WHERE external_controller_id = 'ctrl-1eaf59ad';

COMMENT ON COLUMN environment_controllers.mqtt_topic IS '제어기 MQTT 토픽 (sensimul/sites/{siteId}/controllers/{controllerId})';
