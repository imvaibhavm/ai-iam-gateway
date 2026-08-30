BEGIN;

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS department varchar(255);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS clearance varchar(255);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS region varchar(255);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS policy_assignments varchar(2000);

CREATE INDEX IF NOT EXISTS ix_audit_tenant_ts ON audit_log (tenant_id, ts);

CREATE TABLE IF NOT EXISTS mcp_provider_settings (
    id varchar(255) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    provider_id varchar(255) NOT NULL,
    admin_enabled boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS ix_mcp_provider_tenant ON mcp_provider_settings (tenant_id);

CREATE TABLE IF NOT EXISTS user_mcp_settings (
    id varchar(255) PRIMARY KEY,
    tenant_id varchar(255) NOT NULL,
    user_email varchar(255) NOT NULL,
    provider_id varchar(255) NOT NULL,
    enabled boolean NOT NULL DEFAULT false
);

CREATE INDEX IF NOT EXISTS ix_user_mcp_tenant_email ON user_mcp_settings (tenant_id, user_email);

COMMIT;
