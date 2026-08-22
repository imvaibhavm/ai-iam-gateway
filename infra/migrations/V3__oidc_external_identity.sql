BEGIN;

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS external_issuer varchar(255);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS external_subject varchar(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_users_external_identity
    ON app_users (external_issuer, external_subject)
    WHERE external_issuer IS NOT NULL AND external_subject IS NOT NULL;

COMMIT;
