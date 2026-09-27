-- Preserve all capability flags and explicit per-user restrictions.
-- The legacy default 200 was an execution ceiling. V2 processes complete authorized scopes in pages of 100.
-- Back up AGENT_CAPABILITIES_CONFIG before this DML; restore that row to roll back policy only.
UPDATE system_config
SET config_value=JSON_SET(config_value,
  '$.autoTaskPolicy.roleDefaults.teacher.audienceLimitMode','ALL_AUTHORIZED',
  '$.autoTaskPolicy.roleDefaults.teacher.scopes',JSON_ARRAY('OWN_CLASSES','OWN_DIVISION','OWN_COLLEGE'))
WHERE config_key='AGENT_CAPABILITIES_CONFIG'
  AND JSON_VALID(config_value)
  AND JSON_EXTRACT(config_value,'$.autoTaskPolicy.roleDefaults.teacher.audienceLimitMode') IS NULL
  AND JSON_EXTRACT(config_value,'$.autoTaskPolicy.roleDefaults.teacher.maxTargetsPerRun')=200;
