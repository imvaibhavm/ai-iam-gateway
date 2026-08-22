-- One-time migration from the POC email primary key to tenant-scoped identities.
-- Run before deploying the tenant-aware backend against an existing database.
BEGIN;

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS tenant_id varchar(255);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS id varchar(255);

UPDATE app_users
SET tenant_id = COALESCE(tenant_id, 'default'),
    id = COALESCE(id, 'default|' || lower(email));

ALTER TABLE app_users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE app_users ALTER COLUMN id SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'app_users'::regclass
          AND conname = 'app_users_pkey'
          AND pg_get_constraintdef(oid) = 'PRIMARY KEY (email)'
    ) THEN
        ALTER TABLE app_users DROP CONSTRAINT app_users_pkey;
        ALTER TABLE app_users ADD CONSTRAINT app_users_pkey PRIMARY KEY (id);
    END IF;
END $$;

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS estimated_cost_usd double precision NOT NULL DEFAULT 0;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS input_tokens bigint NOT NULL DEFAULT 0;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS latency_ms bigint NOT NULL DEFAULT 0;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS output_redacted boolean NOT NULL DEFAULT false;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS output_tokens bigint NOT NULL DEFAULT 0;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS provider_succeeded boolean NOT NULL DEFAULT false;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_id varchar(255);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS tenant_id varchar(255);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS model varchar(255);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS routing_reason varchar(255);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS policy_version varchar(255);

UPDATE audit_log SET tenant_id = COALESCE(tenant_id, 'default');

COMMIT;
